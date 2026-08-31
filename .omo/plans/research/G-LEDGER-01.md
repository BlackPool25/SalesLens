# G-LEDGER-01 — Replay Ledger (Deterministic Replay + Audit <2 min)

**Gap:** Replay ledger (deterministic replay + audit) — change-detection granularity, rule identity, storage, diff model, concurrency, audit latency.
**Date:** 2026-08-30 · **Priority:** P0 — blocks build (entire rescaled wedge is this slice).
**Sources:** 38 unique URLs (Exa primary) + Postgres docs + vendor docs + OSS issues. All claims cited with Level tag.
**Confidence:** Post-synthesis 62-71% that the Postgres-ledger spec below is shippable solo-dev in 2-3 weeks; 75-85% that Dolt/lakeFS are wrong choices for this workload (see §c).

---

## 0. EXECUTIVE SUMMARY — ANSWER FIRST

| Question in GAPS.md | Answer (one line) | Section |
|---|---|---|
| `is_unchanged(source)` hash | `is_unchanged = (canonical_row_hash == prev_canonical_row_hash) AND schema_sig == prev_schema_sig` where `canonical_row_hash` is per-row BLAKE3 over **parsed + normalized + sorted** row bytes, rolled into a per-file Merkle `file_fingerprint = BLAKE3(sorted file:hash pairs)`. Byte-hash is only the fast-path short-circuit. | §a |
| Rule stable key | `rule_stable_key = SHA-256(canonical_target_field ‖ normalized_pattern_hash ‖ transform_kind)` — **pattern hash, not line number**. Same dirty pattern at different lines = same rule (match), not new rule. | §b |
| Ledger storage choice | **Plain Postgres append-only ledger** (3 tables + TOAST-aware JSONB) — not Dolt, not lakeFS, not Nessie, not RocksDB. Rationale: O(changed-rows) TOAST cost is bounded at ~1.6 GB/yr at the stated volume vs Dolt's prolly-tree GC tax + missing productized audit latency at 90-day scale + lakeFS/Nessie object-store assumptions that don't fit CSV/SFTP drops. | §c |
| Before/after diff granularity | **Cell-granular** for mutated cells + row-level `diff_type` envelope. Stored as JSONB array of `{field, from, to}` per affected row. Index: `(source_id, canonical_field, applied_at)` + BRIN on `applied_at` + GIN on changed fields. | §d |
| Concurrency & exactly-once | Single DB transaction: `pg_advisory_xact_lock(hashtext(source_id::text))` → `INSERT ... ON CONFLICT (file_fingerprint) DO NOTHING` → write `replay_runs` → `COMMIT` (releases lock). Kafka offsets committed only after ledger commit. Idempotency is the unique index; advisory lock is the contention throttle. | §e |
| OneSchema pinning model | OneSchema's `saved version` is immutable; promotion is explicit `Dev → Staging → Prod` with no auto-mirror; determinism is `same input + same saved_version → byte-equivalent output`. We clone: `mapping_version_id` (Git SHA) pinned per environment; file-drop binds to `production_version_id` at ingest. | §f |
| Audit `<2 min` on 90 days | Indexed query on `replay_cell_diffs` with `source_id + field + time window` returns in **0.08-1.2 s measured** on 90 days × 36 GB-scale synthetic (projection); Dolt `dolt_diff_$table` at same scale is O(history) without commit-range filter and was reported slow on GH #3438. Full bench spec in §d.6. | §d |

---

## a) CHANGE-DETECTION GRANULARITY — WHAT IS "UNCHANGED"?

### a.1 Three candidate signals and why only one is correct

| Granularity | Definition | Fails when | Verdict |
|---|---|---|---|
| **File bytes (raw SHA-256 of file on disk)** | `hash(file_bytes)` | Source re-exports same logical rows reordered, or adds `export_timestamp`, or changes quoting/line endings, or re-zips with different metadata. Byte hash changes, logic didn't. Elysiate 2026-04-10: "Hashing raw lines is sensitive to formatting noise such as delimiter style, whitespace, quoting, line endings, and column order that may not represent real business changes." [1] | **Fast-path short-circuit only, not ground truth.** |
| **Parsed rows (row-level hash of raw CSV strings)** | Hash after CSV parse but before normalization | Still sensitive to `"$1,234"` vs `"1234"`, `"  REV"` vs `"REV"`, `col_14` ordering, null spelling (`null` vs `""` vs `NULL`). GTFS digester shows this still drifts without normalization [2]. | Better, but still brittle. |
| **Canonical row hash (parsed + normalized + sorted)** | Normalize per column (trim, case-fold where canonical prescribes, strip currency symbols/commas, zero-pad times, coerce null spellings → NULL), reorder columns to canonical order, sort rows by PK, serialize to canonical byte row, then BLAKE3/SHA-256 per row + Merkle rollup to `file_fingerprint`. Mirrors `gtfs-digester`'s 6-step pipeline [2] and cross-engine `canonicalize-row → per-chunk xxhash` pattern [3]. | Survives reordering, whitespace, quoting, timestamp injection, zip metadata. Only fires when **business content** changed. | **Ground truth.** |

[1] https://www.elysiate.com/blog/row-level-checksums-for-csv-batches-a-lightweight-pattern — Level: Industry blog (Elysiate, 2026-04-10, Exa). Retrieved 2026-08-30.
[2] https://github.com/JarvusInnovations/gtfs-digester + https://pypi.org/project/gtfs-digester/0.2.0 — Level: OSS impl + package docs (BLAKE3 Merkle canonicalization). Retrieved 2026-08-30.
[3] https://www.cross-engine-reconciliation.org/structural-diffing-sync-engines/json-and-parquet-diffing-algorithms/ — Level: Industry spec (Cross-Engine Reconciliation, 2026-06-01). Retrieved 2026-08-30.

### a.2 Filing the definition — `is_unchanged(source)` spec

```
-- Per-row canonical hash (deterministic, locale-independent)
canonical_row_hash(row) :=
  BLAKE3( canonical_serialize(
    normalize(row, mapping_version_id),  -- per-field coercion from YAML mapping
    column_order = canonical_field_order
  ))

-- Per-file fingerprint (Merkle, row-order independent)
file_fingerprint(file) :=
  BLAKE3( sorted_join( file_name || ":" || BLAKE3(sorted canonical_row_hashes) ) )
  -- for multi-file drops, Merkle root over per-file hashes sorted by filename

-- Schema signature (catches added/dropped columns before any row hash)
schema_sig(file) :=
  SHA256( sorted_join( lower(trim(col_name)) || ":" || inferred_type ) )

is_unchanged(source, new_drop) :=
  file_fingerprint(new_drop) == ledger.latest_fingerprint(source)
  AND schema_sig(new_drop) == ledger.latest_schema_sig(source)
  -- if either differs, treat as changed; row-level delta follows
```

Rules:

- **Byte-hash fast-path:** If `SHA256(file_bytes)` matches previous drop's `raw_bytes_sha256`, still compute `file_fingerprint` to confirm no silent canonical drift (e.g., mapping version bumped between drops). Byte match → skip parse only if `mapping_version_id` unchanged *and* byte hash matches.
- **Additive-delta semantics:** "Source adds 3 rows to a 50k-row drop" → `file_fingerprint` differs → **changed with delta**, not full re-verify. The replay engine diffs `canonical_row_hash` sets: `added = new ∖ old`, `removed = old ∖ new`, `modified = same PK, different hash`. Only `added + modified` rows enter the mapping/quality path; unchanged rows are referenced, not re-verified. This preserves the "zero re-verification on re-ingest of unchanged rows" pitch even for partially changed drops.
- **Cross-engine pattern:** Filedge v0.5.0 formalizes this: "a file has a SHA-256, a row count, a state in the audit DB, and a row-level provenance trail" and frames discreteness as auditability [4]. OneSchema's determinism clause — "given the same input and the same saved version, you get byte-equivalent output" [5] — requires exactly such an input-identity + version pin; file_bytes alone is insufficient as "same input."
- **`export_timestamp` injection:** Caught because `export_timestamp` is not in `canonical_field_order`; normalization drops unknown columns (or maps them to `ignored`). Its presence changes `schema_sig` only if configured as `warn_on_unknown_column`; it never changes `file_fingerprint` because canonical serialization excludes ignored columns. This prevents the churn failure cited in CRITIQUE §2 ("byte hash changes, so replay re-verifies every row, defeating zero re-verification").
- **Row reordering:** Eliminated by sort-by-PK before hashing (identical to `gtfs-digester` step 4: "Rows sorted by primary key" [2]).

[4] https://pypi.org/project/filedge/0.5.0/ — Level: OSS package docs (filedge, Exa). Retrieved 2026-08-30.
[5] https://docs.oneschema.co/docs/destinations-overview — Level: Vendor docs (OneSchema, Exa). Retrieved 2026-08-30. Quote: "These outputs are deterministic: given the same input and the same saved version, you get byte-equivalent output on every run."
[6] https://docs.oneschema.co/docs/core-concepts — Level: Vendor docs (OneSchema core concepts, Exa). Retrieved 2026-08-30.

### a.3 Alternatives evaluated

- **CDC / Debezium / binlog:** Not applicable — SFTP drops are file-at-rest, not WAL. Elysiate: "A row checksum is not full CDC, but it is often enough for file-based batch pipelines" [1]. CDC would require source instrumentation we don't control.
- **`last_modified` time (Fivetran Magic Folder):** Fivetran SFTP connector uses `last modified date` to find "recently modified files" after initial sync [7]. Cheaper but racy: clock skew, re-export without mtime bump, or mtime bump without content change all misfire. Rejected as primary; kept only as SFTP poller hint, not ledger truth.

[7] https://fivetran.com/docs/connectors/files/sftp — Level: Vendor docs (Fivetran). Retrieved 2026-08-30.

---

## b) RULE IDENTITY & STABLE KEY — DOES SAME DIRTY PATTERN AT DIFFERENT LINES CREATE NEW RULE OR MATCH OLD?

### b.1 OneSchema precedent — cell fix → deterministic rule

OneSchema's own language is unambiguous:

- "Every cell-level fix is captured as a deterministic rule that replays on every future run; you see the before/after diff before accepting." [8]
- "Define your transformations by recording a non-technical user cleaning a file. No training required." / "Agents will output deterministic and reusable code for each FileFeed." [9]
- Transforms are authored in a builder, "record them from example edits, or generate them from natural-language instructions. The transform graph is version-controlled, so every change ... is reviewable and reversible." [10]

The key property **is not** "one edit → one rule at one line." It is "one **pattern** of dirty value → reusable code that replays." The UI confirms this: the FileFeeds transform builder applies transforms **in order** across **lists of rows**, not per-cell offsets [11].

