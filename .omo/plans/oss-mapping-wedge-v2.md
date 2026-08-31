# OSS Semantic Mapping Wedge — Build Plan v2 (Hardened, Post-Critique Batch A)

**Date:** 2026-08-30 · **Status:** Hardened Draft v2 — P0 Batch A (4/5 landed) fixes applied, G-MOAT-01 + Batch C pending
**Research base:** 78 sources → 5 hypotheses → 55 evidence notes → synthesis (35-92% conf) → brutal critique (420 lines, 7 P0) → gap research (4 P0 specs landed, 866+837+955+422 lines)
**Wedge:** recurring SFTP replay + audit ledger · dbt YAML→SQL motion · model-agnostic harness · opinionated ER + seam · hybrid plane (Java window + Python subprocess)
**Overall confidence (rescaled wedge):** 55-68% → hardening raises to 62-72% on P0-closed slices

---

## 0. What v2 Fixes vs v1 (Gap-Driven Hardening)

| Gap | Critique Kill Mode | Fix in v2 | Artefact | P0 Gate |
|-----|-------------------|-----------|----------|---------|
| **G-YAML-01** | `SELECT order` / `Revenue ($)` unquoted fails every warehouse; `validate` syntax-only → nightly replay 3 AM page; Soda + mapping dual-compiler diverges | **IR mandatory:** `MappingIR` thin dataclasses (portable predicate/transform/rule/file) + sqlglot Dialect lowering (`IDENTIFIER_START/END`, `DATE_PART_MAPPING`, `TRANSFORMS`) + `semantic_manifest.json` with `SemanticValidationFailure` hard gate + JSON Schema + version pin + shared `quote(col,dialect)` lib; execution-tested `validate` = T1 JSON Schema → T2 `information_schema` binding → T3 ephemeral `duckdb.connect(":memory:")` + BigQuery `dryRun:true` (zero-slot); bad-emit triad (`order` reserved, `Revenue ($)` punctu, alias-unquoted `ORDER`) in Suite A proves T3 catches what T1 misses | `research/G-YAML-01.md` + `gap-research/G-YAML-01-SPEC.md` (955 lines, 22 refs) | YAML with `order`/`Revenue ($)` compiles and `validate` EXECUTES SQL on ephemeral DuckDB catching quoting bugs |
| **G-LEDGER-01** | `is_unchanged` byte-hash false ("reordered rows + timestamp" → byte change, logic didn't) defeats zero re-verify; ledger Postgres TOAST 400 MB/run → 36 GB/90d / 14 min audit; concurrent drop overwrites first replay; no rule identity → `v4` explosion | **`is_unchanged = canonical_row_hash` Merkle:** `BLAKE3(sorted canonical_row_hash per row)` → `file_fingerprint = BLAKE3(sorted file:hash pairs)`; byte SHA-256 fast-path only. **Stable key:** `SHA256(field ‖ normalized_pattern_hash ‖ kind ‖ scope)` — same dirty at line 904 = match. **Storage:** plain Postgres append-only (3 tables + JSONB cell-granular `{field,from,to,rule_id}`) — Dolt/lakeFS/Nessie rejected (Dolt >10 GB GC, `dolt_diff` O(history), lakeFS object-store mismatch). Volume: **0.62 GB/yr / 0.18 GB/90d** vs 832 GB/yr naive — $0.86/yr vs $1,148/yr. **Concurrency:** `pg_advisory_xact_lock(source_id)` + `UNIQUE(file_fingerprint)` + `replay_leases` fencing + single-TX drop atomic + offset-after-commit + `replay --check` stale detection. **OneSchema pin:** `mapping_version_id = Git SHA` per env + `environment_pins` + `same fingerprint × same version → byte-equivalent`. Audit 0.08-1.2 s on 90d/26M rows. | `research/G-LEDGER-01.md` (866 lines, 38 sources) | `is_unchanged` hash + stable key + DDL + 0.18 GB/90d + advisory lock + <2 min benchmark |
| **G-SCHEMA-01** | Breaking taxonomy missing — `rev→revenue_net` looks "nullable add" at raw, is MAJOR at canonical (zero-row field); `validate` never checks FK≡PK junction; version bump never enforced → silent breaking drift; warehouse null/case/quoting variance unchecked | **Breaking taxonomy:** Confluent-style `BACKWARD/FORWARD/FULL/FULL_TRANSITIVE` at mapping layer. Rename `rev→revenue_net` = MAJOR at canonical surface; every FK retarget / PK change / m:n grain = MAJOR with DDL migration. **Registry gate:** `major_version` partitions history; BREAKING without MAJOR → `exit 1` with `RENAME_MAPPED_FIELD` + field; `422 kind+field` precedent; transitive `FULL` vs priors; consumer-ACK on MAJOR; GitHub Actions snippet + worked `rev→revenue_net` CI output proves gate. **Matrix:** 16-row Scope×Change → Bump × Migration × Gate `kind` (`FK_RETARGET`, `FK_EQ_PK_DRIFT`, `MN_JUNCTION_ADDED`, `RESERVED_UNQUOTED`). **FK≡PK:** junction `@@id([orderId,productId])` invariant, `fk_is_half_of_pk` detection, `rebuild_constraints` per-child canonical DDL, BFS blast radius, `relationships+not_null` test suite. **Variance:** `contracts/dialect-matrix.yaml` (5 dialects × quoting char / case folding / `IS DISTINCT FROM` null-safe / type canonicalization). **Harness:** sqlglot transpile dry-run (BQ without creds) + ephemeral DuckDB Suites A/B/C. | `research/G-SCHEMA-01.md` (837 lines, 54 sources) | CI rejects rename without MAJOR bump; covers m:n+FK≡PK drift |
| **G-HYBRID-01** | Arrow `to_pylist` GIL (20% GC, 25% GetScalar, 24× vs ndarray) → hybrid slower than Java; per-window spawn 2.0 s × 2,880 = 96 min/day; JSON 1.1-2.4 s, Parquet 350-700 ms; not measured end-to-end (scan micro win erased by serialization) | **Serialization:** Arrow IPC **stream** over stdout pipe, length-prefixed, 8-64k rows/batch — NOT JSON (380 ms→6.2 s at 5k→200k), NOT Parquet (350-700 ms small-window overhead), NOT Flight/gRPC. Optional `ShmPipeTransport` ≥100k batches (7045 vs 3324 MB/s). Batch p99: IPC 45 ms→420 ms vs JSON 380 ms→6.2 s. **Quality path:** **All 6 checkers as DuckDB SQL aggregates on `arrow_scan` (zero-copy) — `UNION ALL` single round-trip, 1-10 rows materialized — NO `to_pylist`**. **Lifecycle:** long-lived daemon+forkserver pool (2-4 workers), `request_queue→Worker→response_queue` (soothe/py-drakkar), watermark `last_offset+1` after ledger+canonical, SHA-256 dedup idempotent, contiguous HWM. **Cold-start:** warm daemon p50 0.58 s / p99 1.16 s (20-col) / 3.5-8.2 s (180-col) = 11-27% of 30s interval PASS; per-window spawn 40-67% FAIL. | `research/G-HYBRID-01.md` (422 lines, 26 rows) | IPC stream chosen; DuckDB SQL aggregates (no `to_pylist`) OR measured p99 at 30s window; p99 <50% of window interval |

**Pending G-MOAT-01 (+ 8-search AI eval expanded per your ask):** Sato/TURL transfer + LLM factorial/harness/production handling — will land as Block A/B/C of `MODEL-EVAL-PROD-SPEC.md` P0 gate. **Pending Batch C (7× P1):** Soda wrapping, Splink blocking, Dagster distribution, Elementary, BPM seam, tenancy/pricing, consistency — P1/P2, parallel with wedge prototyping once P0 A green.

---

## 1. Rescaled Wedge (Unchanged — Post-Falsification)

Recurring SFTP replay + audit ledger. Zero re-verification on unchanged sources (Merkle BLAKE3) + <2 min audit (indexed `replay_cell_diffs`) — "SFTP drops that map themselves after first approval." Not one-shot 50% (H1 provisional, strong on bottleneck 2-4h of 58h + CHOKE). Pitch phase-specific 60-70% mapping-phase where Tamr/Forrester holds, but headline is recurring replay.

OSS motion: **dbt motion** (YAML rules → SQL/Python compilation) — connectors commoditised ("only move bytes"), dbt governance won 30k customers. Engine Apache 2.0 (YAML + tests + ledger + replay) + cloud governance/audit/caching per-seat $0-500/mo. Dagster asset/component + Python library (Soda/MetricFlow pattern), not service — Airlift incremental migration.

Harder moat (still P0-if-in-MVP / P2-if-deferred): learned type inference with table context (Sato CRF + TURL Transformer + RECA) on dirty retail/CRM 180-col wide tables — dataset incumbents haven't productised — plus cross-dataset blocking learner. Deferred to gated research track unless dirty-CRM F1 with p99 <500 ms clears.

---

## 2. Hardened Specs (Per P0 Artefact Ready to Paste)

### 2.1 YAML→SQL Compilation — Fixes Applied (G-YAML-01)

**Grammar:** JSON Schema Draft 2020-12 `mapping.schema.json` — `source_column` (string, required), `canonical_entity` + `canonical_field` (enum from registry), `confidence` (0-1), `verification_state` (pending/confirmed/ignored), `deterministic_condition: {sql_predicate, dialect}` (structured, not raw string), `transform` enum (not free-form), `type` (from registry), `audit: {author, commit, rule_id}`. Invariant table (e.g., `deterministic_condition.dialect` must be one of `duckdb|postgres|bigquery`). 3-tier validation split mirrors `dbt parse → SemanticManifestValidator → data-platform`.

**IR required:** `MappingIR` dataclasses (per `dbt-fusion` #9762/#9750 precedent) — thin portable `Predicate/Transform/Rule/File` → `dataflow plan → abstract SQL → engine-SQL` via optimizers. Direct YAML→SQL reproduces ambiguous-column/`__` dedup bugs (`ambiguous column 'YELLOW'` #1930). Solo dev bounding: week not quarter; no optimizer/time-grain cost (the heavy part that killed MetricFlow's first pass is skipped).

**Dialect matrix:** sqlglot `Dialect/Generator` (`IDENTIFIER_START/END`, `DATE_PART_MAPPING`, `TIME_MAPPING`, `TRANSFORMS`) is the proven lowering layer. Tables for quoting (`"` vs `` ` ``), alias quoting (#2058 fix), string/regex (arg-order flip `TRY_STRPTIME` ↔ `SAFE.PARSE_DATE`), date (`DATE_ADD` flips), null (`TRY_CAST`/`NUMERIC`/`IS DISTINCT FROM`), type canonicalization. Shared `quote(col,dialect)` lib used by both mapping compiler and Soda wrapper — single quoting authority.

**Validate harness:** T1 offline JSON Schema → T2 `information_schema` binding → T3 **execution** `EXPLAIN` over ephemeral `duckdb.connect(":memory:")` + BigQuery `dryRun:true` (zero-slot, no charge per cloud.google.com). PR `mapping diff` compares canonicalized manifests, not raw YAML. MetricFlow missed `ambiguous column 'YELLOW'` at `sl validate` but caught it at query-time #1930 — our T3 closes that gap. Example triad `Revenue ($)` / `order` / alias-unquoted `ORDER` in Suite A proves T3 catches what T1 misses: bad emit `SELECT Revenue ($) AS revenue` fails at T3 with "no such column."

**Spec versioning:** `spec_version: "0.1"` (SemVer major=breaking IR change blocks without migrate, minor=additive). Manifest pins `generator_version` for replay idempotence (`same fingerprint × same version → byte-equivalent`). `mapping migrate --dry-run/--apply` with `v1_0_to_v1_1.py` modules.

**Coherence:** Soda issue #2108 ("if you don't quote in YAML, generated queries won't be quoted") is the exact dual-compiler divergence. Fix: Soda contracts generated from MappingIR (not hand-authored) + `mapping validate --coherence` cross-EXPLAIN (`quoted fragment equality` + canonical round-trip).

### 2.2 Replay Ledger — Fixes Applied (G-LEDGER-01)

**is_unchanged:** `file_fingerprint = BLAKE3( sorted{ BLAKE3(parsed+normalized+sorted row bytes) } )` — per-row canonical hash, not byte hash. Byte SHA-256 fast-path `hash(file_bytes)` only short-circuit; additive delta (3 new rows in 50k) → only those replay. Elysiate 2026-04-10: raw-lines hash sensitive to delimiter/whitespace/quoting/column-order noise.

**Stable key:** `rule_stable_key = SHA-256(field ‖ normalized_pattern_hash ‖ kind ‖ scope)` — pattern determines identity. Same dirty at line 904 = match, not `v4`. Regex equivalence flagged, not auto-merged.

**Storage:** Plain Postgres append-only ledger (3 tables: `replay_runs`, `replayed_files` with `UNIQUE(file_fingerprint)`, `replay_cell_diffs` JSONB array `{field,from,to,rule_id}` per mutated row). Dolt/lakeFS/Nessie rejected: Dolt >10 GB with index at 1,570 days, `dolt_diff` O(history) without commit-range, lakeFS/Nessie object-store mismatch for CSV drops. Volume: cell-granular 0.62 GB/yr (1.09M diffs) / 0.18 GB/90d vs naive row blob 832 GB/yr / 36 GB/90d — $0.86/yr vs $1,148/yr at scale. Indexes: B-tree `(source_id, canonical_field, applied_at)` + `run_id` + `rule,time` + BRIN on `applied_at` + GIN on changed fields. Full `pgbench` harness in spec.

**Concurrency / exactly-once:** Single TX: `pg_advisory_xact_lock(hashtext(source_id::text))` → `INSERT ... ON CONFLICT (file_fingerprint) DO NOTHING` → write `replay_runs` → `COMMIT` (releases lock). Kafka `last_offset+1` watermark committed only after ledger+canonical commit; `replay_leases` fencing + contiguous HWM; DLT after 3×1s; pod-eviction: `replay --check` stale detection via lease heartbeat. Prevents fork, double-write, replay-over-replay.

**OneSchema pin:** Immutable `saved version` as `mapping_version_id = Git SHA` per environment + `environment_pins` + `Dev→Staging→Prod` explicit promotion (no auto-mirror). Determinism: `same fingerprint × same version → byte-equivalent`.

**Audit <2 min:** Indexed query on `replay_cell_diffs` with `source_id + field + time window` → 0.08-1.2 s on synthetic 90 days × 36 GB-scale projection (1,040 files, 26M rows, 1.09M diffs, 0.18 GB). Bench spec included.

### 2.3 Schema Evolution — Fixes Applied (G-SCHEMA-01)

**Taxonomy:** Confluent `BACKWARD/FORWARD/FULL/FULL_TRANSITIVE` at mapping layer. `rev→revenue_net` = MAJOR at canonical surface (zero-row field → MAJOR with DDL migration), even if raw layer sees it as "nullable add". Every FK retarget / PK change / m:n grain = MAJOR. 14-row breaking taxonomy (§1.2) maps each case.

**Registry gate:** `major_version` partitions history; BREAKING without MAJOR → `exit 1` with `RENAME_MAPPED_FIELD` + field; `422 kind+field` precedent (data-contract-registry L3); transitive `FULL` vs all priors (not just latest); consumer-ACK on MAJOR before merge; GitHub Actions snippet + worked `rev→revenue_net` CI output proves gate rejects without bump.

**Compatibility matrix:** 16-row Scope×Change table (field/type/nullability/FK/PK/m:n/identifier/condition → Bump × Migration × Gate `kind` (`FK_RETARGET`, `FK_EQ_PK_DRIFT`, `MN_JUNCTION_ADDED`, `RESERVED_UNQUOTED`)) routable to selective gates.

**FK≡PK / m:n:** Junction `@@id([orderId,productId])` invariant (Prisma 8), `fk_is_half_of_pk` detection, `rebuild_constraints` per-child canonical DDL migration (Percona), BFS blast radius, `relationships+not_null` test suite.

**Variance:** `contracts/dialect-matrix.yaml` (5 dialects × quoting char, alias-unquoted GH #2058, case folding, `IS DISTINCT FROM` null-safe, type canonicalization) checked into repo.

**Harness:** sqlglot dry-run (BQ without creds — lenient) + ephemeral DuckDB Suites A (bad triad), B (FK orphan + junction PK + type/narrow + grain), C (null-safe `IS DISTINCT FROM`); 6-step `validate` sequence with `0/1/2` exits proves `validate ≠ syntax-only`.

### 2.4 Hybrid Plane — Fixes Applied (G-HYBRID-01)

**Serialization:** Arrow IPC **stream** over stdout pipe, length-prefixed, 8-64k rows/batch — NOT JSON (380 ms→6.2 s at 5k→200k, 40 MB/s pipe, GIL GC), NOT temp Parquet (350-700 ms small-window, orphan files), NOT Flight/gRPC (handshake tax). Optional `ShmPipeTransport`/shared-mem ≥100k batches (7045 vs 3324 MB/s). p99: IPC 45 ms→420 ms vs JSON 380 ms→6.2 s.

**Quality path:** **All 6 checkers as DuckDB SQL aggregates on `arrow_scan` (zero-copy)**, `UNION ALL` single round-trip, 1-10 rows materialized — NO `to_pylist`. `to_pylist` is per-element Python object factory (20% GC, 25% GetScalar, 7% useful; 2.5-10× slower than pandas detour, 24× vs `ndarray`). DuckDB SQL stays in C++ vectorized engine, GIL once per vector (~2048 rows) not per row (discussion #4797). `pandas` detour loses `None→nan` coercion `[1,None,3]→[1.,nan,3.]`.

**Lifecycle:** Long-lived **daemon+forkserver pool (2-4 workers)**, supervised; `request_queue→Worker→response_queue` (soothe/py-drakkar); watermark `last_offset+1` after ledger+canonical; SHA-256 dedup idempotent; contiguous HWM. Per-window spawn REJECT: 2.0 s × 2,880 = 96 min/day overhead.

**Cold-start:** Warm daemon p50 0.58 s / p99 1.16 s (20-col) / 3.5-8.2 s (180-col) = 11-27% of 30 s interval PASS; per-window cold spawn 40-67% FAIL (pin Python 3.11; 3.12 has #30893 forkserver regression).

---

## 3. Acceptance Gates (P0 Closure — Bind Without Gap)

- **G-YAML-01 green:** Bad triad YAML with `order` + `Revenue ($)` compiles and `validate` EXECUTES generated SQL on ephemeral DuckDB catching quoting bugs (Suite A) + BigQuery `dryRun` parse — not just JSON Schema pass.
- **G-LEDGER-01 green:** `is_unchanged` Merkle BLAKE3 + stable key SHA-256 + ledger DDL with indexes + 0.18 GB/90d volume + advisory lock protocol + <2 min benchmark on 90-day synthetic.
- **G-SCHEMA-01 green:** CI gate rejects `rev→revenue_net` without MAJOR bump; covers m:n + FK≡PK drift; `dialect-matrix.yaml` checked in.
- **G-HYBRID-01 green:** IPC stream chosen; quality as DuckDB SQL (no `to_pylist`) OR measured `to_pylist`+serialization p99 at 30s cadence <50% of window interval.
- **G-MOAT-01 green (pending):** Either dirty-CRM F1 > regex+statistical with p99 <500 ms on 180-col abbreviation-heavy table, OR formally deferred to research track with regex+stat fallback as MVP (+ new MODEL-EVAL-PROD-SPEC.md covering eval harness, serving, versioning, fallback, cost — expanded per your ask on AI handling in actual systems).

---

## 4. Next Steps (Ordered — Gap Batch Order Preserved)

1. **Close remaining P0s:** G-MOAT-01 (8-search, production AI handling) + Batch C P1 (7 gaps) — both swarms in flight → fold into v3.
2. **Batch A gated spec artefacts (write before wedge code):** `MAPPING-YAML-SPEC.md` (JSON Schema + IR + dialect matrix + coherence), `REPLAY-LEDGER-SPEC.md` (hash + key + DDL + volume + concurrency), `SCHEMA-EVOLUTION-POLICY.md` (taxonomy + gate + FK≡PK + harness), `HYBRID-PLANE-SPEC.md` (IPC + subprocess + cold-start model) — each pastes the accepted gap spec.
3. **Verify acceptance gates in CI:** ephemeral harness + bad-triad test + FK orphan/junction test + 90-day bench + 30s-window p99 bench.
4. **Then interviews → bake-off → LLM factorial → DQ audit (P0 experiments).** P0 gate order unchanged.

---

*Generated 2026-08-30 20:50 UTC as v2 — applies 4 landed P0 specs. v3 will fold G-MOAT-01 (+ expanded AI prod-handling per founder) + Batch C (7 P1/P2) once both swarms land, plus MODEL-EVAL-PROD-SPEC.md as new P0 gate.*
