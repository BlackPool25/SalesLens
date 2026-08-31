# saleslens-wave1-foundations - Work Plan

## TL;DR (For humans)

**What you'll get:** P0 foundations that gate everything — versioned `mapping.schema.json` + 5-dialect matrix with always-quote, frozen MappingIR via sqlglot, 3-tier `mapping validate` (JSON Schema → information_schema → ephemeral DuckDB + BigQuery dryRun) with coherence, Merkle BLAKE3 ledger (0.18GB/90d, 6 indexes, advisory-lock), Confluent taxonomy + FK=PK harness, and Arrow IPC stream + daemon (NO to_pylist, 3.11 pin). No wedge code ships until these gates pass.

**Why this approach:** Critique proved 7 of 20 todos were hand-waving: Ajv Draft7 vs 2020-12, sqlglot[c] 3.8× slowdown, dryRun SA leak on forks, BRIN stale, per-window spawn 96min/day. This wave closes those with SearxNG-backed patches before any P1 wrapping.

**What it will NOT do:** No Soda/Splink/Dagster/Elementary/BPM/tenancy, no learned/TURL, no interviews — those are waves 2-4.

**Effort:** Large (6 todos, 2-3 weeks solo, serial 1→2→3)
**Risk:** Medium → Low after gates (85-90% shippable)
**Decisions to sanity-check:** (1) pure `sqlglot` without `[c]` (2) per-source UNIQUE vs global (3) `BRIN pages_per_range=128` + `autovacuum_analyze_scale_factor=0.02`

Your next move: approve this wave, then run `$start-work` on this wave's plan — each wave is independently implementable.

---

> TL;DR (machine): Wave foundations — 1-6 — 6 todos — gates-before-code.

> **PIVOT 2026-08-31 FileFeeds:** This wave now ships as part of **B2B SaaS FileFeeds for Revenue** (ICP: GTM ops, customer CSV via SFTP/S3/email → `contacts/companies/opportunities` template). Pricing `$50k+/yr` [wetransform.com] vs self-hostable `$0-500/seat`; build cost `$100k+75k/yr 2×` [oneschema.co]. See parent `saleslens-final-build-plan.md` pivot header.

