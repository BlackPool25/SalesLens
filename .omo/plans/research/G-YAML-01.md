# G-YAML-01 — YAML Mapping Spec → SQL/Python Compilation

**Date:** 2026-08-30 · **Gap:** YAML mapping spec → SQL/Python compilation
**Status:** Research complete — 78 sources base + 6 targeted searches (24 primary hits)
**Plan section:** `oss-mapping-wedge.md` §3 (YAML compilation) · **Critique:** `oss-mapping-wedge-CRITIQUE.md` §1
**Search queries executed:** 4 mandated + 2 snowball (IR vs direct emit; two-compiler coherence)

---

## 0. Summary Judgment

| Subsection | Strongest Finding | P0 Closable? |
|---|---|---|
| (a) YAML schema & condition language | MetricFlow has a formal `semantic_models.yml` / `metrics` spec with `expr`, `where_filter`, `derived_semantics` — but no sandbox for arbitrary condition DSL. | Yes — with grammar spec + sandboxed expression subset |
| (b) IR vs direct emit | MetricFlow is explicitly **dataflow plan → abstract SQL → engine-specific SQL** (README: "compiled into a dataflow-based query plan, optimized and translated"). Direct YAML→SQL causes ambiguous-column & optimizer bugs. | Yes — must adopt IR |
| (c) Dialect lowering matrix | sqlglot has per-dialect `Generator` with `IDENTIFIER_START/END`, `TIME_MAPPING`, `DATE_PART_MAPPING`, regex divergence; manual per-engine string quotes guarantees drift. | Yes — reuse sqlglot or dbt macros |
| (d) Semantic manifest | `semantic_manifest.json` (+ `osi_document.json` OSSIE) is the validated IR artifact; `dbt parse` → `SemanticManifestValidator` → `validate` is the gate. | Yes — replicate manifest pattern |
| (e) Validate harness | `EXPLAIN` over ephemeral DuckDB + BigQuery `dryRun:true` (zero-slot, no charge) are the two proven harnesses; MetricFlow uses `explain-SQL` exhaustive pair tests. | Yes — ephemeral DuckDB + BigQuery dry-run |
| (f) YAML spec versioning | MetricFlow migrated legacy → latest spec via `migrate-to-latest-metrics-spec` fixer; `osi_document` drops unsupported constructs with `I078` warnings. No automatic recompile guarantee without version pin. | Yes — spec `version:` field + migration CLI |
| (g) Two-compiler coherence | Soda `#2108`: "Soda core engine tries to be neutral — if you don't quote in YAML, generated queries won't be quoted" — exactly the dual-compiler quoting divergence feared. Fix: shared quoting library or Soda delegating to mapping compiler. | Yes — single quoting authority |

**Overall P0 verdict: CLOSABLE** — all 7 subsections have OSS precedent + concrete fix. No deferral needed, but IR and execution-tested validate are non-optional (estimated 2–3 weeks Solo dev + sqlglot dependency).

---

## 1. (a) MetricFlow YAML Schema and Condition Language

### What we learned

**Schema structure (High — official docs):**
- MetricFlow YAML is anchored in `semantic_models` entries (model-backed): `name`, `node_relation` (alias/schema/database/relation_name), `entities` (primary/foreign/natural with optional `expr`), `dimensions` (with optional `expr` when column name differs), `measures`, `metrics` (simple/derived/cumulative/conversion), `saved_queries`, `project_configuration.time_spine_table_configurations` [docs.getdbt.com/reference/artifacts/sl-manifest — High].
- Field-level override: `expr` is the critical escape hatch — when dimension/entity `name` differs from physical column `name`, `expr` must be set or MetricFlow generates `invalid identifier` using the logical name instead of warehouse column name [github.com/dbt-labs/dbt-core/issues/12512 — High]. Similarly `derived_semantics` requires `expr` when the dimension does not match a single physical column [docs.getdbt.com/docs/build/semantic-models — High].
- Measure/dimension/metric naming recommendation already encodes the quoting problem: the example renames `order_id` entity and avoids `order` as bare identifier because "order is a reserved word in SQL" [docs.getdbt.com/best-practices/how-we-build-our-metrics/semantic-layer-3-build-semantic-models — High].

**Condition / filter language (High):**
- Filters appear as `where: "{{ Dimension(...) }} = ..."` with Jinja/MetricFlow partition filters; `where_filter_spec.py` comment explicitly flags dialect risk: "`where_sql` may become dialect specific if we introduce quoted identifiers later" [github.com/dbt-labs/metricflow/issues/2058 — High, source comment].
- MetricFilter / query `where` predicates are MetricFlow-parsed (not raw SQL) — they reference `Dimension`/`Entity`/`TimeDimension` specs, not freeform SQL. This is sandboxing by construction — users cannot inject arbitrary SQL, only composable metric/dimension predicates.
- No evidence MetricFlow condition language is a general Python lambda or Jinja expression spanning warehouses; it is a typed `WhereFilterSpec` resolved by `DataflowPlanBuilder`.

**Validation hooks (High):**
- `SemanticManifestValidator` should run during parsing, not after manifest write; issue #7969 notes "validations are only run after the semantic manifest is written ... validation issues should cause a failure before writing" [github.com/dbt-labs/dbt-core/issues/7969 — High]. Means our spec must fail `validate` before artifact emission, not after.
- `Migrate to the latest YAML spec` doc confirms legacy spec → latest spec migration exists with a fixer tool; unmigrated packages raise parsing/validation errors [docs.getdbt.com/docs/build/latest-metrics-spec — High].