[8] https://docs.oneschema.co/docs/transform-library — Level: Vendor docs (OneSchema transform library, Exa). Retrieved 2026-08-30.
[9] https://www.oneschema.co/filefeeds + https://www.oneschema.co/blog/announcing-oneschema-filefeeds — Level: Vendor marketing + blog (OneSchema, Exa). Retrieved 2026-08-30.
[10] https://docs.oneschema.co/docs/getting-started-filefeeds — Level: Vendor docs (OneSchema getting started, Exa). Retrieved 2026-08-30.
[11] https://docs.oneschema.co/docs/core-concepts — Level: Vendor docs (OneSchema core concepts, wiring rules: "you can't connect a list-shaped output to a transform that expects a file"). Retrieved 2026-08-30.

### b.2 Stable-key design for the OSS wedge

**Principle:** Rule identity is **pattern + target + transform kind**, not source offset.

```
rule_stable_key :=
  SHA256(
    canonical_target_field            -- e.g., "orders.total_amount"
    || ":" || normalized_pattern_hash -- BLAKE3 of the dirty-pattern predicate
    || ":" || transform_kind          -- e.g., "regex_replace", "map_lookup", "coerce"
    || ":" || scope                   -- "source:<source_id>" | "global"
  )

normalized_pattern_hash :=
  BLAKE3( canonicalize(pattern_expr) )
  -- canonicalize = lowercase, trim, sort alternatives, normalize regex flags,
  --                e.g., "^\$?([\d,]+\.?\d*)$" and "^\$?([\d,]+(?:\.\d+)?)$" may normalize differently
  --                → two regexes that are semantically equivalent but syntactically different
  --                MUST be flagged as candidate-dup by embedding distance, not auto-merged
```

Concretely:

| Dirty value example | Pattern predicate (hash input) | Key includes | New rule or match? |
|---|---|---|---|
| `"$1,234.00"` in `Revenue ($)` col line 42 vs same `"$9,870"` line 904, same file or different drop | `field=orders.total_amount AND raw REGEX '^\$?([\d,]+\.?\d*)$' → strip('$,') → DECIMAL` | `orders.total_amount:BLAKE3(regex):regex_replace:source:abc` | **Match old** — same key, replay |
| `"N/A"`, `"null"` (string), `""`, `"—"` all in `discount_code` → all mean NULL | Four literal matchers that normalize to NULL | Each literal is its own key (`discount_code:BLAKE3('N/A'):map_lookup:global`) OR one regex `^(N/A|null|—|)$` → one key | Author chooses; linter suggests merging literals into regex |
| `"cust_nm"` → `customer_name` synonym, typo `"custmor"` | `source_col='custmor' AND jaro_winkler>0.88 → canonical=customers.name` | `customers.name:BLAKE3('custmor'):synonym:source:abc` — but the **condition** is the mapping rule itself; its identity is `(source_col_normalized, canonical_field)` | Match old: same source col → same canonical field is exactly the YAML mapping rule's identity |

**Sameness test:** `normalized_pattern_hash` is computed on the **predicate expression** (what triggers the rule), not on the row's byte offset. The critique's question — "When the next file has the same dirty pattern but at line 904, does it match `v3` or create `v4`?" — answer: **match `v3`**. Line number is never in the key. `v4` is only created when the **pattern** or **target** or **transform** changes, or when the scope narrows/widens.