## Scope
### Must have
- `schema/mapping.schema-1.0.json` Draft 2020-12 (`spec_version:"0.1"`, `source_column` verbatim, `canonical_entity/field` enum from `SemanticMapperService.REGISTRY`, `confidence` 0-1, `verification_state`, `deterministic_condition:{language:"sql_predicate",expr,dialect_authored}`, `transform:{kind:7}`, `audit:{rule_id}`) + `contracts/dialect-matrix.yaml` 5 dialects (quote char, case folding, GH #2058 alias, regex/date/null `IS DISTINCT FROM`) + shared `quote(col,dialect)` via `sqlglot` `identify=True`
- `src/mapping/ir.py` frozen `Warehouse(5)`, `TransformKind(7)`, `MappingRuleIR`, `MappingFileIR(frozen hash)` + `src/mapping/dialect.py` `DialectEmitter` delegating to `sqlglot` `IDENTIFIER_START/END, DATE_PART_MAPPING, TRANSFORMS` (pure `sqlglot`, no `[c]`), optimizer `dedup,pushdown`, `assert_values_exhausted`, `v1_0_to_v1_1.py`, snapshots `fixtures/snapshots/<rule_id>/<warehouse>.sql`
- CLI `mapping validate --syntax --execute --dialect --coherence` + `mapping test/diff/migrate` T1 JSON Schema offline (`Ajv2020`/`Draft202012Validator` + `unevaluatedProperties`), T2 `information_schema` binding, T3 ephemeral `duckdb.connect(":memory:")` `EXPLAIN` (<2s/200 rules) + BigQuery `Client(dry_run=True,use_query_cache=False)` WIF OIDC fork branch `parse-only`, `mapping_manifest.json` pins `generator_version`, PR `mapping diff` canonicalized + `generator_version`, `validate --coherence` cross-EXPLAIN
- `V19__replay_ledger.sql` 3 tables (`replay_runs`, `replayed_files` `UNIQUE(source_id,file_fingerprint)`, `replay_cell_diffs` JSONB 200 bytes/cell) + 6 indexes (B-tree + BRIN 128 + GIN) + `replay_leases` fencing + `environment_pins` + `UNIQUE(source_id,file_fingerprint)` per-source, `is_unchanged = BLAKE3(sorted canonical_row_hash)` (byte SHA256 fast-path only), `rule_stable_key = SHA256(field|pattern_hash|kind|scope)`, `pg_advisory_xact_lock` xact + `SELECT FOR UPDATE` lease + `ON CONFLICT`, `last_offset+1` HWM, pgbench `90d 26M 1.09M 0.18GB` Q-AUDIT-1..5 p99 0.02-1.2s, cost $0.86/yr
- `contracts/schema-evolution-policy.md` 14-row taxonomy `BACKWARD/FULL_TRANSITIVE`, `rev->revenue_net` MAJOR, FK retarget/PK/m:n MAJOR `rebuild_constraints`, `major_version` partitions + `422 kind+field` + transitive FULL + consumer-ACK, 16-row Scope×Change matrix, `fk_is_half_of_pk` + `@@id([orderId,productId])`, `contracts/dialect-matrix.yaml` + harness L1 sqlglot + L2 DuckDB Suites A/B/C `IS DISTINCT FROM`
- `src/mapping/hybrid/` Arrow IPC **stream** `pa.ipc.new_stream/RecordBatchStreamReader` 8-64k rows/batch (NOT JSON/Parquet/Flight) optional `ShmPipeTransport` ≥100k, p99 50k×20col 80-220ms, quality 6 checkers as DuckDB `arrow_scan` `UNION ALL` NO `to_pylist`, daemon forkserver pool 2-4 `request_queue→Worker` + 3.11 pin (#30893), `last_offset+1` after ledger+canonical

### Must NOT have (guardrails, anti-slop, scope boundaries)
- No Soda/Splink/Dagster/Elementary/BPM/tenancy/consistency (wave 2), no learned/LLM/prod AI (wave 3), no interviews/bake-off/time-to-canonical/DQ (wave 4)
- No `sqlglot[c]` when custom Dialect, no per-window spawn, no JSON-over-stdin, no `to_pylist`, no global `UNIQUE(file_fingerprint)`, no `coherence` optional, no Git ledger

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- JUnit5+Mockito+MockMvc+H2+ephemeral DuckDB+pgbench+Dagster `dg` — harness before wedge is TDD, wedge tests-after
- `ajv --spec=draft2020` + `Draft202012Validator` on `shopify_orders__reserved.yaml` (order+`Revenue ($)`+`__EMPTY_3`), `quote("order","postgres")='"order"'` + `quote("Revenue ($)","bigquery")='`+"`Revenue ($)`"+'`'
- `mypy --strict src/mapping` 0 errors (pure `sqlglot`), `pytest -k test_reserved_quoted` proves quoted per dialect + `test_two_compiler_coherence`
- `make validate-mapping` T3 `EXPLAIN` <2s/200 rules + BigQuery `dryRun:true` WIF OIDC fork branch, `EXPLAIN ANALYZE` sampled 10% p50
- `psql \d` 3 tables+6 indexes+`UNIQUE(source_id,fp)`+BRIN 128+`autovacuum_analyze_scale_factor=0.02`, `BLAKE3(sorted canonical_row_hash)` zero re-verify on reordered+timestamp, `pgbench -c 10 -T 60` p99 0.02-1.2s, concurrent `pg_advisory_xact_lock`+`FOR UPDATE` one wins
- `pytest tests/hybrid/test_ipc_p99.py` IPC 80-220ms vs JSON 1.1-2.4s, warm daemon p50 0.58s/p99 1.16s (20-col)/3.5-8.2s (180-col) PASS, `to_pylist`-free `UNION ALL` 1-10 rows, `Python 3.11` pinned
- Evidence: `.omo/evidence/task-<N>-saleslens-wave1-foundations.<ext>` per todo (outside ulw-loop `.omo/evidence/`, inside `omo ulw-loop status --json`)

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. This wave is itself one parallel wave; sub-parallelism per Dependencies below.

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| 1 | - | 2,3,5,9,14 | 4,6 |
| 2 | 1 | 3,9,14 | 4,5,6 |
| 3 | 1,2 | 9,17 | 4,5,6 |
| 4 | - | 7,8,9,10,17 | 1,2,3,6 |
| 5 | 1 | 7,10,17 | 2,3,4,6 |
| 6 | - | 16,18 | 1,2,4 |

## Todos

- [ ] 1. Contracts + dialect matrix — author `mapping.schema.json` + `contracts/dialect-matrix.yaml`
  What to do / Must NOT do: Create `schema/mapping.schema-1.0.json` Draft 2020-12 with `spec_version:"0.1"` required, `source_column` verbatim, `canonical_entity/field` enum from `SemanticMapperService.REGISTRY` CopyOnWriteArrayList, `confidence` 0-1, `verification_state` pending|confirmed|ignored, `deterministic_condition:{language:"sql_predicate", expr, dialect_authored}` structured not raw, `transform:{kind:identity|regex_extract|regex_replace|cast|coalesce|sql_expr|lookup, params, on_error}`, `type` from registry, `audit:{author,commit,rule_id}` pattern `^[a-z0-9_-]+$`; add `expr` auto-alias when header not bare identifier before write per #7969; create `contracts/dialect-matrix.yaml` 5 dialects x quoting char (`"` DuckDB/PG vs `` ` `` BQ) / case folding (lower vs preserve vs insensitive) / reserved alias-unquoted GH #2058 / string/regex/date flips (`regexp_extract` vs `substring` vs `REGEXP_EXTRACT`, `TRY_STRPTIME<->SAFE.PARSE_DATE`) / null-safe `IS DISTINCT FROM` vs `COALESCE` (non-SARGable) / type canonicalization `DECIMAL(18,2)->NUMERIC`; shared `quote(col,dialect)` lib is single authority for mapping + Soda. Must NOT hand-author checks without matrix, must NOT bare `order`/`Revenue ($)`.
  Parallelization: Wave 1 | Blocked by: - | Blocks: 2,3,5,9,14
  References (executor has NO interview context - be exhaustive): `src/main/java/com/shreyas/saleslens/service/SemanticMapperService.java:41-63 REGISTRY` + `src/main/java/com/shreyas/saleslens/model/enums/InferredType.java:14` + `src/main/java/com/shreyas/saleslens/service/inference/TypeDetectionService.java:20-22 regex` + `.omo/plans/research/G-YAML-01.md:51-69` + `.omo/plans/gap-research/G-YAML-01-SPEC.md:183-273 JSON Schema` + `.omo/plans/research/G-SCHEMA-01.md:533-611 dialect tables` + `.omo/plans/oss-mapping-wedge-CRITIQUE.md:§1`
  Acceptance criteria (agent-executable): `make validate-mapping` passes T1 on `fixtures/mapping-examples/shopify_orders__reserved.yaml` with `order` + `Revenue ($)`; `contracts/dialect-matrix.yaml` exists and `quote("Revenue ($)", "bigquery")` = `` `Revenue ($)` `` + `quote("order","postgres")` = `"order"`; `ajv validate -s schema/mapping.schema-1.0.json -d fixtures/mapping-examples/shopify_orders__reserved.yaml` exit 0, bad YAML without `spec_version` exit 1.
  QA scenarios (name the exact tool + invocation): happy `ajv` on valid example (pass) + failure `order` without quoting — T3 must error `Parser Error syntax error at or near "order"` on unquoted emit vs pass quoted `SELECT "order" AS "order_id"`; Evidence `.omo/evidence/task-1-saleslens-final-build-plan.md` with compiled SQL per dialect.
  Commit: Y | feat(schema): add mapping schema + dialect matrix + quoting lib

- [ ] 2. MappingIR dataclasses + Dialect lowering via sqlglot
  What to do / Must NOT do: Implement `src/mapping/ir.py` frozen dataclasses `Warehouse(duckdb|postgres|bigquery|snowflake|trino)`, `TransformKind(7)`, `VerificationState`, `DeterministicCondition(expr_sql,dialect_authored,ast)`, `Transform(kind,params,on_error)`, `MappingRuleIR(source_column,canonical_entity,canonical_field,confidence,state,transform,condition,audit)`, `MappingFileIR(rules,version,frozen=True hash for rule_id)` per `gap-research/G-YAML-01-SPEC.md:415-503`; `src/mapping/dialect.py` `DialectEmitter` protocol `quote_ident/quote_relation/emit_predicate/emit_transform/emit_select/supports_try_cast` delegating to `sqlglot` `Dialect/Generator` `IDENTIFIER_START/END, DATE_PART_MAPPING, TRANSFORMS` — not hand string concat; Optimizer `dedup,pushdown`; `generate()` path `YAML->SemanticManifest(Pydantic)->LogicalPlan{SourceScan,Project{sqlglot Expr},Filter{structured}}->Optimizer->Engine`; enforce `assert_values_exhausted` on dialect branches; version dispatch `spec_version` frozen builder `v1_0_to_v1_1.py`; snapshots `fixtures/snapshots/<rule_id>/<warehouse>.sql`. Must NOT direct YAML->SQL without IR (reproduces `ambiguous column YELLOW` #1930 / `__` dedup bugs).
  Parallelization: Wave 1 | Blocked by: 1 | Blocks: 3,9,14
  References: `gap-research/G-YAML-01-SPEC.md:300-386 quoting demo + 415-518 IR + 533-611 matrix + 795-887 versioning` + `research/G-YAML-01.md:99-111 IR + 142-163 matrix` + `pom.xml:162 mapstruct` no Python IR — new Python package `src/mapping/` + `pyproject.toml` uv + basedpyright strict
  Acceptance criteria: `pytest tests/mapping/test_ir_emit.py -k test_reserved_quoted` passes: YAML with `order`+`Revenue ($)` -> DuckDB `SELECT "order" AS "order_id", regexp_extract("Revenue ($)", '^\$?([\d,]+\.?\d*)$',1)::DECIMAL` + PG `regexp_match`+`text[]` + BQ `` `order` AS `order_id`, REGEXP_EXTRACT(...,r'...') SAFE_CAST `` and `test_two_compiler_coherence.py` string-equality quoted ident; `mypy --strict src/mapping` 0 errors; solo bounding week not quarter.
  QA scenarios: happy `pytest -k test_quoting_demo` (pass) + failure `test_unquoted_fails` emits `SELECT order` -> must raise before write; Evidence `.omo/evidence/task-2-saleslens-final-build-plan.md` with IR snapshot diffs.
  Commit: Y | feat(mapping): implement MappingIR + dialect lowering

- [ ] 3. 3-tier `mapping validate` harness + coherence + CI gate
  What to do / Must NOT do: Implement CLI `mapping` with `validate --syntax --execute --dialect --coherence` + `mapping test --exhaustive 1 x 1` + `mapping diff` + `mapping migrate --from --to --dry-run --apply`; T1 offline JSON Schema+semantic (`SemanticManifestValidator` per dbt-fusion #9762), T2 `information_schema.columns` binding (narrow typed probe), T3 ephemeral `duckdb.connect(":memory:")` + stub schema + `EXPLAIN` over generated SQL (<2s/200 rules) + BigQuery `Client(dry_run=True,use_query_cache=False)` zero-slot no charge per `gap-research/G-YAML-01-SPEC.md:650-652`; PR diff compares canonicalized `mapping_manifest.json` not raw YAML; `mapping_manifest.json` pins `generator_version` for replay idempotence (`same fingerprint x same version -> byte-equivalent`); Soda coherence `validate --coherence` cross-EXPLAIN mapping SQL vs `generate_soda_contracts(manifest,dialect)` SQL per dialect (quoted fragment equality + canonical round-trip) per #2108; `.github/workflows/mapping-validate.yml` 6-step `0/1/2` exits per `research/G-SCHEMA-01.md` harness. Must NOT `validate` that is syntax-only — T3 execution is mandatory.
  Parallelization: Wave 1 | Blocked by: 1,2 | Blocks: 9,17
  References: `gap-research/G-YAML-01-SPEC.md:626-724 T1/T2/T3 + 728-791 coherence` + `research/G-YAML-01.md:243-293 harness` + `research/G-SCHEMA-01.md:690-696 ephemeral + 699-713 diff` + `src/main/resources/db/migration/V10__canonical_schema.sql:87 external_refs` for stub schema
  Acceptance criteria: `make validate-mapping` on fixture `shopify_orders__reserved` (order/Revenue ($)) T3 FAILS if quoting omitted (`Parser Error`) and PASSES when quoted; `mapping validate --dialect=bigquery --dry-run` passes without creds; `mapping validate --coherence` executes both compilers per dialect and reports `quoted fragment equality` pass; `mapping diff` on PR with `rev->revenue_net` rename shows `RENAME_MAPPED_FIELD` + requires MAJOR bump (or `exit 1`) per G-SCHEMA-01.
  QA scenarios: happy `mapping validate --execute` on good manifest (pass) + failure `order` unquoted bad emit `SELECT Revenue ($) AS revenue` -> T3 error per #2058 alias-unquoted; Evidence `.omo/evidence/task-3-saleslens-final-build-plan.md` with T1/T2/T3 logs + `dryRun:true` response.
  Commit: Y | feat(mapping): add 3-tier validate harness + coherence + CI

- [ ] 4. Replay ledger DDL — Merkle BLAKE3 + stable key + 3 tables + indexes + pgbench
  What to do / Must NOT do: Create `V19__replay_ledger.sql` with `replay_runs(id uuid pk, source_id uuid fk DataSource, file_path text, file_fingerprint text unique, mapping_version_id text Git SHA, ingested_at timestamptz, row_count int, status text)`, `replayed_files(id uuid, run_id fk, file_fingerprint unique, canonical_row_hash text, created_at)` + `UNIQUE(file_fingerprint)`, `replay_cell_diffs(id uuid, run_id fk, source_id, canonical_field, field_from, field_to, rule_id text, applied_at timestamptz, diff jsonb {field,from,to,rule_id})` cell-granular 200 bytes/cell (not 400MB row blob) + BRIN on `applied_at` + GIN on `diff` changed fields + B-tree `(source_id,canonical_field,applied_at)` + `run_id` + `rule,time`; add `replay_leases(source_id primary, holder text, expires_at, fencing_token bigint)` + `environment_pins(env text pk, mapping_version_id text, pinned_at)` + `UNIQUE(ledger_id,mapping_version_id)` for re-replay on version bump; define `is_unchanged = file_fingerprint = BLAKE3(sorted canonical_row_hash per row)` where `canonical_row_hash = BLAKE3(parsed+normalized+sorted row bytes)` (byte SHA256 fast-path short-circuit only, Elysiate 2026-04-10), `rule_stable_key = SHA256(field|normalized_pattern_hash|kind|scope)` — same dirty at line 904 matches not `v4`; concurrency `pg_advisory_xact_lock(hashtext(source_id::text))` XACT scope + `INSERT ... ON CONFLICT (file_fingerprint) DO NOTHING` + single-TX drop atomic (ledger+canonical) + `last_offset+1` watermark after commit + lease heartbeat + `replay --check` stale detection; pgbench harness `bench/replay_90d.sh` synthetic 90d 26M rows 1.09M diffs 0.18GB 1,040 files 6 queries Q-AUDIT-1..5; Bloom filter optionally for fast-path. Must NOT ledger in Git (20 commits/sec kernel limit) or naive row blob 36GB/90d.
  Parallelization: Wave 1 | Blocked by: - | Blocks: 7,8,9,10,17
  References: `research/G-LEDGER-01.md:38 sources 620 lines` + `.omo/plans/SOURCE-TABLE-GAPS.md:38` + `src/main/java/com/shreyas/saleslens/model/StagedRecord.java:47 recordHash` + `src/main/java/com/shreyas/saleslens/model/DataSource.java:67` + `src/main/resources/db/migration/V17__add_dedup_constraint` + `research/G-BATCH-C-P1.md G-LEDGER` + `docker-compose.yml:Postgres` + `src/main/resources/application.yaml:ddl-auto validate`
  Acceptance criteria: `psql \d replay_runs` shows 3 tables + 6 indexes + `UNIQUE(file_fingerprint)`; `BLAKE3(sorted canonical_row_hash)` on fixture reordered-rows+timestamp column = same fingerprint (byte-only would differ) proves zero re-verify; `pgbench -f bench/q_audit_1.sql -c 10 -T 60` p99 0.02-1.2s on 90d synthetic; concurrent `replay --check` on same `source_id` — one wins, other idempotent; `EXPLAIN (ANALYZE)` uses BRIN for 90-day window.
  QA scenarios: happy `INSERT` new file_fingerprint (pass) + failure duplicate fingerprint `ON CONFLICT` idempotent (no fork) + failure concurrent drop without advisory lock -> duplicate `replay_runs` (must fail before fix); Evidence `.omo/evidence/task-4-saleslens-final-build-plan.md` with `\d+` + pgbench logs + 0.62GB/yr calc.
  Commit: Y | feat(ledger): add replay ledger DDL + Merkle + indexes + bench

- [ ] 5. Schema evolution — taxonomy + registry gate + FK=PK + dialect variance
  What to do / Must NOT do: Implement `contracts/schema-evolution-policy.md` 14-row breaking taxonomy per `research/G-SCHEMA-01.md:§1.2` (add nullable col that is rename of mapped field = MAJOR at canonical surface zero-row field; every FK retarget/PK/m:n grain = MAJOR with canonical DDL migration `rebuild_constraints` per-child per Percona `ALTER each child DROP old FK ADD new FK->new_tbl(col)`; `major_version` partitions history, BREAKING without MAJOR -> `exit 1` with `RENAME_MAPPED_FIELD`+field deterministic `422 kind+field` precedent, transitive `FULL` vs all priors not just latest, consumer-ACK on MAJOR before merge, GitHub Actions snippet + worked `rev->revenue_net` CI output; 16-row Scope x Change matrix (field/type/nullability/FK/PK/m:n/identifier/condition -> Bump x Migration x Gate kind `FK_RETARGET/FK_EQ_PK_DRIFT/MN_JUNCTION_ADDED/RESERVED_UNQUOTED`) routable to selective gates; FK=PK junction detection `fk_is_half_of_pk` + `@@id([orderId,productId])` invariant (Prisma 8) + `rebuild_constraints` DDL per child + BFS blast radius (`data-contract-enforcer` lineage BFS + `ViolationAttributor`) + `relationships+not_null` FK suite + `unique+not_null` PK suite; `contracts/dialect-matrix.yaml` 5 dialects x quoting/case/null-safe/type pinned alongside contract; harness 2-layer L1 sqlglot transpile dry-run (BQ/Snowflake without creds lenient) + L2 ephemeral DuckDB `:memory:` 3 suites A quoting/reserved-word + B FK orphan + junction PK + type/narrow + grain + C null-safe `IS DISTINCT FROM` (prefer over `COALESCE` non-SARGable). Must NOT `validate` without FK=PK or without version bump enforcement.
  Parallelization: Wave 1 | Blocked by: 1 | Blocks: 7,10,17
  References: `research/G-SCHEMA-01.md:837 lines 54 sources` + `research/G-YAML-01.md L316 semver` + `src/main/java/com/shreyas/saleslens/model/enums/InferredType` + `src/main/java/com/shreyas/saleslens/repository/SourceSchemaRepository.java:20` + `src/main/java/com/shreyas/saleslens/service/SchemaManagementService.java:24 promoteAttribute` + `src/main/java/com/shreyas/saleslens/controller/SchemaManagementController.java:64`
  Acceptance criteria: CI on PR renaming `rev`->`revenue_net` without MAJOR bump `exit 1` with `RENAME_MAPPED_FIELD revenue_net`; `validate --execute` suite B catches FK half staleness orphan + junction PK drift; `dialect-matrix.yaml` checked in and `make validate-mapping` proves `Revenue ($)` + `order` alias-unquoted fail at T3; `FULL_TRANSITIVE` vs all priors passes.
  QA scenarios: happy `add nullable non-mapped col` -> MINOR pass + failure `rev->revenue_net` without bump -> `exit 1` + failure FK retarget without `rebuild_constraints` -> orphan probe fails; Evidence `.omo/evidence/task-5-saleslens-final-build-plan.md` with taxonomy table + CI log + matrix.
  Commit: Y | feat(schema): add evolution policy + registry gate + FK=PK harness

- [ ] 6. Hybrid plane — Arrow IPC stream + DuckDB aggregates + daemon pool
  What to do / Must NOT do: Implement `src/mapping/hybrid/` with `ipc_stream.py` `pa.ipc.new_stream`/`RecordBatchStreamReader` length-prefixed 8-64k rows/batch over stdout pipe (NOT JSON-over-stdin NOT Parquet temp NOT Flight/gRPC handshake tax), optional `ShmPipeTransport` (vgi-rpc shared-mem) >=100k batches (7045 vs 3324 MB/s); quality path `src/mapping/quality_duckdb.py` all 6 dimensions as DuckDB SQL aggregates on `arrow_scan` zero-copy vectorized 2048 rows/chunk (GIL per chunk not per row) `UNION ALL` single round-trip 1-10 rows materialized — NO `to_pylist` (`getitem->Scalar->as_py` 20% GC+25% GetScalar+7% useful, 2.5-10x slower) — map `Completeness COUNT_IF null / Validity GROUP BY LIMIT10 / Uniqueness COUNT DISTINCT/HLL / Consistency COUNT_IF pred / Timeliness MAX(event_time) / Accuracy min/max vs μ±3σ`; lifecycle daemon `src/mapping/daemon.py` long-lived forkserver pool 2-4 workers supervised `request_queue->Worker->response_queue` (soothe/py-drakkar `PipesSubprocessClient`), `StreamPipelineScheduler.java:88 30000` + `windowSeconds 30` + `KafkaSourceRegistryService.java:31 volatile Map 300000 ttl` stays Java, watermark `last_offset+1` contiguous HWM only after ledger+canonical commit `AckMode.MANUAL` after tx; cold-start model warm p99 <15s at 30s PASS vs per-window spawn 2.0s x 2880=96min/day FAIL, pin Python 3.11 (3.12 #30893 forkserver regression). Must NOT per-window spawn or `to_pylist` or JSON pipe.
  Parallelization: Wave 1 | Blocked by: - | Blocks: 16,18
  References: `.omo/plans/research/G-HYBRID-01.md:422 lines` + `src/main/java/com/shreyas/saleslens/service/ingestion/StreamPipelineScheduler.java:88` + `src/main/java/com/shreyas/saleslens/service/ingestion/LiveSalesEventConsumer.java:37 AckMode.RECORD` + `src/main/java/com/shreyas/saleslens/service/ingestion/StreamIngestionJobManager.java:28 activeWindows` + `src/main/java/com/shreyas/saleslens/config/StreamKafkaConfig.java:37 concurrency 3 DLT` + `src/main/java/com/shreyas/saleslens/config/KafkaTopicConfig.java:34` + `src/main/resources/application.yaml:streaming.poll 30000 window 30s`
  Acceptance criteria: `pytest tests/hybrid/test_ipc_p99.py` shows p99 50k x 20col IPC 80-220ms vs JSON 1.1-2.4s (table) and `to_pylist`-free proven via single `UNION ALL` (1-10 rows); warm daemon p50 0.58s / p99 1.16s (20-col) / 3.5-8.2s (180-col) =11-27% of 30s PASS; per-window spawn benchmark 1.2s+0.8s model =2.0s encoded as FAIL; `Python 3.11` pinned in `pyproject.toml` + `Dockerfile`.
  QA scenarios: happy warm daemon 50k rows (pass p99) + failure per-window spawn at 30s cadence -> 2.0s overhead measured FAIL + failure `to_pylist` on 180-col -> 6-10s (40-67%) must be rejected; Evidence `.omo/evidence/task-6-saleslens-final-build-plan.md` with IPC vs JSON table + daemon logs + `arrow/issues/50326` profile citation.
  Commit: Y | feat(hybrid): add IPC stream + daemon + DuckDB aggregates

## Final verification wave
> Runs in parallel after ALL todos in this wave. ALL must APPROVE.
- [ ] F1. Plan compliance audit
- [ ] F2. Code quality review
- [ ] F3. Real manual QA
- [ ] F4. Scope fidelity

## Commit strategy
- Conventional Commits `type(scope): summary` — `feat`, `fix`, `docs`, `chore`
- 1 per todo (max 2 if harness+impl), no squash across gates
- Before push: `./mvnw test -Dtest='!SaleslensApplicationTests'` + `make validate-mapping` (wave 1) or `pytest -q` (wave 3) or `pgbench -T 10` (wave 1) — per-wave
- Flyway new `V19+` only, `ddl-auto:validate` stays

## Success criteria
- Bad triad `order`/`Revenue ($)` compiled and `validate --execute` catches quoting (Suite A) + BigQuery `dryRun` parse
- Ledger `is_unchanged` Merkle + stable key + DDL+6 indexes+0.18GB/90d+advisory lock+<2min bench
- CI rejects `rev->revenue_net` without MAJOR + covers m:n+FK=PK + `dialect-matrix.yaml`
- IPC stream + DuckDB aggregates (no `to_pylist`) OR measured p99 <50% window (15s at 30s)
- Interviews/bake-off can start in parallel (wave 1 not blocking learning)

---
> Parent: `.omo/plans/saleslens-final-build-plan.md` (355 lines, 20 todos, hardened Appendix 8 patches) — this wave is a slice. Hardened Appendix applies to this wave's todos. Run `$start-work` per wave independently.