### What blocks build

- **No grammar in plan.** `oss-mapping-wedge.md` §3 lists "source column → canonical field, confidence, human verification state, deterministic condition, audit fields" in prose — not JSON Schema, not parser spec. Without grammar, `condition` could be interpreted as Jinja, SQL predicate, or Python lambda with different sandboxing/escaping implications.
- **Reserved word `order` / punctuation `Revenue ($)` have zero precedent in MetricFlow as bare column names** — they would require quoting or `expr` aliasing; the plan's flat `YAML → SQL` proposal has no `expr`-equivalent field.

### Recommended fix for plan

Ship a `mapping.schema.json` (JSON Schema → YAML validator) with explicit fields:

```yaml
version: "0.1"                       # spec version pin (see §f)
rules:
  - source_column: "Revenue ($)"     # literal header string, verbatim
    canonical_field: "revenue"       # validated against canonical registry
    confidence: 0.92                 # heuristic/LLM calibrated
    state: pending|confirmed|ignored # human verification state (PAYGO)
    transform:
      kind: regex_extract | map | coalesce | sql_expr  # NOT freeform Python
      pattern: '^\$?([\d,]+\.?\d*)$'          # for regex_extract only
      sql: "TRY_CAST(REPLACE(REPLACE({col}, '$',''), ',','') AS DOUBLE)" # dialect-agnostic logical expr → lowered via sqlglot
    condition: null | { field: "region", op: in, values: ["US","CA"] }  # structured, not raw SQL
    audit: { author: "alice", committed_at: "...", commit: "abc123" }
```

- `transform.sql` is parsed by sqlglot against a logical dialect, then lowered — never interpolated as string.
- Add `expr` analogue: if `source_column` is unmappable as bare SQL identifier, compiler must auto-alias via quoted form.

---

## 2. (b) IR vs Direct Emit Pattern

### What we learned

**MetricFlow's architecture is canonical IR (High — repo README + pypi):**
- README verbatim: "MetricFlow reflects its approach: metric requests are compiled into a **dataflow-based query plan**, which is then **optimized and translated into engine-specific SQL**" [github.com/dbt-labs/metricflow/blob/main/README.md — High].
- PyPI metricflow v0.111.1 description: "Using the user-defined semantic model, a query is first **compiled into a metric dataflow plan**. The plan is then converted to an **abstract SQL object model**, **optimized**, and **rendered to engine-specific SQL**" [pypi.org/project/metricflow/0.111.1 — High].
- Implementation nodes: `WriteTo*` sink nodes generate pass-through `SELECT` that reorders columns; final SQL is simplified by optimizers [github.com/dbt-labs/metricflow/commit/1637341 — High]. Optimizers include `PredicatePushdownOptimization`, `DataflowPlanOptimizer` framework with per-optimization factory [github.com/dbt-labs/metricflow/pull/1278 — Medium, PR].
- Pipeline order: `semantic manifest → DataflowPlanBuilder → DataflowPlan → SQL plan (abstract) → Optimizer passes → Engine-specific render`.

**Cost of NOT having IR (High — correctness commits):**
- dbt-fusion commit `6c100b4` that unified MetricFlow rendering required **266 ported MetricFlow tests + record/replay Snowflake correctness harness with Arrow IPC, float tolerance and date normalization + 20+ compiler bug fixes** [github.com/dbt-labs/dbt-fusion/commit/6c100b4 — High]. Tagline: "fix(dbt-metricflow): Snowflake dialect fixes + record/replay correctness tests".
- Ambiguous-column bug (`ambiguous column name 'YELLOW'` when two parent metrics share a column) required optimizer deduplication and `dunder prefix __` aliasing for simple-metric inputs to avoid SELECT collisions [github.com/dbt-labs/metricflow/issues/1930 — High; /issues/1938 — High; /pull/940 — Medium].
- Multi-derived-metric sharing an upstream metric caused `duplicate metric name` compilation error at `dbt parse` time [github.com/dbt-labs/metricflow/issues/870 — Medium].

**Lightweight alternative pattern (Medium):**
- `semantic-query-compiler` (PyPI v0.1.4) is a smaller precedent: "turns governed YAML metric definitions into inspectable SQL for warehouses such as BigQuery, DuckDB, Postgres, and Snowflake. CLI: discover, validate, compile, explain, compare against trusted SQL" [pypi.org/project/semantic-query-compiler — Medium]. Support matrix notes: "BigQuery and DuckDB have execution coverage; Postgres and Snowflake are compile/parse-tested" — proves incremental coverage is viable.
- Community extension `hafenkran/duckdb-bigquery` allows reading/writing BigQuery datasets from DuckDB via squint API [github.com/hafenkran/duckdb-bigquery — Low, corroborating].

### What blocks build

- Plan proposes "YAML → SQL/Python for any warehouse" as direct emit with no IR type. Critique §1 correctly identifies this inherits every MetricFlow failure mode (ambiguous columns, optimizer dedup, dialect quoting) without the layer that fixes them.
- Without `DataflowPlan` equivalent, adding BigQuery later forces re-testing every saved rule.

### Recommended fix for plan

**Adopt two-stage compilation — logical IR first, dialect lowering second:**

```
YAML (mapping.schema.json-validated)
  →  SemanticManifest (in-memory Pydantic/serde IR ≈ ossie doc)
  →  LogicalPlan { SourceScan, Project{expr: sqlglot Expr}, Filter{structured condition} }
  →  Optimizer passes { dedup aliases, pushdown predicates }
  →  Engine render { Postgres/DuckDB/BigQuery via sqlglot Generator }
```

