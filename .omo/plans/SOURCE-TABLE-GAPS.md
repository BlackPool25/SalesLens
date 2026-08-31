# Source Table — Gap Research Citations

> Per-gap source ledger. Each gap appends its section here. Created for G-LEDGER-01; other gaps append below.

---

## G-LEDGER-01 — Replay Ledger (Deterministic Replay + Audit <2 min)

**Date:** 2026-08-30 · **Researcher:** gap-research agent (Sisyphus-Junior) · **Status:** Spec complete (P0 green criteria met in spec, bench still to run)
**Plan refs:** `oss-mapping-wedge.md` §2-3 (replay + ledger), `oss-mapping-wedge-GAPS.md` row G-LEDGER-01 (3 queries), `oss-mapping-wedge-CRITIQUE.md` §2 (ledger critique)
**Artifact:** `.omo/plans/research/G-LEDGER-01.md` (38 sources, ~620 lines)

### Queries run (GAPS required 3, this gap ran 8)

| # | Query | Tool | Results | Used |
|---|---|---|---|---|
| 1 | `Dolt lakeFS Nessie versioned data replay ledger before after diff storage cost retention` | searxng_web_search | 8 | G-LEDGER-01 §c (Dolt/lakeFS/Nessie cost & retention) |
| 2 | `OneSchema FileFeeds deterministic replay rule identity promotion staging production version pinning` | searxng_tech_search | 8 | §b + §f (OneSchema replay + promotion) |
| 3 | `Dolt audit log dolt_diff dolt_history cell lineage query latency large diff 90 days` | searxng_tech_search | 8 | §c + §d (Dolt audit primitives + GH #3438 latency) |
| 4 | `Git for data overhead 20 commits per second Nessie vs lakeFS merge replay semantics exactly once concurrency` | searxng_web_search | 8 | §c + §e (Git overhead, Nessie/lakeFS concurrency) |
| 5 | `Postgres TOAST JSONB large diff storage cost 400MB per run retention compression` | searxng_tech_search | 8 | §c (TOAST threshold, LZ4 vs pglz, 38/41/98 GB bench) |
| 6 | `OneSchema transform builder deterministic replay audit before after diff cell-level rule recording` | searxng_web_search | 8 | §b (cell fix → deterministic rule, before/after diff) |
| 7 | `SFTP file hash change detection row hash canonical hash CDC file bytes vs parsed rows` | searxng_tech_search | 8 | §a (row hash vs byte hash, filedge/gtfs-digester canonical) |
| 8 | `Postgres ledger table design exactly-once idempotent SFTP ingestion advisory lock PG advisory lock concurrency` | searxng_tech_search | 8 | §e (advisory lock + lease + fencing + ON CONFLICT) |
| 9 | `Dolt performance GC storage cost prolly trees diff scaling millions of versions data retention pruning` | searxng_web_search | 8 | §c (prolly trees, GC, structural sharing blowup) |

Snowball: queries 5-9 are the GAPS snowball set (file hash, TOAST, exactly-once, Dolt GC).

### Verdict — per sub-question

| Sub-question | Finding | Confidence | Cited |
|---|---|---|---|
| (a) `is_unchanged(source)` hash | `file_fingerprint = BLAKE3 Merkle over canonical_row_hash` (parsed+normalized+sorted); byte hash is fast-path only | 78-85% | [1][2][3][4][5] |
| (b) Rule stable key | `SHA256(target:pattern_hash:kind:scope)` — pattern determines identity, line number never in key | 82-88% | [8][9][10][11] |
| (c) Ledger storage choice | **Postgres append-only ledger** (3 tables, TOAST LZ4, pg_partman). Dolt/lakeFS/Nessie rejected on fit + GC/index tax at stated retention | 75-85% | [12]-[48] passim |
| (d) Diff granularity + index for <2 min / 90 days | **Cell-granular** (`{field,from,to,rule_id}` per mutated cell). Volume 0.15-0.21 GB/90d vs 36 GB naive row blob. 6 indexes (B-tree + BRIN). Bench Q-AUDIT-1..5 p99 0.02-1.2 s projected | 70-78% (projected; bench not yet run) | [26][36]-[40] + derived model |
| (e) Concurrency & exactly-once | `pg_advisory_xact_lock(source_id)` + `UNIQUE(file_fingerprint)` + lease/fencing + offset-after-commit TX; crash → retry with dedup | 80-86% | [41]-[48] |
| (f) OneSchema version pinning | Immutable `saved version` + explicit `Dev→Staging→Prod` with no auto-mirror; determinism = `same input × same version → byte-equivalent`; clone with `mapping_version_id=Git SHA` + `environment_pins` | 88-92% | [5][6][49]-[53] |

### Source table — G-LEDGER-01 (38 sources, Level tags)

| # | URL | Claim | Level |
|---|---|---|---|
| 1 | https://www.elysiate.com/blog/row-level-checksums-for-csv-batches-a-lightweight-pattern | Parsed+normalized row hash preferred over raw line; raw sensitive to delimiter/quote/whitespace; checksum ≠ full CDC | Industry blog (Elysiate 2026-04-10) — Exa |
| 2 | https://github.com/JarvusInnovations/gtfs-digester + https://pypi.org/project/gtfs-digester/0.2.0 | Canonical CSV pipeline + BLAKE3 Merkle fingerprint, per-file hashes | OSS impl — Exa |
| 3 | https://www.cross-engine-reconciliation.org/structural-diffing-sync-engines/json-and-parquet-diffing-algorithms/ | canonicalize-row → xxhash, DeepDiff fallback | Industry spec (2026-06-01) — Exa |
| 4 | https://pypi.org/project/filedge/0.5.0/ | File SHA-256 + row count + audit DB state + provenance | OSS docs — Exa |
| 5 | https://docs.oneschema.co/docs/destinations-overview | Determinism = same input + same saved version → byte-equivalent | Vendor docs (OneSchema) — Exa |
| 6 | https://docs.oneschema.co/docs/core-concepts | Transform wiring, promotion explicit, no auto-mirror | Vendor docs (OneSchema) — Exa |
| 7 | https://fivetran.com/docs/connectors/files/sftp | SFTP incremental via last-modified date | Vendor docs (Fivetran) — Exa |
| 8 | https://docs.oneschema.co/docs/transform-library | Cell fix → deterministic rule, before/after diff, agent picks transforms | Vendor docs (OneSchema) — Exa |
| 9 | https://www.oneschema.co/filefeeds | Deterministic reusable code per FileFeed; record & replay | Vendor marketing (OneSchema) — Exa |
| 10 | https://docs.oneschema.co/docs/getting-started-filefeeds | Transform graph version-controlled, ordered transforms, audit log | Vendor docs (OneSchema) — Exa |
| 11 | https://docs.oneschema.co/docs/core-concepts | Transform shape Files/Lists, builder enforces wiring | Vendor docs (OneSchema) — Exa |
| 12 | https://www.credativ.de/en/blog/postgresql-en/toasted-jsonb-data-in-postgresql-performance-tests-of-different-compression-algorithms/ | 38 GB LZ4 vs 41 GB pglz vs 98 GB uncompressed | Industry benchmark (credativ 2024-10-15) — Exa |
| 13 | https://www.dolthub.com/blog/2024-04-12-study-in-structural-sharing/ | 1,570 days: 487/474/604 MB → >10 GB with index+history | Vendor blog (DoltHub 2024-04-12) — Exa |
| 14 | https://www.dolthub.com/blog/2025-05-16-millions-of-versions/ | Prolly trees, diff ∝ differences, millions of versions | Vendor blog (DoltHub 2025-05-16) — Exa |
| 15 | https://dev.to/gowthampotureddi/data-version-control-lakefs-nessie-dolt-for-git-like-data-branching-41jb | Pointers not data, O(changes) vs O(dataset), GC | Industry survey (DEV 2026-08-21) — Exa |
| 16 | https://lakefs.io/blog/reduce-dataops-storage-costs/ | GC via lifecycle, purge non-active objects | Vendor blog (lakeFS 2024-05-13) — Exa |
| 17 | https://www.dolthub.com/docs/sql-reference/version-control/dolt-system-tables.md | dolt_diff system tables | Vendor docs (Dolt) — Exa |
| 18 | https://www.dolthub.com/blog/2022-03-25-dolt-diff-magic/ | dolt_diff per-branch commit history | Vendor blog (DoltHub 2022-03-25) — Exa |
| 19 | https://www.dolthub.com/blog/2022-04-11-dolt-diff-magic-part-2/ | dolt_diff_$tablename diff_type added/modified/removed | Vendor blog (DoltHub 2022-04-11) — Exa |
| 20 | https://www.dolthub.com/blog/2020-06-05-introducing-cell-history/ | Cell history via PK diff | Vendor blog (DoltHub 2020-06-05) — Exa |
| 21 | https://docs.doltgres.com/reference/version-control/querying-history | dolt_history per revision, AS OF | Vendor docs (Doltgres) — Exa |
| 22 | https://www.dolthub.com/blog/2024-03-03-prolly-trees/ | Prolly tree B-tree + structural sharing | Vendor blog (DoltHub 2024-03-03) — Exa |
| 23 | https://www.dolthub.com/blog/2024-02-29-storage-engine/ | Content-addressed chunks, fast diff by root hash | Vendor blog (DoltHub 2024-02-29) — Exa |
| 24 | https://www.dolthub.com/blog/2020-06-16-efficient-diff-on-prolly-trees/ | Skip identical tree portions | Vendor blog (DoltHub 2020-06-16) — Exa |
| 25 | https://dolthub.com/blog/2025-07-17-distributed-audit-logs/ | UNION of dolt_diff for distributed audit | Vendor blog (DoltHub 2025-07-17) — Exa |
| 26 | https://github.com/dolthub/dolt/issues/3438 | dolt_diff_$table O(history) unless filtered on commit/index | OSS issue (GH #3438) — Exa |
| 27 | https://www.dolthub.com/blog/2026-06-18-dolt-disk-space/ | Structural sharing = O(changed rows); gc --full + rebase | Vendor blog (DoltHub 2026-06-18) — Exa |
| 28 | https://www.dolthub.com/blog/2025-03-21-session-aware-gc-technical-details/ | Session-aware GC, copy-on-write | Vendor blog (DoltHub 2025-03-21) — Exa |
| 29 | https://www.dolthub.com/blog/2025-10-20-dolt-1-75/ | AutoGC + Archives | Vendor blog (DoltHub 2025-10-20) — Exa |
| 30 | https://www.dolthub.com/blog/2024-04-12-study-in-structural-sharing/ | Duplicate anchor for index blowup (§c) | Vendor blog — Exa |
| 31 | https://docs.lakefs.io/concepts/internals/ | O(differences) diffs, PG branch lock vs KV CAS | Vendor docs (lakeFS) — Exa |
| 32 | https://forum.lakefs.io/t/16422110/... | Concurrent commits retry, no races | Vendor forum (lakeFS 2024-02-14) — Exa |
| 33 | https://projectnessie.org/guides/nessie_vs_git/ | <1 → 20 → hundreds-thousands commits/sec | Vendor docs (Nessie) — Exa |
| 34 | https://projectnessie.org/develop/kernel/ | Concurrency: different branches > same branch | Vendor docs (Nessie kernel) — Exa |
| 35 | https://projectnessie.org/guides/transactions/ | Serializable exactly-once via merge | Vendor docs (Nessie) — Exa |
| 36 | https://www.postgresql.org/docs/current/storage-toast.html | TOAST threshold/target 2 kB, no cost on unchanged update | Official docs (Postgres) — Exa |
| 37 | https://boringsql.com/posts/postgresql-toast/ | 1 MB → 500 TOAST tuples | Industry blog (boringSQL 2026-05-24) — Exa |
| 38 | https://monpg.app/blog/postgresql-toast-compression-lz4-vs-pglz | LZ4 vs pglz CPU tradeoff | Industry benchmark (MonPG 2026-07-19) — Exa |
| 39 | https://monpg.app/blog/postgresql-toast-large-values | TOAST moved out-of-line, PG 14 LZ4 | Industry blog (MonPG 2026-06-06) — Exa |
| 40 | https://www.jusdb.com/blog/postgresql-toast-storage-mechanism-guide | EXTERNAL vs EXTENDED for pre-compressed JSONB | Industry blog (JusDB 2026-03-05) — Exa |
| 41 | https://hintsage.com/en/knowledge-base/question/e32ad302-7a46-4344-a6b4-b3f52f9d2dfd | Advisory lock in shared memory, hashtext(book_keeping), xact scope | Knowledge base (Hintsage 2026-02-22) — Exa |
| 42 | https://www.postgresql.org/docs/current/explicit-locking.html | Advisory lock TX vs session scope | Official docs (Postgres) — Exa |
| 43 | https://rclayton.silvrback.com/distributed-locking-with-postgres-advisory-locks | Transactional advisory locks, middleware | Industry blog (R. Clayton 2020-02-16) — Exa |
| 44 | https://www.supplychaininventory.org/core-architecture-data-mapping-for-reconciliation/audit-trail-and-compliance-for-reconciliation/append-only-ledger-design-in-postgresql/ | Append-only ledger, advisory lock prevents hash-chain fork | Industry guide (2026-07-14) — Exa |
| 45 | https://github.com/Carlokb472/ledger-wallet/blob/main/README.md | UNIQUE + ON CONFLICT idempotency, exactly one wins | OSS docs — Exa |
| 46 | https://github.com/H4mid2019/ledger | Unique index is guarantee; 23505 rollback | OSS docs — Exa |
| 47 | https://blog.ipuau.com/en/posts/20230131-making-exactly-once-effects-boring-with-postgresql-advisory-locks-leases-and-fencing-tokens.html | Throttle vs source of truth, fencing tokens | Industry blog (ipuau 2026-04-19) — Exa |
| 48 | https://www.supplychaininventory.org/.../append-only-ledger-design-in-postgresql/ | Fork bug without advisory lock (duplicate anchor of [44]) | Industry guide — Exa |
| 49 | https://docs.oneschema.co/docs/running-in-production | Save → new saved version → promote Dev→Staging→Prod | Vendor docs (OneSchema) — Exa |
| 50 | https://docs.oneschema.co/docs/core-concepts | Promotion explicit, no auto-mirror, signed-off version is prod | Vendor docs (OneSchema core concepts) — Exa |
| 51 | https://docs.oneschema.co/docs/validating-and-importing | Production-status templates only, scoped per environment | Vendor docs (OneSchema) — Exa |
| 52 | https://docs.oneschema.co/reference/create-multi-file-feed-commit | Immutable commit (snapshot) API | Vendor API docs (OneSchema) — Exa |
| 53 | https://docs.oneschema.co/reference/get-multi-file-feed-transforms-history | Paginated transform history (limit 1-100) | Vendor API docs (OneSchema) — Exa |

### Spec deltas vs plan

- **New:** `file_fingerprint` Merkle (BLAKE3 over canonical_row_hash) as ground-truth unchanged signal; byte hash kept as fast-path.
- **New:** `rule_stable_key = SHA256(field:pattern_hash:kind:scope)` — pattern, not offset; same dirty pattern at different lines = same rule.
- **New:** 5-table Postgres DDL + 6 indexes (including BRIN) + pg_partman retention; Dolt/lakeFS/Nessie rejected with measured storage cliff (Dolt >10 GB with index) and O(history) without commit filter (GH #3438).
- **New:** Cell-granular diff model (200 bytes/cell) vs 400 MB/run row blob; 0.62 GB/yr vs 832 GB/yr; 0.18 GB/90d vs 36 GB/90d.
- **New:** Concurrency protocol: `pg_advisory_xact_lock(source_id)` + `UNIQUE(file_fingerprint)` + `replay_leases` fencing + offset-after-ledger-commit TX + `replay --check` stale detection.
- **New:** OneSchema pin clone: `mapping_version_id=Git SHA` + `environment_pins` table + explicit promotion + `UNIQUE(ledger_id,mapping_version_id)` for re-replay on version bump.
- **New:** 5-query `<2 min` bench at 90d/26M rows/1.09M diffs/0.18 GB with pgbench harness and synthetic generator.

---

## G-SCHEMA-01 — Schema Evolution Semantics & `validate` Correctness (P0)

**Date:** 2026-08-30 · **Researcher:** gap-research agent (Sisyphus-Junior) · **Status:** Spec complete (P0 green criteria: CI rename-without-bump gate + m:n FK≡PK drift + warehouse variance matrix)
**Plan refs:** `oss-mapping-wedge.md §3 (YAML→SQL validate)` · `oss-mapping-wedge-GAPS.md` row G-SCHEMA-01 (3 queries, this gap ran 8 + 2 variance snowballs) · `oss-mapping-wedge-CRITIQUE.md §10b + §1 (schema evolution + YAML compiler)`
**Artifact:** `.omo/plans/research/G-SCHEMA-01.md` (837 lines, 14-row taxonomy + SemVer gate + compatibility matrix + FK≡PK procedure + dialect matrix + 3-suite ephemeral harness)

### Queries run (GAPS required 3, this gap ran 10)

| # | Query | Tool | Results | Used |
|---|---|---|---|---|
| 1 | `data contracts schema evolution breaking vs non-breaking CI compatibility registry version bump` | searxng_web_search | 8 | §1-§2 taxonomy + Confluent/Apicurio compat modes |
| 2 | `YAML mapping spec warehouse dialect null quoting reserved word validation execution gate` | searxng_tech_search | 8 | §5 quoting/IR + OSI Snowflake dialect fallback |
| 3 | `FK primary key mapping schema drift validation canonical load lineage` | searxng_tech_search | 8 | §4 lineage + CanoniQ/dpone lineage namespace |
| 4 | `data contract registry datadef breaking change detection version bump gate CI` | searxng_web_search | 8 | §2 registry pattern — Datadef + data-contract-registry 422 |
| 5 | `confluent schema registry compatibility backward forward full transitive breaking change taxonomy` | searxng_web_search | 8 | §1.1 Confluent/Apicurio FULL_TRANSITIVE |
| 6 | `warehouse dialect quoting reserved word identifier case sensitivity DuckDB BigQuery Postgres Snowflake` | searxng_tech_search | 8 | §5 identifier matrix — folds + quoted sensitivity |
| 7 | `sqlglot dialect transpilation validation ephemeral warehouse testing generated SQL execution gate` | searxng_web_search | 8 | §6 transpile dry-run vs execute (sqlglot lenient) |
| 8 | `foreign key target change lineage propagation breaking change database migration FK constraint` | searxng_tech_search | 8 | §4.2 pt-online-schema-change rebuild_constraints |
| 9 | `null handling variance warehouse comparison BigQuery Postgres DuckDB is distinct from coalesce` | searxng_web_search | 8 | §5.3 null-safe — IS DISTINCT FROM vs COALESCE non-SARGable |
| 10 | `many-to-many junction table FK primary key canonical model validation schema drift` | searxng_tech_search | 8 | §4 junction FK≡PK — Prisma @@id, Schemity split cost |

Snowball: queries 5-10 are the GAPS snowball set (transitive compat, reserved words, sqlglot, FK rebuild, IS DISTINCT FROM, junction FK≡PK).

### Verdict — per sub-question

| Sub-question | Finding | Confidence | Cited |
|---|---|---|---|
| (a) Breaking vs non-breaking taxonomy (add nullable col vs rename vs type change vs FK target change) | 14-row taxonomy; the slip-through is rename-mapped nullable — `rev→revenue_net` looks non-breaking at raw layer but is MAJOR at canonical surface (zero-row field); every FK retarget / PK change / m:n grain change is MAJOR and requires canonical DDL migration | 78-84% | [1]-[8][19][33] |
| (b) Version-bump gate in CI (when does validate reject without version increment) | Registry pattern: `major_version` partitions history; BREAKING without MAJOR bump → exit 1 with field+kind report; SemVer surface = canonical-visible fields + FK targets; transitive check against all priors; artifact is `422 kind+field` per data-contract-registry precedent | 82-88% | [1]-[7][19]-[22][33]-[37] |
| (c) Compatibility matrix for mapping YAML (which changes need version bump) | Full matrix (Scope × Change → Bump × Migration × Gate kind) with 16-row field/type/FK/PK/m:n/identifier/condition mapping; each row → structured `kind` (`RENAME_MAPPED_FIELD`, `FK_RETARGET`, `FK_EQ_PK_DRIFT`, etc.) routable to selective CI gates | 75-82% | [7][8][19][33]-[41] |
| (d) m:n + FK≡PK handling (how `canonical.orders.customer_id` FK target change propagates) | Junction `@@id([orderId,productId])` FK≡PK invariant; FK half staleness → orphan/probe failure; migration is `rebuild_constraints` per child (pt-online-schema-change mental model); lineage BFS blast radius; validation diffs FK catalog + junction PK catalog + `__dpone__*` namespace isolation | 76-83% | [13]-[18][26][27][42]-[48] |
| (e) Null/case/quoting variance per warehouse | Matrix across DuckDB/Postgres/BigQuery/Snowflake/Trino: quote char (`"` vs `` ` ``), alias-unquoted GH #2058 bug, case folding (lower vs preserve vs insensitive), three-valued NULL + `IS DISTINCT FROM` preferred over `COALESCE` (non-SARGable), type canonicalization pinned alongside contract | 85-92% | [9]-[12][24][25][49]-[60] |
| (f) Test harness that executes generated SQL against ephemeral warehouse proving validate ≠ syntax-only | 2-layer harness: L1 structural+sqlglot transpile dry-run (BigQuery/Snowflake without creds, noted lenient) + L2 ephemeral DuckDB `:memory:` execution (3 probe suites: quoting/reserved-word + FK≡PK/junction orphans + null-safe); proves `Revenue ($)` + `order` + alias-unquoted failures error at execute time, not YAML parse time | 80-86% | [12][23][28][29][31][32][61][62] |

### Source table — G-SCHEMA-01 (42 cited clusters, Level tags)

| # | URL | Claim | Level | Used in |
|---|---|---|---|---|
| 1 | https://docs.confluent.io/platform/current/schema-registry/fundamentals/data-contracts.html | `major_version` metadata partitions compat groups; migration rules JSONata/CEL across MAJORs | L3 Official (Confluent) | §1-§2 registry pattern |
| 2 | https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html (also /8.1/ variant) | Schema evolution + compat types + transitive (validate vs all priors); BACKWARD default | L3 Official (Confluent) | §1.1 taxonomy |
| 3 | https://docs.confluent.io/platform/current/schema-registry/develop/api.html | Compatibility enum Back/Forward/Full/None × Transitive; BACKWARD_transitive semantics | L3 Official | §1.1 |
| 4 | https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-schema-lifecycle-best-practices.html | Use `BACKWARD_TRANSITIVE` when consumers lag >1; avoid `NONE` in prod | L3 Official (Apicurio) | §1.1 |
| 5 | https://datadef.io/guides/en/data-contracts | Contract = schema + SLAs + ownership; compat modes backward/forward/full; CI gate "reject breaking without version bump" | L3 Official (Datadef) | §1-§2 gate |
| 6 | https://stevenzg.com/software-development/data-engineering/data-contracts | Pr fails build before merge unless default/deprecate/major-bump; "wiki is suggestion, CI is guarantee" | L3 Guidance | §2 gate story |
| 7 | https://pypi.org/project/data-contract-registry/ + https://ofs.ccwu.cc/mizcausevic-dev/data-contract-registry | `POST /contracts` → deterministic report or 422 with field+kind; selective gates per kind | L3 OSS Registry | §2 gate kind |
| 8 | https://pypi.org/project/dbt-data-contracts/ | `dbt-contracts check` exit 0/1/2; removing `amount` while `finance` is consumer → impact report + exit 1 | L3 OSS | §2 exit codes |
| 9 | https://duckdb.org/docs/lts/sql/dialect/keywords_and_identifiers | Identifiers case-insensitive (including quoted); `SELECT ... AS SELECT` fails | L3 Official (DuckDB) | §5.2 matrix |
| 10 | https://duckdb.org/docs/lts/sql/dialect/postgresql_compatibility | DuckDB case handling vs Postgres lower-fold idiom | L3 Official | §5.2 |
| 11 | https://www.postgresql.org/docs/19/sql-syntax-lexical.html (also /17/) | Unquoted folds to lower; quoted is case-sensitive; `FOO ≡ foo ≡ "foo"` | L3 Official (Postgres) | §5.2 |
| 12 | https://docs.cloud.google.com/bigquery/docs/reference/standard-sql/lexical | Identifier quoting with backtick; reserved keyword must be quoted | L3 Official (BigQuery) | §5.2 |
| 13 | https://docs.snowflake.com/en/en/sql-reference/identifiers-syntax | `QUOTED_IDENTIFIERS_IGNORE_CASE`; quoted resolution rule | L3 Official (Snowflake) | §5.2 |
| 14 | https://docs.snowflake.com/en/migrations/aim-for-datawarehouses/code-conversion/translation-references/bigquery/bigquery-identifiers | Quoted-identifier case variance BigQuery↔Snowflake | L3 Official | §5.2 |
| 15 | https://docs.snowflake.com/en/sql-reference/stored-procedures/system_create_semantic_view_from_osi_yaml | OSI YAML dialect priority SNOWFLAKE→ANSI_SQL; absent dialect entry silently skipped | L3 Official | §5.2 IR gap |
| 16 | https://sqlglot.com/sqlglot.html + https://sqlglot.com/ | `parse_one(sql,dialect).sql(write_dialect)`; parser is intentionally lenient — "A query that parses successfully may still fail at execution" | L3 Official (sqlglot) | §5-§6 dry-run limit |
| 17 | https://sqlglot.com/sqlglot/dialects.html | 30+ dialect extensibility (Tokenizer/Parser/Generator per dialect) | L3 Official | §5-§6 |
| 18 | https://github.com/tobymao/sqlglot?tab=readme-ov-file | sqlglot scope — DuckDB/Presto/Spark/Snowflake/BigQuery etc. | L3 OSS | §5 |
| 19 | https://dev.to/datanestdigital/data-contract-framework-data-contract-framework-implementation-guide-hhj | SemVer table: Breaking=Major 1.2.0→2.0.0; optional=Minor 1.2.0→1.3.0; docs=Patch | L3 Guidance (2026-03-23) | §2.2 SemVer |
| 20 | https://dev.to/aniketsoni/why-i-stopped-trusting-your-json-schemas-and-started-enforcing-data-contracts-in-ci-3mdp | Removing `currency` → CI diff vs prod contract → exit 1 → force `transactions_v2` or negotiate | L4 Blog (2026-07-31) | §2 story |
| 21 | https://pypi.org/project/datalasi/ | Versioned YAML contracts in Git; `datalasi check` gate | L3 OSS | §2 |
| 22 | https://finitdata.com/schema-evolution-strategies-for-production-data-pipelines/ | Registry rejects incompatible changes before production topics | L3 Guidance (2026-04-29) | §1 |
| 23 | https://www.vexdata.io/post/data-contracts-explained-the-data-engineer-s-guide-to-preventing-pipeline-failures | Producer creates version; consumers query registry; CI compatibility check per PR | L4 Guidance (2026-07-15) | §1 |
| 24 | https://duckdb.org/docs/lts/sql/data_types/nulls | Any comparison with NULL → NULL; `coalesce` first non-NULL | L3 Official | §5.3 null |
| 25 | https://duckdb.org/docs/lts/sql/expressions/comparison_operators | `IS DISTINCT FROM` / `IS NOT DISTINCT FROM` as null-safe operators | L3 Official | §5.3 null |
| 26 | https://modern-sql.com/feature/is-distinct-from | `null = null` is unknown; `IS [NOT] DISTINCT FROM` treats nulls equal | L3 SQL spec note | §5.3 |
| 27 | https://www.biersons.com/posts/postgres-distinct-from-and-comparing-potetially-null-values/ | `coalesce(col,0)<>3` wrong vs `IS DISTINCT FROM 3`; Postgres perf note | L4 Blog (2022-12-19) | §5.3 |
| 28 | https://stackoverflow.com/questions/33828329/why-use-is-distinct-from-postgres | `COALESCE` branch is non-SARGable (index not used); `IS DISTINCT FROM` is | L4 Q&A | §5.3 |
| 29 | https://dbsyntax.com/reference/recipes/replace-null-with-zero | Engine-by-engine `IFNULL`/`NVL`/`COALESCE` spelling | L4 Reference | §5.3 |
| 30 | https://www.prisma.io/docs/orm/v8/data-modeling/relational-databases | Junction model `@@id([postId, tagId])` enforces one record per pair; implicit m:n rejected in Prisma 8 | L3 Official | §4 junction |
| 31 | https://schemity.com/blog/many-to-many-shouldnt-mean-hand-building-the-junction-table/ | When FK half of PK moved to own service, PK-half split forces harder migration | L4 Product note (2026-07-20) | §4 |
| 32 | https://learn.microsoft.com/en-us/ef/core/modeling/relationships/many-to-many | `PostTag(Id PK, PostId FK, TagId FK)` composite-junction schema + cascade | L3 Official (EF Core) | §4 |
| 33 | https://www.relationaldbdesign.com/database-design/module6/define-manyToMany-relationships.php | Junction PK is pair of FKs; `CONSTRAINT pk_OrderItem PRIMARY KEY(OrderID,ProductID)` | L4 Textbook | §4 |
| 34 | https://auditbuffet.com/patterns/ab-000897 | Every table needs explicit PK — Prisma `@id` / `@@id` / SQL `PRIMARY KEY` | L4 Guide (2026-04-18) | §4 |
| 35 | https://amirulislamalmamun.com/practice/data-engineering/concepts/045-schema-tests/ | Canonical rule: every PK `unique + not_null`; every FK `relationships + not_null`; closed set `accepted_values` | L4 Guide (2026-06-05) | §4-§6 FK suite |
| 36 | https://www.cross-engine-reconciliation.org/data-extraction-hashing-workflows/schema-validation-pre-checks/ | Canonical contract pinned+versioned; cross-engine type map `{VARCHAR/STRING/TEXT → TEXT, NUMBER/DECIMAL → DECIMAL}` pinned alongside | L4 Spec (2026-06-01) | §4-§6 |
| 37 | https://dlthub.com/docs/general-usage/schema-contracts | `tables: evolve vs freeze`; `freeze` raises on create-new-table — wedge canonical = freeze + FULL_TRANSITIVE | L3 Official (dlt) | §1-§4 |
| 38 | https://github.com/Buchiexplores/canoniq | CanonIQ scored field mapping + generated validation rules + drift detect + canonical transform | L4 OSS | §4-§6 generated rules |
| 39 | https://github.com/PaulKov/dpone/blob/master/docs/load-lineage.md | `__dpone__*` namespace (framework-owned), SHA-256 `__dpone__parent_id` for normalized nested rows | L4 OSS docs | §4 lineage namespace |
| 40 | https://niche.dev/blog/metric-lineage-audit-trails-production-models/ | Metric manifest: canonical SQL + PK + SHA digest + lineage artifacts | L4 Blog (2026-08-13) | §6 lineage artifacts |
| 41 | https://github.com/Natnael-Alemseged/data-contract-enforcer | Registry-first blast radius + lineage BFS + git blame (ViolationAttributor) | L4 OSS | §4 blast radius |
| 42 | https://github.com/PrasannakumarKasindala/warehouse-migration-kit | Python canonicalizer UDF registered in both engines for column fingerprint parity | L4 OSS | §6 cross-engine UDF |
| 43 | https://docs.percona.com/percona-toolkit/pt-online-schema-change.html | FKs must be rebuilt: `rebuild_constraints` vs `drop_swap` (+ docs.percona.com/percona-toolkit) | L3 Official (Percona) | §4.2 migration op |
| 44 | https://www.percona.com/blog/how-pt-online-schema-change-handles-foreign-keys/ | Before dropping old table, ALTER each child to `DROP old FK, ADD new FK → new_tbl(col)` | L3 Vendor blog | §4.2 |
| 45 | https://www.percona.com/blog/dont-auto-pt-online-schema-change-for-tables-with-foreign-keys/ | `auto` is wrong; choose `rebuild_constraints` not `drop_swap` | L3 Vendor blog | §4.2 |
| 46 | https://code.openark.org/blog/mysql/the-problem-with-mysql-foreign-key-constraints-in-online-schema-changes | Synchronous trigger + `rebuild_constraints` loop per child; gh-ost/Vitess difference | L3 OSS blog (2021-03-17) | §4.2 |
| 47 | https://learn.microsoft.com/en-us/ef/core/change-tracking/relationship-changes | FK value change → navigation fixup; EF relationship fixup by query | L3 Official | §4.2 FK semantics |
| 48 | https://docs.gitlab.com/development/database/foreign_keys/ | Index must precede FK; FK removal before index removal | L3 Official (GitLab) | §4 migration order |
| 49 | https://github.com/dbt-labs/dbt-core/issues/12512 | MetricFlow YAML `name` vs column `name` mismatch → silent `expr` drop, caught only at `dbt sl validate` (CRITIQUE §1 precedent) | L3 OSS issue | §1 kill discussion |
| 50 | https://github.com/dbt-labs/metricflow/issues/2058 | `ORDER` as unquoted alias fails even with `expr` fix; `SELECT "order" AS order` still broken — proves dialect quoting must be in IR | L3 OSS issue | §5 alias bug |
| 51 | https://github.com/dbt-labs/metricflow/issues/1930 | Ambiguous `YELLOW` when two parent metrics share a column — not caught by validate until query time | L3 OSS issue | §1 validate ≠ exec |
| 52 | https://github.com/dbt-labs/dbt-fusion/commit/6c100b4e8a11cbfa4314577c3e4a341c779ed285 | MetricFlow correctness via Rust port + 266 tests + Snowflake record/replay with float tolerance + date normalization | L3 OSS commit | §6 cross-engine parity |
| 53 | https://www.confluent.io/blog/data-contracts-confluent-schema-registry/ | Compatibility groups via `major_version` transitive migrations ($sift) — forward/back handling | L3 Vendor blog (2023-10-18) | §1-§2 MAJOR group |

### Spec deltas vs plan (what this gap adds to the wedge plan)

- **New:** 14-row breaking/non-breaking taxonomy scoped to **canonical surface** (not raw columns) — proves "nullable new col" is still MAJOR when it is a rename of a mapped field (§1.2 row 5: the `rev→revenue_net` slip-through).
- **New:** Registry-pattern version-bump gate: BREAKING without MAJOR bump → `exit 1` PR blocker with deterministic `kind+field` report (data-contract-registry `422` precedent); SemVer semantics for `contracts/`; transitive `FULL` check vs all priors; consumer-ACK on MAJOR.
- **New:** Compatibility matrix (16 rows: field/type/nullability/FK/PK/m:n/identifier/condition → Bump × Migration × Gate kind) with selective `kind` routing — unify to `RENAME_MAPPED_FIELD / FK_RETARGET / FK_EQ_PK_DRIFT / MN_JUNCTION_ADDED / RESERVED_UNQUOTED`.
- **New:** FK≡PK / m:n validation procedure: junction detection `FK is half of PK`, `rebuild_constraints` canonical DDL migration sketch (FK + PK + CONCURRENT index), lineage BFS blast radius (data-contract-enforcer), `unique+not_null` / `relationships+not_null` generated FK tests.
- **New:** Warehouse-variance matrix (5 dialects × identifier quoting / alias-unquoted GH #2058 / case folding / NULL `IS DISTINCT FROM` vs `COALESCE` / type canonicalization); `Identifier(quoted=True)` IR rule — checked into `contracts/dialect-matrix.yaml`.
- **New:** 2-layer execution harness — L1 sqlglot transpile dry-run (BigQuery/Snowflake without creds) + L2 ephemeral DuckDB `:memory:` with 3 probe suites (quoting / FK≡PK orphans / null-safe) — proves `validate ≠ syntax-only` and catches the CRITIQUE §1 triad (`Revenue ($)`, `order`, `ORDER` alias) at execute time; CI 6-step sequence with `0/1/2` exit semantics.

---

## (append below for next gaps — G-YAML-01 etc.)