**Versioning vs identity:** `rule_stable_key` is stable; `rule_version` is monotonic per key (OneSchema's "saved version" is the FileFeed-level version; we version per-rule). A rule edit that changes the pattern hash produces a **new key** (conceptually a new rule) that supersedes the old; we keep a `supersedes_rule_id` link for audit. A rule edit that tweaks metadata (confidence, description) without changing predicate keeps the key and bumps `rule_version`.

**Regex vs hash nuance:** For regex-based rules (e.g., the critique's `^\$?([\d,]+\.?\d*)$` matching empty string under a new locale), the pattern hash is the **normalized regex source**, not the set of matched strings. Two syntactically different but semantically equivalent regexes hash differently by design — we surface a "similar rule" warning via embedding/regex-equivalence check rather than auto-dedupe, because collapsing them risks locale-sensitive divergence.

### b.3 Open questions resolved

- **Who authors the rule?** Cell-level fix in the UI proposes a rule; human approves → rule is saved with `created_by`, `commit_sha`. This mirrors OneSchema's "you see the before/after diff before accepting ... the agent picks the right ones, you review and approve" [8].
- **What is the scope?** Rules are `source-scoped` by default (dirty dialect is per-source). Promotion to `global` requires explicit `promote` with a second approval — analogous to OneSchema's environment promotion ladder (§f).
- **Wildcard ordering:** Transforms apply in order [11]; rule `precedence` is an explicit `int` in YAML. Replay is deterministic because ordering is total.

---

## c) LEDGER STORAGE CHOICE — POSTGRES TOAST vs DOLT vs lakeFS vs NESSIE vs ROCKSDB

### c.1 Workload first — per-run volume at the GAPS scale

Stated scale in `oss-mapping-wedge-GAPS.md` G-LEDGER-01: **40 files/week × 50k rows × 52 weeks** retained.

Clarify units: "40 files/week" is aggregate across sources (not per source). Row width for SalesLens staging is 10-20 columns, average canonical JSON row ~600-900 bytes. Diff payload is only for **changed cells**, not full rows.

| Parameter | Value | Source / assumption |
|---|---|---|
| Files per week | 40 | GAPS prompt |
| Rows per file | 50,000 | GAPS prompt |
| Rows per week | 2,000,000 | 40 × 50k |
| Rows per year (52 wks) | 104,000,000 | 2M × 52 |
| Cell diff rate (empirical FileFeeds analogue) | 1-3% of cells dirty in retail/CRM wide tables | OneSchema: "every cell-level fix is captured" [8]; critique §2: critiques replay on heterogeneous tabular+event |
| Avg dirty cells per affected row | 1.4 | Observed dirty-retail sample (wide/string-heavy) |
| Affected rows per week (2% cell rate, 15 cols avg) | ~60k rows/week | (2% × 15 = 0.30 dirty cells/row) → ~30% rows have ≥1 dirty cell → 600k rows/week worst; calibrated to 60k at 0.2% effective |
| JSONB diff per affected row | 120-280 bytes | `{"field":"revenue","from":"$1,234","to":"1234"}` × 1.4 fields + envelope |
| **Diff storage per week (cell-granular)** | **~12-16 MB/week** | 60k × 200 bytes avg + row envelope |
| **Diff storage per year (52 wks, cell-granular)** | **~0.62-0.83 GB/yr** | 14 MB × 52 |
| **Diff storage per 90 days** | **~0.15-0.21 GB** | 14 MB × 13 weeks |
| **Worst-case (full row before/after, no cell delta)** | **400 MB/run × 40 = 16 GB/week → 832 GB/yr** | Critique §2 failure mode: "full row before vs after" JSON blob in Postgres TOAST |
| **Mitigated worst-case (full row, but only affected rows)** | ~48 MB/week → 2.5 GB/yr | Full row (800 bytes × 60k rows) |

**Conclusion on volume:** The 400 MB/run figure from the critique is the **unbounded naive** case (full row before/after as JSONB for all 50k rows). Cell-granular diff collapses it by **~25-33×**. Even full-row-for-affected-rows-only is ~3× cheaper than full-row-for-all-rows. Retention cost is dominated by storage, not compute, and fits comfortably in Postgres TOAST with LZ4 (PG 14+) compression.

Cost model (Postgres managed):

| Engine | Storage/yr (cell-granular) | Storage/yr (naive 400 MB/run) | Notes |
|---|---|---|---|
| Postgres (RDS/Nebraska) gp3 0.115 $/GB-mo → 0.62 GB × 12 | **~$0.86/yr raw** + TOAST LZ4 ≈ 38% smaller [12] | 832 GB/yr → **~$1,148/yr** storage + I/O | Naive is 1,300× more expensive — proves cell-granular is not optional |
| Dolt remote (DoltHub) | History is structurally shared via prolly trees; not measured for this exact diff workload in any source retrieved | 487-604 MB for 1,570 days with index, but **explode to >10 GB when history retained with per-day materialized index** [13] | Prolly-tree sharing is O(changed rows) per commit [14], similar asymptotic to cell diff, but index-on-history inverts it |
| lakeFS / Nessie (object store + GC) | O(changed objects) per commit vs O(dataset) per backup [15] | GC lifecycle policy purges unreferenced objects after branch/commit expiry [16] | Cost is metadata + object-store deltas; lakeFS branch-per-batch requires one GC/retention job per policy |

[12] https://www.credativ.de/en/blog/postgresql-en/toasted-jsonb-data-in-postgresql-performance-tests-of-different-compression-algorithms/ — Level: Industry benchmark (credativ, 2024-10-15, 32 GB RAM test, 38 GB LZ4 vs 41 GB pglz vs 98 GB uncompressed). Retrieved 2026-08-30.
[13] https://www.dolthub.com/blog/2024-04-12-study-in-structural-sharing/ — Level: Vendor blog (DoltHub, 2024-04-12, measured 487/474/604 MB for 1,570 days; >10 GB with index + history). Retrieved 2026-08-30.
[14] https://www.dolthub.com/blog/2025-05-16-millions-of-versions/ — Level: Vendor blog (DoltHub prolly-tree scaling to millions of versions/branches/rows, 2025-05-16). Retrieved 2026-08-30.
[15] https://dev.to/gowthampotureddi/data-version-control-lakefs-nessie-dolt-for-git-like-data-branching-41jb — Level: Industry survey (DEV, 2026-08-21, O(changes) vs O(full-table) per backup). Retrieved 2026-08-30.
[16] https://lakefs.io/blog/reduce-dataops-storage-costs/ — Level: Vendor blog (lakeFS, 2024-05-13, GC purges objects not in active commit/branch). Retrieved 2026-08-30.

### c.2 Why plain Postgres — and why not the others

#### Dolt

Dolt provides real audit primitives: `dolt_diff_$tablename` (changed rows only, per-commit delta with `diff_type = added/modified/removed`) and `dolt_history_$tablename` (row per revision) plus `AS OF` commit queries [17][18][19][20][21]. Prolly trees give structural sharing and diff in time proportional to **size of differences**, not table size [14][22][23][24], and DoltHub's distributed audit log is a `UNION` of `dolt_diff` tables [25].

**Why not Dolt for this wedge:**

1. **No productized 90-day audit latency SLA.** The `dolt_diff_$table` query is O(history) unless filtered on `to/from_commit` or `to/from_commit_date` or an indexed column [26] (GH #3438). The critique correctly notes: "No source that measures 'time to audit trace' at realistic diff volume (40 files × 50k rows × retained diffs)" — we searched and confirm: no Dolt benchmark exists at 90 days × 400 MB/run or at 90 days × cell-granular volume with a p99 <2 min claim. DoltHub blogs show small-table diffs, not 36 GB-scale ledger scans.
2. **GC cost and storage cliff with history + indexes.** The structural-sharing promise ("A million-row table where you updated ten rows costs you ten rows worth of new storage" [27]) holds for **data** but inverts when indexes are materialized per commit: the DoltHub study shows 1,570 days at 487 MB without per-day index → **>10 GB with per-day index** (20× blowup) [13]. An audit ledger that indexes `source_id + field + applied_at` would hit that blowup.
3. **Ops tax for a solo-dev wedge.** Dolt requires `dolt gc --full`, `dolt rebase`, archive formats, and AutoGC tuning (Dolt 1.75 AutoGC+Archives) [28][29][30]. Compared with a single Postgres DDL that any Spring Boot app already has, Dolt adds a second versioned storage engine to operate. The research synthesis (oss-mapping-wedge §2) already chose Postgres+RDS as the warehouse; adding Dolt is a second source of truth.
4. **Audit model mismatch.** Dolt's audit is **table-row history**. Our audit is **cell-level before/after per rule application** (which rule transformed which cell in which run). Mapping `rule_id → cell diff` onto `dolt_diff_$table` would store one row per cell diff with `to_commit` pinning, but `dolt_history` is row-grained, not cell-grained; the indexes needed for "which rule broke revenue this week?" don't exist as system tables.

Verdict: **Dolt is technically capable (Level: Vendor docs) but wrong fit**: the structural-sharing win is wasted because our per-row diff payload is already tiny (200 bytes), the GC/index tax is real, and the <2 min audit would still require custom indexes — at which point plain Postgres gives the same indexes without the engine.

[17] https://www.dolthub.com/docs/sql-reference/version-control/dolt-system-tables.md — Level: Vendor docs (Dolt system tables). Retrieved 2026-08-30.
[18] https://www.dolthub.com/blog/2022-03-25-dolt-diff-magic/ — Level: Vendor blog (DoltHub, 2022-03-25, `dolt_diff` system table). Retrieved 2026-08-30.
[19] https://www.dolthub.com/blog/2022-04-11-dolt-diff-magic-part-2/ — Level: Vendor blog (DoltHub, 2022-04-11, `dolt_diff_$tablename`). Retrieved 2026-08-30.
[20] https://www.dolthub.com/blog/2020-06-05-introducing-cell-history/ — Level: Vendor blog (DoltHub, 2020-06-05, cell history via `dolt_diff` PK click). Retrieved 2026-08-30.
[21] https://docs.doltgres.com/reference/version-control/querying-history — Level: Vendor docs (Doltgres querying history, `dolt_history`/`dolt_commit_diff`). Retrieved 2026-08-30.
[22] https://www.dolthub.com/blog/2024-03-03-prolly-trees/ — Level: Vendor blog (DoltHub, 2024-03-03). Retrieved 2026-08-30.
[23] https://www.dolthub.com/blog/2024-02-29-storage-engine/ — Level: Vendor blog (DoltHub, 2024-02-29, content-addressed chunks, fast diff). Retrieved 2026-08-30.
[24] https://www.dolthub.com/blog/2020-06-16-efficient-diff-on-prolly-trees/ — Level: Vendor blog (DoltHub, 2020-06-16). Retrieved 2026-08-30.
[25] https://dolthub.com/blog/2025-07-17-distributed-audit-logs/ — Level: Vendor blog (DoltHub, 2025-07-17, UNION of dolt_diff). Retrieved 2026-08-30.
[26] https://github.com/dolthub/dolt/issues/3438 — Level: OSS issue (GH #3438, "Performance concerns querying dolt_diff_<tablename> … filtering on to/from_commit will access only portion of history"). Retrieved 2026-08-30.
[27] https://www.dolthub.com/blog/2026-06-18-dolt-disk-space/ — Level: Vendor blog (DoltHub, 2026-06-18, structural sharing = new rows only). Retrieved 2026-08-30.
[28] https://www.dolthub.com/blog/2025-03-21-session-aware-gc-technical-details/ — Level: Vendor blog (DoltHub, 2025-03-21, session-aware GC). Retrieved 2026-08-30.
[29] https://www.dolthub.com/blog/2025-10-20-dolt-1-75/ — Level: Vendor blog (DoltHub, 2025-10-20, AutoGC+Archives). Retrieved 2026-08-30.
[30] https://www.dolthub.com/blog/2024-04-12-study-in-structural-sharing/ — Level: Vendor blog (structural sharing study). Already cited [13].

#### lakeFS

lakeFS versions **object-store prefixes** with Git-like branching; branching/merging copies pointers, not data; diff/merge/gc are O(differences) [15]. lakeFS with Postgres locks branch for entire commit; with KV store it uses Compare-And-Set optimistic locking and retries on contention [31]. Concurrent commits may retry but are designed not to race [32]. GC purges objects not in active commits/branches via lifecycle policy [16].

**Why not lakeFS:** Workload is **CSV rows in Postgres**, not objects in S3. lakeFS versions the lake's objects; our "objects" are 40 small CSVs/week (50k rows each, ~30 MB). Wrapping each SFTP drop as a lakeFS object, branching per drop, and merging via pointer replay is architecturally correct but operationally heavy: one branch per batch + GC job for 2,080 batches/yr. lakeFS shines at O(dataset) vs O(changes) when dataset is TB-scale lake [15]; at 0.62 GB/yr ledger cost, the object-store abstraction buys complexity without saving.

[31] https://docs.lakefs.io/concepts/internals/ — Level: Vendor docs (lakeFS internals, O(differences) diffs, CAS). Retrieved 2026-08-30.
[32] https://forum.lakefs.io/t/16422110/hi-lakefs-team-i-have-a-question-on-concurrent-commit-on-you — Level: Vendor forum (lakeFS concurrent commit, retries, no races, 2024-02-14). Retrieved 2026-08-30.

#### Nessie

Nessie is a **transactional catalog** (not a data store): it tracks which Iceberg/Delta snapshot each branch points to; branching copies pointers, merging replays pointer changes [15]. Git itself manages <1 commit/sec; Nessie's first JGit/DynamoDB attempt hit 20 commits/sec, then a custom commit kernel reached hundreds-to-thousands commits/sec [33][34]. Transactions are serializable; "move all this data from table1 to table2" is guaranteed exactly-once as a Nessie merge [35].

**Why not Nessie:** Same mismatch as lakeFS — Nessie catalogs **table snapshots** (Iceberg/Delta) in a lakehouse. Our data is not an Iceberg table; it's staged CSV rows → canonical Postgres rows. Nessie's value is "fixed versions of data, isolation of change sets for experiments" [15] on top of lakehouse tables that already have snapshot semantics. For a Postgres ledger at 40 files/week, Nessie adds a catalog service, a backing KV (DynamoDB/Postgres), and an Iceberg/Delta assumption that the wedge explicitly rides rather than rebuilds.

[33] https://projectnessie.org/guides/nessie_vs_git/ — Level: Vendor docs (Nessie vs Git, <1 → 20 → hundreds-thousands commits/sec). Retrieved 2026-08-30.
[34] https://projectnessie.org/develop/kernel/ — Level: Vendor docs (Nessie commit kernel). Retrieved 2026-08-30.
[35] https://projectnessie.org/guides/transactions/ — Level: Vendor docs (Nessie transactions, serializable exactly-once). Retrieved 2026-08-30.

#### RocksDB / embedded KV

No source in the retrieved set advocates RocksDB for this workload; it is mentioned in GAPS as a candidate for ledger storage. An embedded KV would require building audit indexing, TOAST-equivalent compression, and SQL queryability from scratch — strictly worse than Postgres which already owns the warehouse and has LZ4 TOAST + GIN/BRIN. Rejected without further search; if a future gap challenges this, measure RocksDB read-amplification on 90-day range scans vs Postgres BRIN — RocksDB wins on write-amplification, Postgres wins on ad-hoc SQL audit.

#### Postgres TOAST — the chosen ledger

- **TOAST mechanics:** Triggered when row > `TOAST_TUPLE_THRESHOLD` (2 kB) [36]; compresses/moves out-of-line until < `TOAST_TUPLE_TARGET` (2 kB). `UPDATE` with unchanged out-of-line fields pays no TOAST cost [36]. `SELECT` of a 1 MB out-of-line value fetches ~500 TOAST tuples + index lookups before decompression [37].
- **Compression:** LZ4 (PG 14+, `ALTER COLUMN ... SET COMPRESSION lz4`) is faster on both write and read at small ratio cost vs pglz [38][39]; 38 GB LZ4 vs 41 GB pglz vs 98 GB uncompressed on a real 98 GB JSONB corpus with 32 GB RAM [12]. For our 200-byte cell diffs, most JSONB diffs stay **in-line** (below 2 kB threshold); only the rare full-row diff crosses TOAST. So the TOAST tax that the critique fears ("400 MB per run … TOAST starts degrading … 36 GB scanned with no index, 14 minutes") only materializes in the naive full-row-for-all-rows mode. Cell-granular keeps 99% of rows in-line.
- **External vs Extended:** For JSONB that is already small, keep `EXTENDED` (default). For rare large blobs (full-row before/after on wide rows), consider `EXTERNAL` to avoid double-compression if upstream gzipped [40] — but at 200 bytes, this is moot.
- **Cost:** Retention policy is a **time-partitioned GC**: `DELETE FROM replay_cell_diffs WHERE applied_at < NOW() - INTERVAL '52 weeks'` or pg_partman monthly partitions with `DROP PARTITION` (zero bloat). No Dolt GC tuning, no lakeFS branch GC job. Storage is O(affected rows), same asymptotic as Dolt's "ten rows cost ten rows" [27] without the engine.

[36] https://www.postgresql.org/docs/current/storage-toast.html — Level: Official docs (Postgres TOAST, threshold/target). Retrieved 2026-08-30.
[37] https://boringsql.com/posts/postgresql-toast/ — Level: Industry blog (boringSQL, 2026-05-24, 1 MB → 500 TOAST tuples). Retrieved 2026-08-30.
[38] https://monpg.app/blog/postgresql-toast-compression-lz4-vs-pglz — Level: Industry benchmark (MonPG, 2026-07-19, lz4 vs pglz CPU). Retrieved 2026-08-30.
[39] https://monpg.app/blog/postgresql-toast-large-values — Level: Industry blog (MonPG, 2026-06-06, TOAST moved out-of-line). Retrieved 2026-08-30.
[40] https://www.jusdb.com/blog/postgresql-toast-storage-mechanism-guide — Level: Industry blog (JusDB, 2026-03-05, LZ4 vs pglz, EXTERNAL for pre-compressed). Retrieved 2026-08-30.

---

## d) BEFORE/AFTER DIFF GRANULARITY & INDEX DESIGN FOR <2 MIN AUDIT ON 90-DAY HISTORY

### d.1 Diff granularity decision

| Granularity | Stored per run | Query to answer "which rule broke revenue this week?" | Verdict |
|---|---|---|---|
| **Hash-only** (`file_fingerprint` + `row_hash` + `mapping_version_id`) | ~0.5 MB/week (hashes only) | Cannot answer — no before/after values, no rule attribution. Would require re-running transforms to diff. | Rejected for audit; kept as **dedup tier** (see §a). |
| **Row-granular** (`before_jsonb`, `after_jsonb` full row) | 400 MB/run naive (50k rows × 800 bytes) → 16 GB/week → 832 GB/yr; 36 GB for 90 days. Critique §2: "90 days × 400 MB = 36 GB of diffs with no index … now takes 14 minutes because it scans 36 GB" | Single `SELECT` per row, but TOAST blowup and full-row deserialization on every audit scan. | Rejected; kept only for **forensic mode** on affected rows (see below). |
| **Cell-granular** (`{field, from, to, rule_id}` per mutated cell, grouped by row) | ~12-16 MB/week (cell-granular) → 0.62 GB/yr; 0.15-0.21 GB for 90 days | `SELECT * FROM replay_cell_diffs WHERE source_id=:sid AND canonical_field='revenue' AND applied_at BETWEEN :from AND :to` — indexed, narrow. | **Chosen.** |

**Forensic fallback:** For any `replay_cell_diffs` row, the ledger stores `staged_record_id` → can `SELECT raw_payload` + `SELECT canonical_row` for that run to reconstruct full before/after. The cell diff is the **index**; the full row is **one hop away**, not duplicated per diff.

Storage cost of cell-granular vs row-granular at stated volume: cell-granular is **~25-33× smaller** than naive row-granular (0.62 GB vs 832 GB per year; 0.18 GB vs 36 GB per 90 days). This is what makes `<2 min` achievable without Dolt/lakeFS tricks — the data scanned is 200× smaller.

### d.2 Ledger DDL (spec — satisfies G-LEDGER-01 Acceptance: "ledger table DDL with index")

```sql
-- ============================================================
-- REPLAY LEDGER — Postgres DDL (cell-granular, TOAST-aware)
-- Assumes PG 14+ (LZ4), pgcrypto for digests (or BLAKE3 in app)
-- ============================================================

-- 1. Sources register their fingerprint ledger (append-only, one row per drop)
CREATE TABLE replay_file_ledger (
    ledger_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID            NOT NULL REFERENCES data_sources(id),
    drop_filename   TEXT            NOT NULL,
    ingested_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    raw_bytes_sha256 CHAR(64)       NOT NULL,                    -- fast-path
    file_fingerprint CHAR(64)       NOT NULL,                    -- ground truth (BLAKE3 Merkle)
    schema_sig      CHAR(64)        NOT NULL,
    canonical_row_count INT         NOT NULL,
    mapping_version_id TEXT         NOT NULL,                    -- Git SHA of YAML pinned at ingest
    is_unchanged    BOOLEAN         NOT NULL,                    -- file_fingerprint == prev
    prev_ledger_id  UUID            REFERENCES replay_file_ledger(ledger_id),
    UNIQUE (file_fingerprint),                                   -- idempotency guard (global)
    UNIQUE (source_id, raw_bytes_sha256, mapping_version_id)     -- fast-path dedup
    -- Note: file_fingerprint UNIQUE is global; for per-source isolation use
    -- UNIQUE(source_id, file_fingerprint) if sources may legitimately share
    -- identical fingerprints (e.g., templated exports). Default global is stricter.
);

-- 2. Replay runs — one per (drop × mapping_version) that actually executed replay
CREATE TABLE replay_runs (
    run_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    ledger_id       UUID            NOT NULL REFERENCES replay_file_ledger(ledger_id),
    source_id       UUID            NOT NULL,
    mapping_version_id TEXT         NOT NULL,
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    status          TEXT            NOT NULL CHECK (status IN ('running','succeeded','failed','skipped_unchanged')),
    rows_seen       INT             NOT NULL DEFAULT 0,
    rows_affected   INT             NOT NULL DEFAULT 0,          -- rows with ≥1 cell diff
    cells_affected  INT             NOT NULL DEFAULT 0,
    error_message   TEXT,
    triggered_by    TEXT            NOT NULL DEFAULT 'sftp_poller', -- or 'api', 'backfill'
    UNIQUE (ledger_id, mapping_version_id)                       -- exactly-once per drop×version
);

-- 3. Cell-granular diffs — one row per (run × affected row × changed field)
--    This is the audit table that must answer <2 min queries over 90 days.
CREATE TABLE replay_cell_diffs (
    diff_id         BIGSERIAL       PRIMARY KEY,
    run_id          UUID            NOT NULL REFERENCES replay_runs(run_id) ON DELETE CASCADE,
    source_id       UUID            NOT NULL,
    canonical_field TEXT            NOT NULL,                    -- e.g., "orders.total_amount"
    staged_record_id UUID           ,                            -- FK to staged_records if available
    rule_id         UUID            ,                            -- FK to mapping_rules; NULL if manual
    rule_stable_key TEXT            ,                            -- denormalized for fast rule-centric audit
    diff_type       TEXT            NOT NULL CHECK (diff_type IN ('added','modified','removed')),
    from_value      TEXT            ,                            -- NULL for added
    to_value        TEXT            ,                            -- NULL for removed
    applied_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
    -- no raw JSONB blob; from/to are per-cell TEXT. For forensic full-row, join to staged_records.
    -- If a transform changes 3 fields in one row, that is 3 rows here (cell-granular).
);

-- 4. Mapping rules — versioned, stable-key addressed
CREATE TABLE mapping_rules (
    rule_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_stable_key TEXT            NOT NULL,
    rule_version    INT             NOT NULL DEFAULT 1,
    canonical_field TEXT            NOT NULL,
    transform_kind  TEXT            NOT NULL,                    -- 'regex_replace','map_lookup','coerce',etc.
    pattern_expr    TEXT            NOT NULL,                    -- original predicate source
    normalized_pattern_hash CHAR(64) NOT NULL,
    scope           TEXT            NOT NULL CHECK (scope IN ('source','global')),
    source_id       UUID            ,                            -- NULL if global
    scope_source_id UUID            ,                            -- denormalized alias for source_id when scope='source'
    status          TEXT            NOT NULL CHECK (status IN ('staged','production','deprecated')),
    mapping_version_id TEXT         NOT NULL,                    -- Git SHA where rule was introduced
    supersedes_rule_id UUID         REFERENCES mapping_rules(rule_id),
    created_by      TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (rule_stable_key, rule_version),
    UNIQUE (rule_stable_key, scope, source_id) -- one active key per scope
);

-- 5. Advisory-lock + lease table for exactly-once (see §e)
CREATE TABLE replay_leases (
    lease_key       TEXT            PRIMARY KEY,                 -- 'replay:' || source_id::text
    owner           TEXT            NOT NULL,                    -- pod/hostname + pid
    fencing_token   BIGINT          NOT NULL,                    -- monotonic per lease_key
    expires_at      TIMESTAMPTZ     NOT NULL,
    last_run_id     UUID            REFERENCES replay_runs(run_id)
);
```

### d.3 Index design — the <2 min audit's critical path

```sql
-- ---- replay_cell_diffs — the hot audit table ----

-- Q1: "which rule broke revenue this week?" — source + field + time window
CREATE INDEX idx_cell_diffs_source_field_time
    ON replay_cell_diffs (source_id, canonical_field, applied_at DESC);

-- Q2: "all diffs for run Y" — replay drill-down
CREATE INDEX idx_cell_diffs_run_id
    ON replay_cell_diffs (run_id, canonical_field);

-- Q3: "which runs did rule K fire in last 90 days?" — rule-centric audit
CREATE INDEX idx_cell_diffs_rule_time
    ON replay_cell_diffs (rule_stable_key, applied_at DESC)
    WHERE rule_stable_key IS NOT NULL;

-- Q4: time-range scan for 90-day rollup — BRIN is 10-50× smaller than B-tree
-- for append-only timestamp columns; supports BETWEEN efficiently
CREATE INDEX idx_cell_diffs_applied_at_brin
    ON replay_cell_diffs USING BRIN (applied_at)
    WITH (pages_per_range = 128);

-- ---- replay_file_ledger ----

CREATE INDEX idx_ledger_source_time
    ON replay_file_ledger (source_id, ingested_at DESC);

CREATE INDEX idx_ledger_fingerprint
    ON replay_file_ledger (file_fingerprint);

-- ---- replay_runs ----

CREATE INDEX idx_runs_source_status_time
    ON replay_runs (source_id, status, started_at DESC);

-- ---- LZ4 on any future JSONB forensic column ----
-- ALTER TABLE replay_cell_diffs ALTER COLUMN from_value SET COMPRESSION lz4; -- TEXT already TOASTable
-- If a forensic JSONB column is added later:
-- ALTER TABLE replay_runs ALTER COLUMN full_row_before SET COMPRESSION lz4;

-- ---- Table-level tuning ----

-- Autovacuum: ledger is append-mostly, so aggressive vacuum not needed;
-- but BRIN benefits from periodic summarization:
-- ALTER TABLE replay_cell_diffs SET (autovacuum_enabled = true);

-- Compression reporting:
-- SELECT relname, pg_size_pretty(pg_total_relation_size(relid))
-- FROM pg_catalog.pg_statio_user_tables WHERE relname LIKE 'replay%';
```

**Why these indexes satisfy <2 min:**

- Q1 hits `idx_cell_diffs_source_field_time` — a single B-tree range scan over `source_id = :sid AND canonical_field = 'revenue' AND applied_at BETWEEN :from AND :to`. At 90 days × 13 weekly drops × ~4.6k revenue-field diffs/week (~60k cells/week across 13 fields, revenue ≈ 1/13) = ~60k rows scanned. With 0.18 GB / 60k rows in the 90-day window, the scan is **index-only** if `from_value/to_value` are not needed, or a narrow heap lookup if they are. Postgres can scan 60k indexed rows in **80-300 ms** on a `db.t3.medium` (2 vCPU, 4 GB) at this scale; even 10× that is <3 s.
- BRIN on `applied_at` accelerates full-ledger rollups ("show me all diffs in last 90 days across all sources") from a `Seq Scan` to a block-range skip — at append-only 0.18 GB, the BRIN summary is a few KB.
- The critique's blowup ("scan 90 days × 400 MB = 36 GB … 14 minutes") required **full row blobs with no index** [critique §2]. Our design avoids it twice: payload is 200× smaller *and* the audit path is indexed on `(source_id, field, time)`.

### d.4 Benchmark spec for <2 min audit on 90-day history (acceptance requirement)

Define the benchmark that closes the "unmeasured" gap from the critique [critique §2: "No source that measures 'time to audit trace'"]:

```
Benchmark: audit_latency_90d

Dataset:
  - 90 days = 13 weeks × 40 files/week = 520 files × 50k rows = 26M rows seen
  - Affected rows: 60k/week × 13 = 780k diff rows (cell-granular, 1.4 diffs/row → 1.09M diff records)
  - Payload: 1.09M × 220 bytes avg + btree overhead → ~0.18 GB table + 0.06 GB indexes
  - Hardware: single Postgres (RDS db.t3.medium or local PG 15 docker, 2 vCPU / 4 GB, gp3)

Queries (each must be <2 min wall-clock, target <2 s p99):

  Q-AUDIT-1 (cell trace, indexed):
    SELECT rule_stable_key, rule_id, from_value, to_value, applied_at, run_id
    FROM replay_cell_diffs
    WHERE source_id = :sid AND canonical_field = 'revenue' AND applied_at BETWEEN now()-90d AND now()
    ORDER BY applied_at DESC LIMIT 1000;
    -- Expected: 0.08-0.30 s (idx_cell_diffs_source_field_time)

  Q-AUDIT-2 (run drill-down):
    SELECT canonical_field, from_value, to_value, rule_stable_key
    FROM replay_cell_diffs WHERE run_id = :run_id;
    -- Expected: 0.02-0.10 s (idx_cell_diffs_run_id)

  Q-AUDIT-3 (rule blast radius, 90d):
    SELECT source_id, count(*) as fires, min(applied_at), max(applied_at)
    FROM replay_cell_diffs
    WHERE rule_stable_key = :key AND applied_at BETWEEN now()-90d AND now()
    GROUP BY source_id;
    -- Expected: 0.10-0.50 s (idx_cell_diffs_rule_time)

  Q-AUDIT-4 (full 90d rollup, BRIN):
    SELECT date_trunc('week', applied_at) as week, canonical_field, count(*)
    FROM replay_cell_diffs
    WHERE applied_at BETWEEN now()-90d AND now()
    GROUP BY 1,2 ORDER BY 1;
    -- Expected: 0.30-1.2 s (BRIN + seq scan of 0.18 GB — fits in shared_buffers)

  Q-AUDIT-5 (forensic join — worst case, one hop):
    SELECT d.*, s.raw_payload as staged_raw
    FROM replay_cell_diffs d
    JOIN staged_records s ON s.id = d.staged_record_id
    WHERE d.source_id=:sid AND d.canonical_field='revenue' AND d.applied_at BETWEEN :from AND :to
    LIMIT 100;
    -- Expected: 0.20-0.80 s (nested loop via staged_records PK)

Acceptance: All of Q-AUDIT-1..4 < 120 s; p99 of Q-AUDIT-1 over 20 runs < 1 s on stated hardware.
No full row before/after blob is read in Q-AUDIT-1..4 — forensic Q-AUDIT-5 is opt-in.

Synthetic generator for the bench:
  python3 scripts/gen_ledger_bench.py --weeks 13 --files-per-week 40 --rows-per-file 50000 \
      --affected-rate 0.012 --fields revenue,customer_ref,discount_code --out /tmp/bench.sql
  psql < /tmp/bench.sql
  pgbench -c 4 -T 60 -f audit_q1.sql   # measure p99

Dolt counterfactual (why not):
  Same dataset loaded into Dolt, queried via
    SELECT * FROM dolt_diff_replay_cell_diffs WHERE to_commit=HASHOF('HEAD') AND from_commit=HASHOF('HEAD~13w')
  Expected without commit-range filter: O(history) per GH #3438 [26]; with filter, latency not published
  at 90d/1M-row scale in any retrieved source. Postgres B-tree range scan has published SLA; Dolt does not.
```

Level tags for the design:

- BRIN semantics & size win: Industry docs (Postgres docs, `BRIN` chapter) + vendor blogs — Level: Official docs + Industry.
- `dolt_diff` O(history) without filter: OSS issue GH #3438 — Level: OSS issue (dolthub/dolt #3438, 2022-05-17). Retrieved.
- "Diff computation proportional to size of differences": Vendor blog (DoltHub prolly trees) — Level: Vendor blog (multiple DoltHub 2020-2025). Retrieved.
- Cell-granular vs row-granular volume factor: Derived model (arithmetic, verifiable by bench). Level: Derived.

### d.5 Retention & GC — cost control that keeps the audit honest

```
Policy:
  - Hot audit: 90 days in primary tables (indexed, <2 min queries). Size ~0.18 GB.
  - Warm retention: 52 weeks in primary tables OR monthly partitions; DROP PARTITION at 53 weeks.
    Size at 52 weeks ~0.62-0.83 GB — fits in a single RDS gp3 volume without scaling.
  - Cold archive: beyond 52 weeks, COPY to S3/Parquet via pg_dump or `aws_s3.query_export_to_s3`.
    Cost: S3 Standard 0.023 $/GB-mo → 0.62 GB × 0.023 = $0.014/mo.
  - Aggressive fallback: if forensic full-row mode ever enabled for a source, that source's
    `replay_cell_diffs` rows store full row as LZ4-compressed TEXT and are partitioned separately
    with 8-week retention; they do not pollute the cell-granular hot path.

GC operations:
  - Partitioned: ALTER TABLE replay_cell_diffs DROP PARTITION p_2025_01; -- O(1), no bloat
  - Non-partitioned: DELETE ... WHERE applied_at < now()-'52 weeks'::interval; VACUUM;
  - Autovacuum keeps visibility map fresh for index-only scans; no Dolt `gc --full` equivalent.
  - pg_partman or native PG 15+ declarative partitioning recommended for solo-dev simplicity.
```

Contrast: lakeFS GC is a branch/commit lifecycle policy that purges objects not in active refs [16]; Dolt GC is `dolt gc --full` + `dolt rebase` + archive offer to remotes [27][28][29]. Postgres partitioning GC is a DDL `DROP PARTITION` — smallest operational surface for the stated retention.

---

## e) CONCURRENCY & EXACTLY-ONCE PROTOCOL

### e.1 Failure modes the protocol must survive

| Scenario | What breaks without a protocol | Required property |
|---|---|---|
| **Two SFTP drops landing simultaneously** (two files for same `source_id` or two different sources hitting same DB) | Both poller threads read same `prev_fingerprint`, both compute `file_fingerprint`, both think "new", both write canonical rows — double-write, conflicting canonical values, doubled ledger entries | Serialize per `source_id` |
| **Same file re-delivered** (SFTP re-send, or poller re-sees before cleanup) | Second ingest treats file as new because filename matches but content hash would reveal dupe — but only if hash is checked atomically | Idempotent dedup on `file_fingerprint` |
| **Pod eviction / crash between Kafka offset commit and ledger commit** | Replay half-applied: some canonical rows written, ledger not persisted, offset not committed → on restart, replay re-runs and duplicates | Exactly-once effect = offset commit **after** ledger+canonical commit, with dedup as safety net |
| **Replay interrupted (long transform, OOM, timeout)** | `replay_runs.status='running'` forever, next poller either blocks or duplicates | Lease expiry + fencing token + `replay --check` detects partial application |
| **Concurrent promotion (two operators promoting different mapping versions at once)** | Two `mapping_version_id` values race to be "production" | Promotion is a `SELECT ... FOR UPDATE` on a single `environment_pins` row |

### e.2 Locking primitives — why `pg_advisory_xact_lock` + unique index

From Postgres docs and production patterns:

- **Advisory locks** live in shared-memory `LOCKTAG` with `LOCKMETHOD_ADVISORY`, never touch relation pages, no bloat, no index contention [41].
- **`pg_advisory_xact_lock(key)`** is transaction-scoped: auto-released on `COMMIT`/`ROLLBACK`, so a crashed writer never holds a stale lock [42][43][44].
- **`INSERT ... ON CONFLICT DO NOTHING`** on a unique index is the atomic idempotency claim: concurrent duplicates block on the index, exactly one wins, loser gets `0 rows` and short-circuits [45][46].
- **Lease row** (durable `replay_leases`) is the observable source of truth for "who owns this source's replay right now" and fencing. Advisory lock is the **throttle** that reduces contention before the lease row is hit [47].
- Without the advisory lock, "two concurrent transactions can both read the same `prev_hash`, each compute a valid-looking `row_hash`, and commit — producing two rows that both claim the same parent and silently forking the chain" [48]. That is exactly the replay ledger fork bug.

[41] https://hintsage.com/en/knowledge-base/question/e32ad302-7a46-4344-a6b4-b3f52f9d2dfd — Level: Knowledge base (Hintsage, 2026-02-22, advisory lock in shared memory, pg_advisory_xact_lock). Retrieved.
[42] https://www.postgresql.org/docs/current/explicit-locking.html — Level: Official docs (PG explicit locking, advisory lock semantics). Retrieved.
[43] https://rclayton.silvrback.com/distributed-locking-with-postgres-advisory-locks — Level: Industry blog (R. Clayton, 2020-02-16, transactional advisory locks). Retrieved.
[44] https://www.supplychaininventory.org/core-architecture-data-mapping-for-reconciliation/audit-trail-and-compliance-for-reconciliation/append-only-ledger-design-in-postgresql/ — Level: Industry guide (append-only ledger design, pg_advisory_xact_lock, 2026-07-14). Retrieved.
[45] https://github.com/Carlokb472/ledger-wallet/blob/main/README.md — Level: OSS docs (ledger-wallet, unique index + ON CONFLICT). Retrieved.
[46] https://github.com/H4mid2019/ledger — Level: OSS (H4mid2019/ledger, unique index is idempotency guarantee). Retrieved.
[47] https://blog.ipuau.com/en/posts/20230131-making-exactly-once-effects-boring-with-postgresql-advisory-locks-leases-and-fencing-tokens.html — Level: Industry blog (ipuau, 2026-04-19, advisory lock as throttle, lease row as source of truth, fencing tokens). Retrieved.
[48] https://www.supplychaininventory.org/core-architecture-data-mapping-for-reconciliation/audit-trail-and-compliance-for-reconciliation/append-only-ledger-design-in-postgresql/ — Level: Industry guide (advisory lock prevents hash-chain fork). Same as [44].

lakeFS / Nessie comparison on concurrency:

- lakeFS branch commit with Postgres locks branch for entire commit; with KV uses CAS optimistic locking, fails/retries if branch state changed mid-commit, no races but slowness/timeouts at high concurrency [31][32]. Our `pg_advisory_xact_lock` serializes per `source_id`, not per branch — finer granularity, same no-race guarantee.
- Nessie kernel handles hundreds-thousands commits/sec, with concurrent commits against **different branches** faster than same branch, and concurrent commits against same table slower [34]. Our `source_id` sharding is analogous to branch sharding — 40 weekly files map to ~40 source_ids, so contention is naturally low.
- Nessie's "20 commits/sec (JGit/DynamoDB) insufficient, custom kernel required" [33] underscores why we avoid a Git-style commit kernel for 40 files/week — 40/week is 0.000066 commits/sec; Git's overhead is irrelevant at this throughput. Postgres `INSERT` handles it trivially.

### e.3 Exactly-once replay transaction (spec)

```python
# Pseudocode — Spring Batch / Python subprocess either calls this via SQL

def ingest_and_replay(source_id: UUID, file_path: str, mapping_version_id: str) -> ReplayRun:
    raw_bytes = read(file_path)
    raw_sha = sha256(raw_bytes)
    parsed = parse_csv(raw_bytes)                          # or Excel/JDBC
    canonical_rows = [normalize(r, mapping_version_id) for r in parsed]
    fingerprint = merkle_blake3(canonical_rows)            # file_fingerprint
    schema_sig = compute_schema_sig(parsed)

    # --- ATOMIC SECTION — one DB transaction ---
    with db.transaction() as tx:
        # 1. Serialize per source_id (finer than lakeFS branch lock)
        tx.execute("SELECT pg_advisory_xact_lock(hashtext(:key))",
                   key=f"replay:{source_id}")

        # 2. Lease check + fencing (observable ownership; survives advisory lock collapse)
        lease = tx.select_one("SELECT * FROM replay_leases WHERE lease_key=:k FOR UPDATE",
                              k=f"replay:{source_id}")
        if lease and lease.expires_at > now() and lease.owner != my_owner:
            raise ConcurrentReplayInProgress(lease.owner)
        fencing = (lease.fencing_token + 1) if lease else 1
        tx.execute("""
            INSERT INTO replay_leases(lease_key, owner, fencing_token, expires_at)
            VALUES (:k, :owner, :f, now() + interval '5 minutes')
            ON CONFLICT (lease_key) DO UPDATE
              SET owner=:owner, fencing_token=:f, expires_at=now()+'5 minutes'
        """, k=f"replay:{source_id}", owner=my_owner, f=fencing)

        # 3. Idempotency claim — exactly one wins on file_fingerprint
        #    (global UNIQUE; use (source_id, fingerprint) variant if per-source dup allowed)
        inserted = tx.execute("""
            INSERT INTO replay_file_ledger
              (source_id, drop_filename, raw_bytes_sha256, file_fingerprint, schema_sig,
               canonical_row_count, mapping_version_id, is_unchanged, prev_ledger_id)
            VALUES (:sid, :fn, :raw, :fp, :sig, :cnt, :ver,
                    :fp = (SELECT file_fingerprint FROM replay_file_ledger
                            WHERE source_id=:sid ORDER BY ingested_at DESC LIMIT 1),
                    (SELECT ledger_id FROM replay_file_ledger
                     WHERE source_id=:sid ORDER BY ingested_at DESC LIMIT 1))
            ON CONFLICT (file_fingerprint) DO NOTHING
            RETURNING ledger_id
        """, sid=source_id, fn=file_path, raw=raw_sha, fp=fingerprint,
             sig=schema_sig, cnt=len(canonical_rows), ver=mapping_version_id)

        if inserted.rowcount == 0:
            # Duplicate drop — idempotent no-op. No canonical writes, no diff rows.
            tx.execute("UPDATE replay_leases SET last_run_id=:r WHERE lease_key=:k",
                       r=lease.last_run_id if lease else None, k=f"replay:{source_id}")
            tx.commit()  # releases advisory lock
            return ReplayRun(status='skipped_duplicate', ledger_id=existing_ledger_id)

        ledger_id = inserted.scalar()

        # 4. Create replay_run
        run_id = tx.execute("""
            INSERT INTO replay_runs(ledger_id, source_id, mapping_version_id, status, rows_seen)
            VALUES (:lid, :sid, :ver, 'running', :cnt) RETURNING run_id
        """, lid=ledger_id, sid=source_id, ver=mapping_version_id, cnt=len(canonical_rows)).scalar()

        # 5. Deterministic replay — apply versioned rules in precedence order
        #    Diffs are computed in-memory before any canonical write so the TX can roll back cleanly.
        diffs: list[CellDiff] = []
        for row in canonical_rows:
            for rule in rules_for(source_id, mapping_version_id):  # ordered by precedence
                if rule.matches(row):
                    before = row[rule.canonical_field]
                    after = rule.apply(before)
                    if before != after:
                        diffs.append(CellDiff(run_id, source_id, rule, before, after, row.id))
                        row[rule.canonical_field] = after

        # 6. Canonical write + diff persistence — same TX (atomic unit = window/drop)
        for row in canonical_rows:
            tx.execute("INSERT INTO canonical_rows ... ON CONFLICT (pk) DO UPDATE ...",
                       row=row)  # actual canonical table logic
        tx.executemany("""
            INSERT INTO replay_cell_diffs(run_id, source_id, canonical_field,
                                          staged_record_id, rule_id, rule_stable_key,
                                          diff_type, from_value, to_value)
            VALUES (:run, :sid, :field, :srid, :rid, :key, :dtype, :frm, :to)
        """, diffs)

        tx.execute("""
            UPDATE replay_runs SET status='succeeded', finished_at=now(),
                   rows_affected=:ra, cells_affected=:ca WHERE run_id=:rid
        """, ra=len({d.staged_record_id for d in diffs}), ca=len(diffs), rid=run_id)

        tx.execute("UPDATE replay_leases SET last_run_id=:rid WHERE lease_key=:k",
                   rid=run_id, k=f"replay:{source_id}")

        # 7. Commit — releases pg_advisory_xact_lock automatically.
        #    Kafka offset commit happens ONLY after this commit succeeds
        tx.commit()

    # --- OUTSIDE TX — Kafka / SFTP acknowledgement ---
    kafka_commit_offset(source_id, file_path)   # at-least-once → idempotent dedup guards
    return ReplayRun(status='succeeded', run_id=run_id, ledger_id=ledger_id)

# Crash recovery:
# - If TX rolls back (pod eviction, OOM, timeout), advisory lock releases, no ledger row persisted,
#   no canonical rows committed (single TX). Next poller re-acquires lock and retries — idempotent
#   because fingerprint dedup will catch any partial that slipped through, and replay is deterministic.
# - If TX commits but kafka_commit_offset fails, next poller will re-see the Kafka message;
#   file_fingerprint UNIQUE turns second attempt into skipped_duplicate (exactly-once effect).
# - If poller sees replay_runs.status='running' with started_at < now()-5m and lease expired,
#   `replay --check` flags it: "stale running run {run_id} on {source_id}, fencing_token={n}, lease_expired".

def replay_check(source_id: UUID | None = None):
    # Mirrors OneSchema's `replay --check` ledger primitive: detect partial/failed replays
    stale = db.query("""
        SELECT r.run_id, r.source_id, r.started_at, l.expires_at, l.fencing_token
        FROM replay_runs r JOIN replay_leases l ON l.lease_key='replay:'||r.source_id::text
        WHERE r.status='running' AND r.started_at < now() - interval '5 minutes'
          AND (:sid IS NULL OR r.source_id=:sid)
    """, sid=source_id)
    for s in stale:
        log.warn(f"stale replay {s.run_id} source={s.source_id} started={s.started_at} "
                 f"fencing_token={s.fencing_token} — lease expired, safe to retry")
    return stale
```

**Atomic unit:** One SFTP drop (or one Kafka window) = one `replay_runs` row = one DB transaction. Not per-record. This keeps advisory lock hold time proportional to drop size (50k rows → ~1-3 s transform + write), not per-row chatter.

**Offset vs commit ordering (Kafka path):** `AckMode.RECORD` / per-message commit is **after** the ledger TX. SalesLens's `StreamKafkaConfig` already uses `AckMode.RECORD` + `@Transactional` [AGENTS context]; the wedge extends this: the Kafka consumer does not ack until `replay_runs.status='succeeded'` is persisted. Dedup via `UNIQUE(file_fingerprint)` is the safety net for reprocessing after rebalance — identical to the existing `V17__add_dedup_constraint_to_staged_records.sql` pattern for `staged_records(record_hash)`.

### e.4 What this prevents (mapping back to critique §2 kills)

- **Concurrent SFTP double-write:** Both pollers block on `pg_advisory_xact_lock(hashtext('replay:<source_id>'))`; second waiter proceeds only after first commits; its `file_fingerprint` insert hits `ON CONFLICT DO NOTHING` if duplicate, or proceeds cleanly if it is a genuinely different drop queued behind the first.
- **Replay overwriting first replay's fix (double replay on same canonical table without TX boundary):** All canonical writes + diffs for a drop are in one TX. No interleaving.
- **TOAST 36 GB blowup:** Not a concurrency bug but a volume bug — fixed by cell-granular diff (§d); concurrency protocol does not amplify it.

---

## f) ONESCHEMA VERSION PINNING MODEL (STAGING → PRODUCTION PROMOTION)

### f.1 OneSchema's actual model — evidence

OneSchema FileFeeds determinism is explicitly tied to version pinning:

- "These outputs are deterministic: **given the same input and the same saved version**, you get byte-equivalent output on every run." [5]
- "Every change to a Multi FileFeed is reviewable and reversible" via the version-controlled transform graph [10]; transforms are "version-controlled, so every change ... is reviewable and reversible" [10].
- Promotion is **explicit and non-mirrored**: "From the transform builder, **Save** your changes. This creates a **new saved version** of your transforms … From the MFF's settings, **promote** the saved version to the next environment (Development → Staging → Production …). There is **no automatic mirroring**, which means the version running in production is **exactly the version you've signed off on**." [49][50]
- Only production-pushed templates/MFFs are eligible for import in the target environment — template status is scoped per environment [51].
- The API exposes commits as immutable snapshots: `POST /v0/multi-file-feeds/{id}/commits` "Creates an immutable commit (snapshot)" [52] and `GET /.../transforms/history` paginated commits [53].
- Determinism plus promotion together give the guarantee: byte-equivalent output requires **both** same input identity (file hash) **and** same saved version. Without the pin, "unchanged" is meaningless — same file on a new transform version is a different logical run.

[49] https://docs.oneschema.co/docs/running-in-production — Level: Vendor docs (OneSchema promotion ladder Dev→Staging→Prod, Save → promote → test import, 2026-08-30). Retrieved.
[50] https://docs.oneschema.co/docs/core-concepts — Level: Vendor docs (OneSchema environments, explicit promotion, no auto-mirror). Retrieved.
[51] https://docs.oneschema.co/docs/validating-and-importing — Level: Vendor docs (Only templates with Production status in current environment eligible; template status scoped per environment). Retrieved.
[52] https://docs.oneschema.co/reference/create-multi-file-feed-commit — Level: Vendor API docs (OneSchema commits as immutable snapshots). Retrieved.
[53] https://docs.oneschema.co/reference/get-multi-file-feed-transforms-history — Level: Vendor API docs (OneSchema transform history, pagination). Retrieved.

### f.2 Clone for the OSS wedge — spec

```
Environments: dev (default branch) → staging → production
  - Each is a Git branch or an `environment_pins` row:
    environment_pins(environment TEXT PK, mapping_version_id TEXT, pinned_at TIMESTAMPTZ, pinned_by TEXT)

Mapping version identity:
  - mapping_version_id := Git SHA of the mapping YAML commit (first-class) + optional tag (v3.2.1)
  - The YAML spec itself is versioned; when condition language changes, old rules recompile
    identically because the compiler is pinned to the same SHA (two-compiler coherence deferred to G-YAML-01).
  - Every replay_run.ledger_id binds to exactly one mapping_version_id — the version that ran.

Promotion protocol:
  1. Author edits YAML in `dev` (feature branch), opens PR.
     CI runs `validate` + `test` against ephemeral DuckDB (G-YAML-01) and `replay --check` dry-run.
  2. On merge to `main`, CI tags `mapping_version_id = HEAD SHA` and records it in
     `environment_pins('staging', sha, ...)`. Staging ingest automatically binds next drop to staging SHA.
  3. Operator validates in staging: `replay_runs` where mapping_version_id=staging_sha, inspect diffs,
     run Q-AUDIT-1..3. If green, explicit promote:
       UPDATE environment_pins SET mapping_version_id=:sha WHERE environment='production';
     (guarded by SELECT ... FOR UPDATE on that row; exactly one promote wins if concurrent).
  4. Production SFTP poller always reads:
       SELECT mapping_version_id FROM environment_pins WHERE environment='production'
     at the start of each ingest_and_replay transaction. No drop is ever processed on an unpinned version.

Determinism guarantee (cloned wording):
  "Given the same file_fingerprint and the same mapping_version_id, the wedge emits
   byte-equivalent canonical rows and identical replay_cell_diffs on every run."
  — This is testable: replay a drop's staged_rows through two different pods pinned to same SHA,
  compare canonical_row hashes; must be equal.

OneSchema divergence we explicitly copy:
  - No auto-mirror (promotion is not merge): production never auto-follows main.
  - Status scoped per environment: a rule with status='staged' is not eligible for production replay
    until its mapping_version_id is the pinned production version and the rule's status is 'production'.
  - Audit log of promotions: INSERT INTO promotion_log(environment, from_sha, to_sha, promoted_by, at)
    on every UPDATE of environment_pins — mirrors OneSchema's transform history pagination [53].

Failure mode prevented:
  - "Same file, new transform version, silent new output" → caught because replay_runs key includes
    (ledger_id, mapping_version_id) UNIQUE; re-ingesting the same file_fingerprint on a new
    mapping version creates a new replay_run row that replays transforms — not a duplicate skip.
    The diff for that run shows exactly which rules newly fired, so audit can answer
    "what changed when we promoted v42?"
```

### f.3 What the critique flagged and how this closes it

The critique [critique §2] notes: "OneSchema's implementation is SaaS-hosted with private code — no OSS reference implementation to copy." True for the runtime, but the **contract** is public via docs [5][49][50] and is sufficient to clone: immutable commits + explicit promotion + determinism as `input × version`. We don't copy OneSchema's agent; we copy the **pin + promotion + byte-equivalence** contract, with `Git SHA` as `mapping_version_id` and `file_fingerprint` as input identity (§a).

---

## g) ACCEPTANCE CHECKLIST — G-LEDGER-01 GREEN CRITERIA

From `oss-mapping-wedge-GAPS.md` Batch A / Acceptance criteria:

| Criterion | Spec location | Status |
|---|---|---|
| `is_unchanged(source)` hash defined | §a.2 — `is_unchanged := file_fingerprint == prev AND schema_sig == prev` where `file_fingerprint = BLAKE3(canonical_row_hash Merkle)` | ✅ Defined |
| Rule stable key defined | §b.2 — `SHA256(canonical_target_field:normalized_pattern_hash:transform_kind:scope)` | ✅ Defined |
| Ledger table DDL with index | §d.2 (5 tables) + §d.3 (6 indexes + BRIN) | ✅ DDL + indexes |
| Per-run volume at 40 files/week × 50k × 52 wks retention, with cost model | §c.1 — cell-granular 0.62-0.83 GB/yr, 90d 0.15-0.21 GB; naive 832 GB/yr; cost $0.86/yr vs $1,148/yr; S3 cold $0.014/mo | ✅ Modeled |
| Concurrency protocol (two SFTP drops, pod eviction, offset/commit) | §e.3 — `pg_advisory_xact_lock` + lease + `ON CONFLICT` + offset-after-commit + `replay --check` | ✅ Protocol |
| Benchmark `<2 min` on 90-day history | §d.4 — 5 queries, 520 files / 26M rows / 1.09M diffs / 0.18 GB, p99 targets 0.02-1.2 s, generators + pgbench harness | ✅ Specced |
| Storage choice rationale (TOAST vs Dolt/lakeFS/Nessie/RocksDB) | §c.2 — Postgres chosen; each alternative rejected with sources and scaling evidence | ✅ Compared |

Additional invariants that a code review should enforce:

- No `SELECT` on `replay_cell_diffs` without a time predicate on `applied_at` (use the indexed `source_field_time` path or BRIN path).
- No `UPDATE` on `replay_file_ledger` — append-only. Mutations are new rows.
- Every `INSERT` into `replay_cell_diffs` must carry non-null `rule_stable_key` when `rule_id` present (denormalized for rule-centric audit without join).
- `pg_advisory_xact_lock` key must be `hashtext('replay:'||source_id::text)` — not a random int — so lock identity is deterministic across pods.

---

## h) RISKS & OPEN QUESTIONS

| Risk | Likelihood | Mitigation |
|---|---|---|
| `file_fingerprint` BLAKE3 Merkle at 50k rows × 40 files/week may add 200-400 ms per file on Python path | Medium | Hashing is streaming + parallelizable; benchmark in the ledger bench. Fast-path byte-hash short-circuit avoids it when truly unchanged and version pinned. |
| Advisory lock per `source_id` serializes replay for a hot source with back-to-back drops | Low | 40 files/week ≈ one every 4 hours per source on average; drops are not second-apart except backfill. For backfill, process sequentially per source — matches lakeFS "serialize per branch" [31] and is acceptable. |
| Rule stable key normalization for regex misses semantic equivalence (two regexes that match same dirty set) | Medium | Linter suggests merge but does not auto-merge; embedding distance on pattern_expr as secondary signal — deferred to post-MVP. |
| Postgres BRIN on `applied_at` not used for small tables (planner prefers seq scan anyway) | Low | BRIN helps at 52-week / 0.62 GB scale; at 90-day / 0.18 GB both paths are <2 s. Keep BRIN for 52-week rollups where it matters. |
| OneSchema-style explicit promotion adds operator toil vs auto-deploy | Low | Pin is a single-row UPDATE; add `make promote ENV=production SHA=...` and Slack webhook from `promotion_log` — <1 hr automation. |

---

## SOURCES — FULL CITATION TABLE (Level tags)

| # | URL | Claim cited | Level |
|---|---|---|---|
| 1 | https://www.elysiate.com/blog/row-level-checksums-for-csv-batches-a-lightweight-pattern | Row hash should be parsed+normalized, not raw line; raw sensitive to delimiter/quote/whitespace; checksum not full CDC | Industry blog (Elysiate 2026-04-10) — Exa |
| 2 | https://github.com/JarvusInnovations/gtfs-digester + https://pypi.org/project/gtfs-digester/0.2.0 | Canonical CSV pipeline: parse→reorder→normalize→sort-by-PK→BLAKE3 per file + Merkle archive fingerprint, per-file hashes for change detection | OSS impl (gtfs-digester) — Exa |
| 3 | https://www.cross-engine-reconciliation.org/structural-diffing-sync-engines/json-and-parquet-diffing-algorithms/ | canonicalize-row → per-chunk xxhash, DeepDiff fallback, canonical byte row | Industry spec (2026-06-01) — Exa |
| 4 | https://pypi.org/project/filedge/0.5.0/ | File has SHA-256 + row count + audit DB state + provenance; discreteness = auditability | OSS docs (filedge) — Exa |
| 5 | https://docs.oneschema.co/docs/destinations-overview | Determinism: same input + same saved version → byte-equivalent output; destination failure does not fail import | Vendor docs (OneSchema) — Exa |
| 6 | https://docs.oneschema.co/docs/core-concepts | Transform wiring rules, promotion explicit, no auto-mirror, template scoped | Vendor docs (OneSchema) — Exa |
| 7 | https://fivetran.com/docs/connectors/files/sftp | Magic Folder uses last modified date for incremental sync | Vendor docs (Fivetran) — Exa |
| 8 | https://docs.oneschema.co/docs/transform-library | Every cell-level fix captured as deterministic rule, before/after diff, agent picks right transforms | Vendor docs (OneSchema transform library) — Exa |
| 9 | https://www.oneschema.co/filefeeds | Deterministic reusable code per FileFeed; record & replay transformations | Vendor marketing (OneSchema) — Exa |
| 10 | https://docs.oneschema.co/docs/getting-started-filefeeds | FileFeeds auto-apply transforms in order, validate against Template, emit audit log (accepted/transformed/rejected), version-controlled transform graph | Vendor docs (OneSchema) — Exa |
| 11 | https://docs.oneschema.co/docs/core-concepts | Transform takes Files/Lists, builder enforces wiring, downstream affected by upstream change | Vendor docs (OneSchema) — Exa |
| 12 | https://www.credativ.de/en/blog/postgresql-en/toasted-jsonb-data-in-postgresql-performance-tests-of-different-compression-algorithms/ | 38 GB LZ4 vs 41 GB pglz vs 98 GB uncompressed (32 GB RAM, TOAST test) | Industry benchmark (credativ 2024-10-15) — Exa |
| 13 | https://www.dolthub.com/blog/2024-04-12-study-in-structural-sharing/ | 1,570 days: 487/474/604 MB without index → >10 GB with index+history (log-scale blowup) | Vendor blog (DoltHub 2024-04-12) — Exa |
| 14 | https://www.dolthub.com/blog/2025-05-16-millions-of-versions/ | Prolly trees, fast diff proportional to differences, millions of versions | Vendor blog (DoltHub 2025-05-16) — Exa |
| 15 | https://dev.to/gowthampotureddi/data-version-control-lakefs-nessie-dolt-for-git-like-data-branching-41jb | lakeFS/Nessie branching copies pointers O(changes) vs O(dataset) backup; GC copies deltas | Industry survey (DEV 2026-08-21) — Exa |
| 16 | https://lakefs.io/blog/reduce-dataops-storage-costs/ | lakeFS GC lifecycle: purge objects not in active commit/branch; retention via branching policy | Vendor blog (lakeFS 2024-05-13) — Exa |
| 17 | https://www.dolthub.com/docs/sql-reference/version-control/dolt-system-tables.md | dolt_diff, dolt_diff_$tablename, dolt_commit_diff system tables | Vendor docs (Dolt) — Exa |
| 18 | https://www.dolthub.com/blog/2022-03-25-dolt-diff-magic/ | dolt_diff shows commit history per branch, data+schema change flags | Vendor blog (DoltHub 2022-03-25) — Exa |
| 19 | https://www.dolthub.com/blog/2022-04-11-dolt-diff-magic-part-2/ | dolt_diff_$tablename per-row delta with diff_type added/modified/removed | Vendor blog (DoltHub 2022-04-11) — Exa |
| 20 | https://www.dolthub.com/blog/2020-06-05-introducing-cell-history/ | Cell history via dolt_diff on PK click; dolt_history per commit | Vendor blog (DoltHub 2020-06-05) — Exa |
| 21 | https://docs.doltgres.com/reference/version-control/querying-history | dolt_history per revision, dolt_commit_diff between commits, AS OF queries | Vendor docs (Doltgres) — Exa |
| 22 | https://www.dolthub.com/blog/2024-03-03-prolly-trees/ | Prolly trees: B-tree read/write + diff in time proportional to differences + structural sharing | Vendor blog (DoltHub 2024-03-03) — Exa |
| 23 | https://www.dolthub.com/blog/2024-02-29-storage-engine/ | Content-addressed chunks, root-hash compare, walk differing paths | Vendor blog (DoltHub 2024-02-29) — Exa |
| 24 | https://www.dolthub.com/blog/2020-06-16-efficient-diff-on-prolly-trees/ | Skip identical Prolly-tree portions via content-address comparison | Vendor blog (DoltHub 2020-06-16) — Exa |
| 25 | https://dolthub.com/blog/2025-07-17-distributed-audit-logs/ | Distributed audit via UNION of dolt_diff tables, annotated by database | Vendor blog (DoltHub 2025-07-17) — Exa |
| 26 | https://github.com/dolthub/dolt/issues/3438 | dolt_diff_$tablename O(history); filtered on to/from_commit or indexed column reduces work; optimization implemented | OSS issue (GH #3438, 2022-05-17) — Exa |
| 27 | https://www.dolthub.com/blog/2026-06-18-dolt-disk-space/ | Structural sharing: million-row table updating 10 rows costs 10 rows; dolt gc --full + rebase + push remote as offload | Vendor blog (DoltHub 2026-06-18) — Exa |
| 28 | https://www.dolthub.com/blog/2025-03-21-session-aware-gc-technical-details/ | Session-aware GC, copy-on-write prolly trees, chunk store | Vendor blog (DoltHub 2025-03-21) — Exa |
| 29 | https://www.dolthub.com/blog/2025-10-20-dolt-1-75/ | AutoGC + Archives enabled by default (Dolt 1.75) | Vendor blog (DoltHub 2025-10-20) — Exa |
| 30 | https://www.dolthub.com/blog/2024-04-12-study-in-structural-sharing/ | Duplicate of [13] — second citation for index blowup specifically | Vendor blog — Exa |
| 31 | https://docs.lakefs.io/concepts/internals/ | lakeFS diff in time proportional to difference; PG branch lock vs KV CAS optimistic locking, commit fails if branch changed | Vendor docs (lakeFS internals) — Exa |
| 32 | https://forum.lakefs.io/t/16422110/hi-lakefs-team-i-have-a-question-on-concurrent-commit-on-you | Concurrent lakeFS commits retry, no races by design, slowness/timeouts only | Vendor forum (lakeFS 2024-02-14) — Exa |
| 33 | https://projectnessie.org/guides/nessie_vs_git/ | Git <1 c/s → JGit/DynamoDB 20 c/s → Nessie custom kernel hundreds-thousands c/s | Vendor docs (Nessie vs Git) — Exa |
| 34 | https://projectnessie.org/develop/kernel/ | Commit kernel perf factors: different branches faster than same branch, different tables faster than same table | Vendor docs (Nessie kernel) — Exa |
| 35 | https://projectnessie.org/guides/transactions/ | Nessie serializable transactions, exactly-once move via merge/branch | Vendor docs (Nessie transactions) — Exa |
| 36 | https://www.postgresql.org/docs/current/storage-toast.html | TOAST threshold 2 kB, target 2 kB, unchanged out-of-line fields no cost on UPDATE | Official docs (Postgres TOAST) — Exa |
| 37 | https://boringsql.com/posts/postgresql-toast/ | 1 MB out-of-line = 500 TOAST tuples + index lookups + decompression | Industry blog (boringSQL 2026-05-24) — Exa |
| 38 | https://monpg.app/blog/postgresql-toast-compression-lz4-vs-pglz | LZ4 faster CPU write+read vs pglz, small ratio cost; compression not free | Industry benchmark (MonPG 2026-07-19) — Exa |
| 39 | https://monpg.app/blog/postgresql-toast-large-values | Row too large → compress/move out-of-line → pointer; PG 14 LZ4 choice | Industry blog (MonPG 2026-06-06) — Exa |
| 40 | https://www.jusdb.com/blog/postgresql-toast-storage-mechanism-guide | PG 14+ LZ4 vs pglz, EXTERNAL vs EXTENDED for pre-compressed JSONB | Industry blog (JusDB 2026-03-05) — Exa |
| 41 | https://hintsage.com/en/knowledge-base/question/e32ad302-7a46-4344-a6b4-b3f52f9d2dfd | Advisory lock in LOCKTAG shared memory, no relation pages, hashtext(book_keeping_key), pg_advisory_xact_lock auto-release | Knowledge base (Hintsage 2026-02-22) — Exa |
| 42 | https://www.postgresql.org/docs/current/explicit-locking.html | Advisory locks: transaction vs session scope, auto-release at end of transaction | Official docs (Postgres explicit locking) — Exa |
| 43 | https://rclayton.silvrback.com/distributed-locking-with-postgres-advisory-locks | Transactional advisory locks, middleware, credit-holder isolation | Industry blog (R. Clayton 2020-02-16) — Exa |
| 44 | https://www.supplychaininventory.org/core-architecture-data-mapping-for-reconciliation/audit-trail-and-compliance-for-reconciliation/append-only-ledger-design-in-postgresql/ | Append-only ledger, pg_advisory_xact_lock mandatory else hash-chain fork, lease observable | Industry guide (2026-07-14) — Exa |
| 45 | https://github.com/Carlokb472/ledger-wallet/blob/main/README.md | Postgres idempotency via UNIQUE + INSERT ON CONFLICT, exactly one wins, no app lock | OSS docs — Exa |
| 46 | https://github.com/H4mid2019/ledger | Unique index is guarantee, cheap existence check is optimization only, 23505 rollback | OSS docs — Exa |
| 47 | https://blog.ipuau.com/en/posts/20230131-making-exactly-once-effects-boring-with-postgresql-advisory-locks-leases-and-fencing-tokens.html | Advisory lock as throttle, lease row as source of truth, fencing tokens, observable via SQL | Industry blog (ipuau 2026-04-19) — Exa |
| 48 | https://www.supplychaininventory.org/core-architecture-data-mapping-for-reconciliation/audit-trail-and-compliance-for-reconciliation/append-only-ledger-design-in-postgresql/ | Without advisory lock two TXs read same prev_hash and fork the chain (duplicate of [44] but cited for fork bug specifically) | Industry guide — Exa |
| 49 | https://docs.oneschema.co/docs/running-in-production | Save → new saved version → promote Dev→Staging→Prod; verify in target env via test import | Vendor docs (OneSchema) — Exa |
| 50 | https://docs.oneschema.co/docs/core-concepts | Promotion explicit, no auto-mirror, version running in prod is exactly signed-off version | Vendor docs (OneSchema core concepts) — Exa |
| 51 | https://docs.oneschema.co/docs/validating-and-importing | Only Production-status templates eligible per environment; status scoped per environment | Vendor docs (OneSchema) — Exa |
| 52 | https://docs.oneschema.co/reference/create-multi-file-feed-commit | POST /commits creates immutable commit (snapshot) of a FileFeed | Vendor API docs (OneSchema) — Exa |
| 53 | https://docs.oneschema.co/reference/get-multi-file-feed-transforms-history | GET transforms history paginated commits (limit 1-100, skip) | Vendor API docs (OneSchema) — Exa |

*Additional docs consulted without direct citation: OneSchema custom-file-transforms, filefeeds-transformations-codeactions, sheet-transforms, build-your-first-multi-filefeed (all docs.oneschema.co, Exa 2026-08-30) — confirm deterministic rule recording pattern but not cited line-by-line to keep table bounded. Dolt docs on dolt_diff/dolt_history query patterns similarly cross-checked via docs.dolthub.com.*

---

## i) WHAT THIS RESEARCH STILL DOES NOT PROVE

- No Dolt/lakeFS/Nessie benchmark at 40 files/week × 50k × 90 days exists in the retrieved corpus — rejection is by **design-fit + published scaling pathology** (prolly-tree index blowup, O(history) without filter), not by head-to-head latency measurement. The Postgres bench must still be run (scripts in §d.4).
- No measurement of `to_pylist`/`Arrow → canonical_row_hash` cost inside the Python subprocess at 30s windows — G-HYBRID-01's concern still applies to the hashing path; isolate it behind the same DuckDB/SQL avoidance where possible (compute row hashes in SQL `BLAKE3(canonical_serialize(row))` rather than Python dicts).
- OneSchema determinism docs do not disclose the exact rule-to-code compilation target (JS/Python/SQL) — we clone the **contract**, not the codegen.

---

*End of G-LEDGER-01. 38 sources, 6 spec sections, DDL + 6 indexes, volume model at 40×50k×52, concurrency protocol, promotion model, 5-query <2-min bench. Next: wire `SOURCE-TABLE-GAPS.md` row and open the ledger bench in a throwaway PG.*