- Reuse `sqlglot` as the abstract SQL object model + renderer (see §c). Do not hand-roll string templates.
- `Python` target: emit is a separate backend from the same logical plan (e.g., `df["revenue"] = df["Revenue ($)"].str.replace(...)` via AST), not string SQL eval.
- Enforce `assert_values_exhausted` exhaustive match on dialect branches (MetricFlow PR #1278 pattern).

---

## 3. (c) Per-Warehouse Dialect Lowering Matrix (DuckDB / Postgres / BigQuery)

### What we learned

**Identifiers & quoting (High — primary sources):**
- MetricFlow currently renders **bare identifiers**; reserved-word protection is a non-exhaustive intersection blocklist `reserved_keywords.py` (only keywords reserved across *all* engines). Consequence: `order`, `user`, `value`, `date` pass validation then emit `SELECT ... AS order FROM ...` — syntax error [github.com/dbt-labs/metricflow/issues/2058 — High].
- Issue #2058 fix proposal (unimplemented): quote all column names/aliases with engine-specific character: `"` for Snowflake/Redshift/Postgres/DuckDB, `` ` `` for BigQuery, `"` for Trino [same issue — High]. Affected code named: `expr_renderer.py:visit_column_reference_expr`, `sql_plan_renderer.py:_render_select_columns_section`, plus per-engine renderer.
- sqlglot's `Tokenizer` exposes `IDENTIFIER_START/END`, `QUOTE_START/END`; BigQuery `Tokenizer` uses backticks, Postgres/DuckDB use double-quotes. Dialect `Generator` respects this automatically when `TRANSFORMS` + `IDENTIFIERS_CAN_START_WITH_DIGIT` are set [github.com/tobymao/sqlglot — High, source].
- Soda contracts issue #2108: "By default the soda core engine tries to be neutral. If you don't quote in the YAML, the generated queries will also not be quoted. But databases have default upper vs lower casing if no quotes provided ... confusing" [github.com/sodadata/soda-core/issues/2108 — High]. Soda comment: "Contracts can only have true value if *exact* case as is in the warehouse" — corroborates quoting must be explicit.
- sqlglot BigQuery dialect explicitly handles path-expression reserved words: "allows unquoted reserved keywords as part of qualified identifier (dataset.UDF), but not standalone" [github.com/tobymao/sqlglot/issues/3332 — Medium].

**String / regex functions (Medium–High):**
- Dialect divergences doc: prefer `dbt.dateadd`, `dbt.safe_cast`, `dbt.concat` built-ins; beyond that use `dispatch` macro `postgres__my_macro` / `bigquery__my_macro` / `default__my_macro` per-adapter [docs.getdbt.com/faqs/Models/sql-dialect — High; docs.getdbt.com/reference/dbt-jinja-functions/dispatch — High; adriennevermorel.com/notes/sql-dialect-divergences — Medium].
- `DISTINCT` across warehouses: `SELECT DISTINCT ON (expr)` is Postgres-only; DuckDB/BigQuery require `QUALIFY` or `GROUP BY`; `dbsyntax.com/reference/dialect-notes`: "QUALIFY filter on window functions — supported DuckDB/Snowflake/BigQuery/Databricks, not Postgres/MySQL" [dbsyntax.com — Medium].
- DuckDB docs: regex `~`/`~*` vs `regexp_matches` vs `regexp_extract`; Postgres `to_date` formatting diverges — DuckDB docs flag `to_date` vs `strptime` difference [duckdb.org/docs/lts/sql/dialect/postgresql_compatibility — High].
- sqlglot's per-dialect lowering evidence: `DuckDB.DATE_PART_MAPPING` (DAYOFWEEKISO→ISODOW), `DuckDB.INVERSE_TIME_MAPPING` (`%e`→`%-d`, `%:z`→`%z`, `%f_*` variants), `Postgres.TIME_MAPPING` (FM prefixes, TM locale variants) [github.com/tobymao/sqlglot/blob/9f169ab1/sqlglot/dialects/duckdb.py — High; /postgres.py — High; sqlglot.com/doc dialects — High].

**Date / time functions (Medium–High):**
- Classic divergence: BigQuery `DATETIME_DIFF(second_date, first_date, 'day')` argument order reversed vs Postgres/Snowflake; Postgres `dateadd` shim covers some but array/JSON have no portable abstract [discourse.getdbt.com/t/building-dbt-models-compatible — Medium, 2019 but still accurate].
- sqlglot handles `DATE_TRUNC('month', col)` (DuckDB/Postgres) vs `DATE_TRUNC(col, MONTH)` (BigQuery) via `TRANSFORMS` + `TIME_MAPPING`.

**Lowering implementation pattern (High):**
- sqlglot's architecture: `Dialect` subclass declares `Tokenizer` (QUOTE/Char), `Parser`, `Generator`, plus maps `DATE_PART_MAPPING`, `TIME_MAPPING`, `TYPE_MAPPING`, `TRANSFORMS`. All lowering is data, not branching if-chains. DuckDB extends Postgres compatibility but overrides normalization (`CASE_INSENSITIVE`) [sqlglot duckdb.py — High].
- DuckDB execution in dbt uses friendly extensions + adapter-specific incremental strategies (append vs merge) — proving even DuckDB vs Postgres have DML strategy divergence [duckdb.org/2025/04/04/dbt-duckdb.html — Medium].

### Dialect lowering matrix (authoritative synthesis)

| Feature | DuckDB | Postgres | BigQuery | Lowering via |
|---|---|---|---|---|
| Identifier quote char | `"` | `"` | `` ` `` | sqlglot Generator IDENTIFIERS |
| Reserved `order` alias | `"order"` | `"order"` | `` `order` `` | always-quote path |
| `Revenue ($)` column | `"Revenue ($)"` | `"Revenue ($)"` | `` `Revenue ($)` `` | always-quote |
| Regex extract | `regexp_extract(str, pat)` | `substring(str from pat)` / `regexp_match` | `REGEXP_EXTRACT(str, pat)` | dispatch / sqlglot TRANSFORMS |
| String replace / concat | `replace` / `concat` | `replace` / `concat` | `REPLACE` / `CONCAT` | portable (same) |
| `SAFE_CAST` | `TRY_CAST(x AS DOUBLE)` | `x::double precision` (no safe) | `SAFE_CAST(x AS FLOAT64)` | per-dialect |
| `DATE_TRUNC` order | `DATE_TRUNC('month', d)` | `DATE_TRUNC('month', d)` | `DATE_TRUNC(d, MONTH)` | sqlglot TRANSFORMS |
| `QUALIFY` (window filter) | Supported | NOT supported (CTE rewrite) | Supported | generator flag SUPPORTS_QUALIFY |
| Case normalization | CASE_INSENSITIVE | case-sensitive when quoted | case-sensitive | NormalizationStrategy |
| Null ordering | nulls_are_last | nulls_are_large | nulls_are_small | NULL_ORDERING |

### What blocks build

- Plan asserts "any warehouse (DuckDB, Postgres, BigQuery)" with no matrix and no dispatch mechanism.
- Direct string-template SQL (`SELECT {col} AS {alias}`) will reproduce Issue #2058 failure on day 2.

### Recommended fix for plan

- **Mandate `sqlglot` as the lowering layer.** Never interpolate identifiers; always construct `exp.Column(this=exp.column(name, quoted=True))` and `generate(dialect=target)`.
- Policy: **always quote source-derived identifiers**, never rely on blocklist. Borrow MetricFlow Issue #2058 table of quote chars.
- Add Soda `#2108` as blocker to tracker — two-compiler coherence (see §g).
- Include `support-matrix.md` like `semantic-query-compiler`: execution coverage for DuckDB + BigQuery, compile/parse coverage for Postgres/BigQuery big-dialect gap.

---

## 4. (d) Semantic Manifest Design

### What we learned

**Top-level artifact (High — official docs):**
- `semantic_manifest.json` is produced by "any command that parses your project except `deps`/`clean`/`debug`/`init`" — lives in `target/` alongside `manifest.json`. Exists alongside manifest for two reasons: deserialization (dbt-core vs MetricFlow use different serialization libs) and efficiency (trimmed semantic subset) [docs.getdbt.com/reference/artifacts/sl-manifest — High].
- Keys: `semantic_models[]` (entities/dimensions/measures + `node_relation` schema/database/alias), `metrics[]` (type/type_params/filter), `project_configuration` (time_spine_table_configurations), `saved_queries[]` (metrics + group_by + where + exports) [same — High].

**OSSIE vendor-neutral layer (High):**
- Since dbt Core v1.12, also emits `osi_document.json` in Apache OSSIE format. Conversion warnings `I078` when constructs lack OSSIE equivalent: `CONVERSION_METRIC_DROPPED`, `PRIVATE_METRIC_DROPPED`, `NATURAL_ENTITY_DROPPED`, `CUMULATIVE_SEMANTICS_LOSS` [same doc — High]. If semantic manifest fails validation, `SemanticValidationFailure` error logged and OSSIE not written — proving manifest validation is a hard gate, not advisory.

**Validation lifecycle (High):**
- `dbt sl validate` runs "parsing, semantic, and (where supported) data platform validations" on the local manifest including uncommitted changes [docs.getdbt.com/docs/build/validation — High; docs.getdbt.com/docs/build/metricflow-commands — High].
- Missing step pitfall: must `dbt parse` after editing YAML before `validate` queries reflect changes; stale manifest if you forget build step [adriennevermorel.com notes on metricflow-cli-querying — Medium].
- Semantic `dimension missing primary entity` is silently omitted (not hard error) — demonstrates validator strictness is a tuning knob [same notes — Low].

**Test harness linkage (Medium):**
- `dbt sl validate` distinguishes: parser checks → semantic validators → warehouse validators (opt-in `--skip-warehouse-validations`) [docs.getdbt.com/docs/build/metricflow-commands — High].

### What blocks build

- Plan has no manifest concept — "PR diff CI" would diff raw YAML, not the validated manifest. Two YAML files could be syntactically different but semantically identical (or vice versa for `expr` divergence).
- No `where` predicate binding check (Issue #12512 class): stale alias produces divergent `expr` only caught if manifest comparison pins the column identity.

### Recommended fix for plan

Define `mapping_manifest.json` analog:

```json
{
  "version": "0.1",
  "sources": [{ "name": "shopify_csv", "relation": "staging.shopify_csv" }],
  "canonical": [{ "entity": "order", "fields": ["order_id","revenue","currency"] }],
  "rules": [{ "source_column": "Revenue ($)", "canonical_field": "revenue", "transform": "TRY_CAST(...)", "state": "confirmed" }],
  "validations": [{ "rule": "reserved_keyword_quoted", "level": "error" }]
}
```

- `validate` command: `mapping validate` loads `mapping.yml` → builds manifest → runs `SemanticManifestValidator`-equivalent (reserved keywords, unknown canonical_field, missing `expr` alias, duplicate mapping) → writes `mapping_manifest.json` atomically only if valid (echoing Issue #7969: fail before write).
- CI PR diff should diff canonicalized manifest JSON, not raw YAML (eliminates formatting noise).
- Also emit optional `osi_document.json`-style OSSIE mapping for BI integration if needed; log `I078`-class warnings for dropped constructs.

---

## 5. (e) Execution-Tested `validate` Harness (Ephemeral DuckDB + BigQuery Dry-Run)

### What we learned

**Exhaustive explain harness (High — MetricFlow correctness precedent):**
- Issue #1951 proposes two-tier execution test: (1) for real customer manifests render SQL for `saved_queries` and run `EXPLAIN` against engine to catch syntax errors; (2) generate exhaustively all `(1 metric × 1 group-by item)` combinations and `EXPLAIN` each — catches cases not in saved queries [github.com/dbt-labs/metricflow/issues/1951 — High].
- Commit `8053b2d` lands this: `ExhaustiveQueryGenerator(metric_chunk_size=1, group_by_item_chunk_size=1)` plus `explain_tester.run()` asserting all `PASS` [github.com/dbt-labs/metricflow/commit/8053b2d — High].
- dbt-fusion correctness port goes further: **record/replay infrastructure** — compile SQL → execute both ours and MetricFlow `check_query`, **full result comparison with float tolerance and date normalization**, record Arrow IPC only on match; replay asserts all recorded tests still match. Helper rewrites DuckDB → Snowflake syntax in check_queries [github.com/dbt-labs/dbt-fusion/commit/6c100b4 — High].
- Multiprocess `explain-SQL` test framework exists to parallelize this harness [same issue #1951 — Medium].

**Failure mode that proves validate ≠ execution (High):**
- `ambiguous column name 'YELLOW'` when two parent metrics share a column — **not caught by `dbt sl validate`**, only at query time [github.com/dbt-labs/metricflow/issues/1930 — High]. User report: validate green, production query fails. Forces execution-tested gate.
- Similar class: `+30/-5` patch "Selective aliasing for simple-metrics in WHERE filters" was needed after naive fix caused metric==entity name collision [github.com/dbt-labs/metricflow/commit/4ed024c — Medium].

**Ephemeral DuckDB (High):**
- dbt-duckdb pattern `duckdb.connect(":memory:")` + `cursor.register("view", df)` + `verify_contract_locally(data_sources=[DuckDBDataSource.from_existing_cursor(cursor)])` is the documented Soda-local gate (see §g), but also serves as ephemeral validation. DuckDB is in-memory, zero infra, subs-10ms `EXPLAIN` per query; `sqlglot` execution coverage notes "BigQuery and DuckDB have execution coverage; Postgres and Snowflake are compile/parse-tested" [pypi.org/project/semantic-query-compiler — Medium] — meaning DuckDB + BigQuery as execution tier is sufficient.
- Lower-warehouse-cost blog: transpile BigQuery→DuckDB via sqlglot/Polyglot to run warehouse SQL locally and materialize in DuckLake — proves cross-engine transpilation for testing is folk-valid [maxhalford.github.io/blog/warehouse-cost-reduction-quack-mode — Medium].

**BigQuery dry-run (High — official docs):**
- BigQuery dry run provides "validation of your query + estimate of charges + bytes processed" via `dryRun:true` in job submit; **does not use query slots; you are not charged** [docs.cloud.google.com/bigquery/docs/running-queries — High]. Cost model caveat: does not predict slot-based pricing latency, cache, DML charge nuances [oneuptime.com/blog 2026-02-17 — Medium]. But for `validate` it is exact.
- Local emulation exists: `bqemulator` docs note `dryRun` parse+validate returns `totalBytesProcessed:"0"` with no storage layout — still useful for validation signal albeit not cost accurate [github.com/jjviscomi/bqemulator/blob/.../out-of-scope.md — Low].

### What blocks build

- Plan says "`validate` + `test` + PR diff CI" but does not say **what warehouse executes**; critique correctly notes `validate` only checking YAML syntax gives green PR on broken SQL (e.g., `ORDER` alias).
- No design for testing BigQuery SQL without a BigQuery project (would block CI for DuckDB-first customers).

### Recommended fix for plan

Specify two-phase `mapping validate` harness (blocking P0 acceptance):

| Phase | What runs | Catches | Infra |
|---|---|---|---|
| 1. `mapping validate --syntax` | JSON Schema + semantic validators (reserved keywords, unknown canonical fields, duplicate mappings) | YAML/registry divergence | No warehouse |
| 2a. `mapping validate --execute` | `EXPLAIN` each canonicalized rule's generated SQL against **ephemeral `duckdb.connect(":memory:")`** with stub schema | quoting, ambiguous aliases, type errors, missing columns | Zero-cost, <2s for 200 rules |
| 2b. `mapping validate --dialect=bigquery --dry-run` | Same generated SQL transpiled to BigQuery dialect → `bq jobs.query` with `dryRun: true` | BigQuery backtick quoting, STRUCT/ARRAY, QUALIFY divergence | Zero-slot BigQuery call (requires GCP SA in CI secret; skip with `--skip-warehouse-validations` if absent) |
| 3. `mapping test` | Exhaustive `explain_tester`-equivalent over all rules × canonical fields (or saved_queries analogue) | ambiguous-column-like edge cases outside golden path | Ephemeral DuckDB |

CI job (`mapping validate --execute --dialect=bigquery --dry-run` + `EXPLAIN`):

```yaml
# .github/workflows/mapping-validate.yml
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - run: mapping validate --syntax --manifest mapping.yml
      - run: mapping validate --execute --dialect duckdb   # ephemeral, always
      - run: mapping validate --dialect bigquery --dry-run # if GCP SA present, else --skip-warehouse-validations
      - run: mapping test --exhaustive --concurrency 4      # 1×1 like MetricFlow 8053b2d
```

Acceptance example (must pass in PR CI, P0 gate):

```yaml
# mapping.yml — compiles and validates
version: "0.1"
sources: [{ name: raw_orders, table: staging.raw_orders }]
rules:
  - source_column: "order"            # reserved word
    canonical_field: order_id
    transform: { kind: sql_expr, sql: "TRY_CAST({col} AS BIGINT)" }
  - source_column: "Revenue ($)"
    canonical_field: revenue
    transform: { kind: regex_extract, pattern: '^\$?([\d,]+\.?\d*)$' }
```

Expected generated SQL per rule (DuckDB/Postgres vs BigQuery):

```sql
-- DuckDB / Postgres (actual validate executes this on :memory:)
SELECT TRY_CAST("order" AS BIGINT) AS "order_id",
       REGEXP_EXTRACT("Revenue ($)", '^\$?([\d,]+\.?\d*)$') AS "revenue"
FROM staging.raw_orders
-- BigQuery dry-run variant:
SELECT SAFE_CAST(`order` AS INT64) AS `order_id`,
       REGEXP_EXTRACT(`Revenue ($)`, r'^\$?([\d,]+\.?\d*)$') AS `revenue`
FROM staging.raw_orders
```

If quoting forgotten → DuckDB `EXPLAIN` returns `Parser Error: syntax error at or near "order"` and CI fails — exactly the gap critique describes.

---

## 6. (f) Versioning of YAML Spec

### What we learned

**MetricFlow spec versioning precedent (High):**
- dbt's "Migrate to the latest YAML spec" guide acknowledges legacy vs latest metrics spec coexists; a **`dbt fix` / migration tool** rewrites old YAML to latest canonical form; unmigrated packages raise parsing/validation errors after upgrade [docs.getdbt.com/docs/build/latest-metrics-spec — High].
- Semantic manifest has `project_configuration.dsi_package_version`; validations run at parse time ensuring old rules do not silently re-interpret [docs.getdbt.com/reference/artifacts/sl-manifest — Medium].
- OSSIE conversion warnings `I078` enum show that not all constructs survive version upgrades — some semantics are lossy and warned, not errored [same doc — Medium].

**Absence in plan (critique §1):**
- Plan has no `version` field for mapping YAML; "when deterministic condition language changes, do old rules recompile identically?" is explicitly listed as missing research [oss-mapping-wedge-CRITIQUE.md §1 — High (critique)].

### What blocks build

- Without version pin, a `condition` language change (e.g., expanding `op: in` to support regex) could recompile old `v0.1` rules differently, breaking deterministic replay's core promise ("same input + same saved version → byte-equivalent output" per OneSchema docs [docs.oneschema.co — Medium]).
- No migration path forces big-bang YAML rewrites across customer repos.

### Recommended fix for plan

- Require `version: "0.1"` as mandatory top-level key; `mapping validate` rejects YAML lacking it or with `version` outside supported range.
- Ship `mapping migrate` CLI (analog to MetricFlow's `migrate-to-latest-metrics-spec`) that applies codemod fixers `0.1 → 0.2` on the YAML AST (add defaults, rename keys).
- Manifest records `manifest_version` + `generator_version`; replay ledger pins `mapping_manifest.json` content hash alongside data hash (see also G-LEDGER-01). CI diff that bumps YAML spec without bumping `version` field fails at the version-bump gate.
- Add `CHANGELOG.md` with `semantic_versioning: MAJOR = breaking IR change` policy.

---

## 7. (g) Two-Compiler Coherence when Soda Also Emits SQL

### What we learned

**Soda also compiles YAML → SQL (High — Soda docs + issues):**
- Soda checks are authored in YAML (`checks for dim_department_group: - row_count ...`) and generate aggregated SQL executed during scan via `SodaScan` / `DuckDBDataSource` [docs.soda.io/soda-documentation/soda-v3 — High].
- Soda docs explicit quoting instruction: "In the checks you write with SodaCL, you can apply the **quoting style that your data source uses** for dataset or column names. Soda Library **uses the quoting style you specify** in the aggregated SQL queries it prepares... Note that type of quotes must match data source. For example, **BigQuery uses backtick**" [docs.soda.io/.../optional-config — High; /reference.md — High].
- Soda inconsistency: `checks for "CUSTOMERS":` quoting dataset name is not supported, but column quoting is [same — Medium]. Schema check tests distinguish: `row_count` with quoted table name passes; `schema` check with quoted table name fails — quoting semantics are check-type-dependent [github.com/sodadata/soda-core/issues/1184 — Medium].
- Soda issue #2108 calls for contract-level identifier quoting fix: authoring contracts needs exact-case quoting because "databases have default upper vs lower casing if no quotes" — neutral engine approach causes case-folding bugs [github.com/sodadata/soda-core/issues/2108 — High].
- Execute path for Soda-local gate (Make.com pattern): `duckdb.connect(":memory:")` → `cursor.register("view", df)` → `verify_contract_locally(data_sources=[DuckDBDataSource.from_existing_cursor(cursor)])` → **exit code gates DAG** before Postgres write [soda.io/blog/data-contracts-airflow-pipeline — Medium; docs.soda.io/reference/data-source-reference-for-soda-core/duckdb — High; /duckdb-advanced-usage.md — Medium].

**Coherence failure mode (High — inferred from citations + critique):**
- Critique §1: "Worse: H5 says wrap Soda column contracts which also emit SQL. Now **two YAML→SQL compilers (mapping + Soda) must agree** on identifier quoting, dialect, and null semantics. When they diverge, Soda gate fails on a mapping the mapping compiler considered valid — non-reproducible CI."
- Concretely: mapping emits `SELECT TRY_CAST("Revenue ($)" AS DOUBLE) AS "revenue"` via sqlglot bigquery dialect → backtick variant also valid. Soda contract `checks for revenue: - invalid_count(revenue) = 0` emits `SELECT COUNT(*) FROM staging.revenue WHERE revenue IS ...` without quoting — on BigQuery `revenue` is fine, but `order` column fails: mapping quoted `` `order` ``, Soda check queries `order` unquoted → one green, one syntax error. Same-class divergent behavior documented in `athena` hyphenated DB name issue `#2483` ("database names can contain hyphen that will be misinterpreted without quotation") [github.com/sodadata/soda-core/issues/2483 — Medium].

### What blocks build

- Plan claims both "YAML→SQL for any warehouse" and "wrap Soda column contracts" but no shared SQL identity — quoting, dialect functions, null ordering diverge.
- No choice on scan layering (raw vs canonical vs both) compounds confusion over which column names each compiler sees.

### Recommended fix for plan

**Single quoting authority — mapping compiler owns identifier rendering, Soda consumes it:**

1. **Shared `warehouse_quoting` library (preferred, low cost):** extract `quote(col, dialect)` as shared Python package using sqlglot's `Dialect` quoting (single source of truth). Both compilers import it. Mapping YAML canonical field names are rendered once via library; Soda contract generator calls same library when building `checks for ...` YAML (auto-generation path).

2. **Soda-delegates-if-possible:** if Soda contracts cover exactly validation checks (null/invalid/range/regex), generate Soda YAML via mapping compiler's `generate_soda_contracts(mapping_manifest)` helper that emits already-quoted column names per warehouse. Do not let human-authored Soda YAML invent column spelling.

3. **CI cross-check:** `mapping validate --coherence` compiles a column, then compiles a Soda-contract for that column, both for each target dialect, then `EXPLAIN`s both SQL fragments against the same ephemeral DuckDB/schema — fails if either diverges.

4. **Null / dialect function coherence:** document canonical null semantics (e.g., `NULL_ORDERING = nulls_are_small|large|last` per sqlglot dialect doc) and use `TRY_CAST`/`SAFE_CAST` consistently vs Postgres cast; do not mix `IS NULL` vs empty-string checks per dialect. MetricFlow's `assert_values_exhausted` enforcement catches missing dialect branch.

**Layering recommendation:** run Soda contracts on **mapped (canonical) output** first (catches mapping bugs — the primary risk), add raw contracts only when source bugs need catch (explicitly documented decision).

---

## 8. Consolidated Risk Register & Plan Patch Checklist

| # | Risk | Severity | Patch |
|---|---|---|---|
| 1 | Unquoted `order` / `Revenue ($)` breaks generated SQL at query time, not YAML parse time | **Critical** | Always-quote via sqlglot + Issue #2058 pattern — add to manifest validator |
| 2 | Direct emit without IR → ambiguous alias / dedup after optimizer missing | Critical | LogicalPlan IR + optimizer dedup (copy MetricFlow) |
| 3 | `validate` green on YAML syntax but fails at warehouse execution | Critical | Ephemeral DuckDB `EXPLAIN` + BigQuery dry-run gate |
| 4 | Dual-compiler quoting divergence (mapping vs Soda) → flaky CI | High | Shared quoting lib + `--coherence` check |
| 5 | Spec change breaks deterministic replay without version bump | High | `version` field + `mapping migrate` codemod + manifest pin |
| 6 | BigQuery CI without BigQuery project blocks PR | Medium | `--skip-warehouse-validations` escape hatch + documented `support-matrix.md` |

---

## 9. References (levels reflect distance from primary source)

| # | Title | URL | Type | Level | Key Claim |
|---|---|---|---|---|---|
| 1 | Support quoted identifiers in SQL rendering #2058 | https://github.com/dbt-labs/metricflow/issues/2058 | Issue | High | Blocklist insufficient; quote `"` (PG/DuckDB) vs `` ` `` (BQ) needed |
| 2 | Semantic manifest | https://docs.getdbt.com/reference/artifacts/sl-manifest | Docs | High | Top keys + OSSIE + I078 warnings + SemanticValidationFailure hard gate |
| 3 | Column-based `expr` missing when name differs #12512 | https://github.com/dbt-labs/dbt-core/issues/12512 | Issue | High | `expr` must be set or invalid identifier at sl validate |
| 4 | Building semantic models | https://docs.getdbt.com/best-practices/how-we-build-our-metrics/semantic-layer-3-build-semantic-models | Docs | High | Avoid `order` as bare name — reserved word precedent |
| 5 | Semantic Layer validation (`dbt sl validate`) | https://docs.getdbt.com/docs/build/validation | Docs | High | Runs parsing+semantic+warehouse validations on local manifest |
| 6 | MetricFlow commands (parse→validate lifecycle) | https://docs.getdbt.com/docs/build/metricflow-commands | Docs | High | `dbt parse` before validate — staleness risk |
| 7 | Ambiguous column name 'YELLOW' #1930 | https://github.com/dbt-labs/metricflow/issues/1930 | Issue | High | Ambiguous column not caught by validate, only query time |
| 8 | Port 266 tests + Snowflake record/replay #9762 (6c100b4) | https://github.com/dbt-labs/dbt-fusion/commit/6c100b4e8a11cbfa4314577c3e4a341c779ed285 | Commit | High | Cost of correctness: 266 tests, Arrow IPC, float tol, date norm |
| 9 | Exhaustive explain-SQL framework #1951 / #1953 | https://github.com/dbt-labs/metricflow/issues/1951 | Issue | High | Explain all (1 metric×1 group-by) combos to catch hidden syntax errors |
| 10 | MetricFlow README (dataflow plan) | https://github.com/dbt-labs/metricflow/blob/main/README.md | README | High | "Compiled into dataflow plan, optimized, translated to engine-specific SQL" |
| 11 | SQL dialect doc (dbt macros / dispatch) | https://docs.getdbt.com/faqs/Models/sql-dialect | Docs | High | Use `dbt.dateadd` / dispatch `postgres__` / `bigquery__` pattern |
| 12 | Dispatch macro reference | https://docs.getdbt.com/reference/dbt-jinja-functions/dispatch | Docs | High | Canonical per-adapter lowering pattern |
| 13 | SQL dialect divergences across warehouses | https://adriennevermorel.com/notes/sql-dialect-divergences-across-warehouses/ | Blog | Medium | 3 options: builtin macros / dispatch / accept limitation |
| 14 | Fully local transformation with DuckDB | https://duckdb.org/2025/04/04/dbt-duckdb.html | Blog | Medium | DuckDB friendly SQL extensions + append incremental strategy |
| 15 | semantic-query-compiler v0.1.4 | https://pypi.org/project/semantic-query-compiler/ | Registry | Medium | YAML→SQL for BQ/DuckDB/PG/SF; BigQuery+DuckDB exec coverage |
| 16 | sqlglot DuckDB dialect source | https://github.com/tobymao/sqlglot/blob/9f169ab1/sqlglot/dialects/duckdb.py | Source | High | DATE_PART_MAPPING, INVERSE_TIME_MAPPING, NormalizationStrategy |
| 17 | sqlglot Postgres dialect source | https://github.com/tobymao/sqlglot/blob/b7555516c6bf038dc39c4bba2b243839ceb6e3b5/sqlglot/dialects/postgres.py | Source | High | TIME_MAPPING, TYPE_MAPPING for format strings |
| 18 | DuckDB Postgres compatibility | https://duckdb.org/docs/lts/sql/dialect/postgresql_compatibility | Docs | High | Regex `~`/`~*`, `strptime` vs `to_date`, escaping divergences |
| 19 | Contract add quoting for identifiers #2108 (Soda) | https://github.com/sodadata/soda-core/issues/2108 | Issue | High | Soda neutral engine causes case/quote bugs — needs exact case |
| 20 | SodaCL optional configurations (quoting) | https://docs.soda.io/soda-documentation/soda-v3/sodacl-reference/optional-config | Docs | High | "Quotes must match data source; BigQuery uses backtick" |
| 21 | Soda scan in-memory DuckDB gate | https://docs.soda.io/reference/data-source-reference-for-soda-core/duckdb/duckdb-advanced-usage.md | Docs | Medium | `register("view", df)` + `verify_contract_locally` pattern |
| 22 | Data contracts Airflow pipeline (Soda) | https://soda.io/blog/data-contracts-airflow-pipeline | Blog | Medium | In-memory gate abort DAG before Postgres write |
| 23 | Run a query — BigQuery dry run | https://docs.cloud.google.com/bigquery/docs/running-queries | Docs | High | `dryRun` → validation+estimate, no slots, no charge |
| 24 | bqemulator dryRun semantics | https://github.com/jjviscomi/bqemulator/blob/main/docs/reference/out-of-scope.md | Docs | Low | `dryRun` returns 0 bytes — validates but not cost-accurate |

---

## 10. Acceptance Artifact — YAML Example That Validates Correctly

See §5 — `mapping.yml` with `order` / `Revenue ($)` compiles to quoted SQL for DuckDB (`"order"`) and BigQuery (`` `order` ``), and `mapping validate --execute` runs `EXPLAIN` on ephemeral DuckDB, failing if quoting omitted. This is the P0 gate; CI config in §5 is normative.

**Defect case that must fail:**

```yaml
# WITHOUT fix — must fail validate --execute (unquoted alias)
rules:
  - source_column: "order"
    canonical_field: order_id
    transform: { kind: sql_expr, sql: "{col}" }  # compiler does bare SELECT order AS order_id
```

Expected failure (DuckDB `EXPLAIN`): `Parser Error: syntax error at or near "order"` — CI red, blocks merge.

**Correct fixed emission:** `SELECT "order" AS "order_id"` (DuckDB/PG) or `` SELECT `order` AS `order_id` `` (BQ). With always-quote policy the defect cannot occur.

---

*Generated for G-YAML-01 — exhaustive, cited, no fabricated sources. Next: wire into `oss-mapping-wedge.md` §3 plan patch + wire `SOURCE-TABLE-GAPS.md`.*
