# SOURCE-TABLE-GAPS — Cited Sources by Gap

Aggregated markdown table sections per gap. Each row cites a URL and its key claim; Level = High (official docs / primary repo issue or commit) / Medium (maintained blog / registry) / Low (secondary note).

---

## G-YAML-01 — YAML Mapping Spec → SQL/Python Compilation

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| 1 | Support quoted identifiers in SQL rendering #2058 | https://github.com/dbt-labs/metricflow/issues/2058 | Issue | High | Blocklist `reserved_keywords.py` is intersection-only; `order`/`user` pass validate then emit `SELECT … AS order` syntax error; fix = engine-specific quoting `"` vs `` ` `` | Proposal not yet implemented in MetricFlow — proves gap is real but not shipped fix | 2026-05-21 |
| 2 | Semantic manifest | https://docs.getdbt.com/reference/artifacts/sl-manifest | Docs | High | `semantic_manifest.json` (+ `osi_document.json` OSSIE) is validated IR; I078 warnings for dropped constructs; SemanticValidationFailure hard-fails before write | Docs describe artifact, not exhaustive validator list | 2026-08-27 |
| 3 | Column-based dimensions missing expr #12512 | https://github.com/dbt-labs/dbt-core/issues/12512 | Issue | High | If dim/entity `name` differs from column `name`, missing `expr` → manifest uses logical name → `invalid identifier` at sl validate | Single bug report — but root cause is general (missing column binding) | 2026-02-19 |
| 4 | Building semantic models (order reserved example) | https://docs.getdbt.com/best-practices/how-we-build-our-metrics/semantic-layer-3-build-semantic-models | Docs | High | Example explicitly avoids `order` as bare identifier because "order is a reserved word" | Best-practice doc, not validator spec | 2026-07-09 |
| 5 | Semantic Layer validation (`dbt sl validate`) | https://docs.getdbt.com/docs/build/validation | Docs | High | `dbt sl validate` runs parsing + semantic + (where supported) data platform validations on local manifest incl. uncommitted changes | Does not document exhaustive coverage — validate ≠ execution (see #7) | 2026-08-12 |
| 6 | MetricFlow commands (parse→validate lifecycle) | https://docs.getdbt.com/docs/build/metricflow-commands | Docs | High | Must `dbt parse` to refresh `semantic_manifest.json` before validate — stale manifest bug class | Procedural doc, not architecture rationale | 2026-08-19 |
| 7 | Ambiguous column name 'YELLOW' #1930 | https://github.com/dbt-labs/metricflow/issues/1930 | Issue | High | `ambiguous column name 'YELLOW'` when two parent metrics share column — **not caught by `dbt sl validate`**, only at query time | MetricFlow-specific, but demonstrates validate≠execution is systemic | 2025-11-05 |
| 8 | Port 266 tests + Snowflake record/replay (6c100b4) | https://github.com/dbt-labs/dbt-fusion/commit/6c100b4e8a11cbfa4314577c3e4a341c779ed285 | Commit | High | Cost of correctness: 266 tests, Arrow IPC record/replay, float tolerance, date normalization, 20+ compiler fixes | dbt-fusion is post-MetricFlow unified Rust port — scale exceeds OSS wedge but principle applies | 2025+ |
| 9 | Exhaustive explain-SQL framework #1951 | https://github.com/dbt-labs/metricflow/issues/1951 | Issue | High | Two-tier harness: saved_queries EXPLAIN + exhaustive (1 metric×1 group-by) EXPLAIN to catch hidden syntax errors | Issue proposal; commit 8053b2d shows implementation parity | 2025-12-18 |
| 10 | MetricFlow README (dataflow plan) | https://github.com/dbt-labs/metricflow/blob/main/README.md | README | High | "Compiled into a dataflow-based query plan, optimized and translated into engine-specific SQL" — IR is canonical, not optional | README slogan — but corroborated by DataflowPlanBuilder source & PR #1278 | 2025+ |
| 11 | SQL dialect doc (dbt built-ins / dispatch) | https://docs.getdbt.com/faqs/Models/sql-dialect | Docs | High | Use `dbt.dateadd`/`safe_cast`/`concat` built-ins; beyond that dispatch per adapter | Warehouses beyond BigQuery/Postgres/DuckDB not enumerated | 2026-05-18 |
| 12 | Dispatch macro reference | https://docs.getdbt.com/reference/dbt-jinja-functions/dispatch | Docs | High | Canonical lowering pattern `postgres__my_macro` / `bigquery__my_macro` / `default__my_macro` | Jinja dispatch — not sqlglot, but same IR concept | — |
| 13 | SQL dialect divergences across warehouses | https://adriennevermorel.com/notes/sql-dialect-divergences-across-warehouses/ | Blog | Medium | Three options: builtin macros / dispatch / accept limitation; BigQuery `APPROX_COUNT_DISTINCT` example | Third-party notes, not official dbt position | 2026-03-27 |
| 14 | Fully local transformation with DuckDB | https://duckdb.org/2025/04/04/dbt-duckdb.html | Blog | Medium | DuckDB friendly extensions + adapter-specific incremental strategies prove DML divergence even DuckDB vs PG | Focused on dbt-duckdb adapter, not semantic layer | 2025-04-04 |
| 15 | semantic-query-compiler v0.1.4 | https://pypi.org/project/semantic-query-compiler/ | Registry | Medium | YAML→SQL for BQ/DuckDB/PG/SF; BigQuery+DuckDB have execution coverage; PG/SF compile/parse-tested | Early beta (0.1.0), support-matrix not exhaustive | — |
| 16 | sqlglot DuckDB dialect source | https://github.com/tobymao/sqlglot/blob/9f169ab1/sqlglot/dialects/duckdb.py | Source | High | `DATE_PART_MAPPING`, `INVERSE_TIME_MAPPING`, `CASE_INSENSITIVE` normalization — lowering is data not branches | Source file — authoritative but must track sqlglot version | — |
| 17 | sqlglot Postgres dialect source | https://github.com/tobymao/sqlglot/blob/b7555516c6bf038dc39c4bba2b243839ceb6e3b5/sqlglot/dialects/postgres.py | Source | High | `TIME_MAPPING` (FM/TM locale), type mappings — confirms format string divergence is material | Same caveat as #16 | — |
| 18 | DuckDB Postgres compatibility | https://duckdb.org/docs/lts/sql/dialect/postgresql_compatibility | Docs | High | Regex `~`/`~*` vs `regexp_*`, `strptime` vs `to_date`, escaping divergences | DuckDB-specific; BigQuery divergences not listed here | — |
| 19 | Soda contracts #2108 (case/quoting) | https://github.com/sodadata/soda-core/issues/2108 | Issue | High | Soda neutral engine ("don't quote → don't quote") causes Snowflake upper vs Postgres lower case bugs — needs exact case quoting | Contracts-focused; SodaCL `checks for` behavior slightly different | 2024-06-26 |
| 20 | SodaCL optional configurations (quoting) | https://docs.soda.io/soda-documentation/soda-v3/sodacl-reference/optional-config | Docs | High | "Type of quotes must match data source; BigQuery uses backtick" — Soda does not infer quoting | Docs example, not engine spec | 2026-05-04 |
| 21 | Soda scan in-memory DuckDB gate | https://docs.soda.io/reference/data-source-reference-for-soda-core/duckdb/duckdb-advanced-usage.md | Docs | Medium | `cursor.register("view", df)` + `verify_contract_locally` pattern for in-memory gate before Postgres write | Soda advanced usage — requires DuckDB connection handle | — |
| 22 | Data contracts Airflow pipeline (Soda) | https://soda.io/blog/data-contracts-airflow-pipeline | Blog | Medium | In-memory gate that aborts DAG if checks fail before Postgres write — precedent for validate executing before load | Blog narrative, not API spec | — |
| 23 | BigQuery dry run (running queries) | https://docs.cloud.google.com/bigquery/docs/running-queries | Docs | High | `dryRun:true` → validation+estimate, no slots, no charge — perfect for CI without warehouse access | Predicts bytes not slot latency; DML charge nuance ignored | 2026-08-12 |
| 24 | bqemulator dryRun semantics | https://github.com/jjviscomi/bqemulator/blob/main/docs/reference/out-of-scope.md | Docs | Low | Emulator `dryRun` always returns `totalBytesProcessed:"0"` — validates but not cost-accurate | Emulator docs — low authority, but corroborates validation signal still useful | — |
| 25 | Migrate to the latest YAML spec | https://docs.getdbt.com/docs/build/latest-metrics-spec | Docs | High | Legacy → latest spec migration via fixer; unmigrated packages raise parse errors after upgrade | Docs describe migration tool existence, not its internals | 2026-08-19 |
| 26 | sqlglot BigQuery reserved-path qualified identifier #3332 | https://github.com/tobymao/sqlglot/issues/3332 | Issue | Medium | BigQuery allows reserved keyword qualified (dataset.UDF) but not standalone — nuanced quoting scope | Edge case for nested/qualified identifiers | 2024-04-17 |
| 27 | Missing quoting Athena #2483 / datacontract Snowflake #797 (coherence) | https://github.com/sodadata/soda-core/issues/2483 | Issue | Medium | Hyphenated DB names misinterpreted without quoting — shows coherence cost when one compiler quotes and other doesn't | Athena-specific but principle general | 2025-11-18 |
| 28 | Warehouse cost reduction via transpilation (Max Halford) | https://maxhalford.github.io/blog/warehouse-cost-reduction-quack-mode/ | Blog | Medium | Transpile BigQuery→DuckDB via SQLGlot to run warehouse SQL locally — folk validation of cross-engine testing | Blog pattern, not quantitative benchmark | 2026-03-12 |

---

## G-HYBRID-01 — Hybrid Plane Isolation (Java Window + Python Subprocess)

**Searches:** 4 SearxNG (searxng_tech_search ×2, searxng_web_search ×2, 10 hits each, 8 screened per search + 4 follow-ups at 10 hits each). 30 unique sources deduplicated to 26 rows below; each search contributes 5-7 best hits as required.

### Search 1 — `Arrow to_pylist GIL bottleneck Python UDF DuckDB quality checks avoid dict materialization` (searxng_tech_search)

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| H1-1 | to_pylist on list-typed arrays 2.5–10× slower than via pandas #50326 | https://github.com/apache/arrow/issues/50326 | Issue | High | `pa.Array.to_pylist()` 2.5–10× slower than `to_pandas().values.tolist()` detour; Spark Arrow-serialized Python UDFs hit this (Spark PR #56940/#56943); pandas detour rejected due to coercion `[1, None, 3]→[1., nan, 3.]` | List-typed arrays; struct/map similar but magnitude varies | — |
| H1-2 | GH-50327: Convert without per-element Scalars in to_pylist | https://github.com/apache/arrow/pull/50327 | PR | High | Scalar-free path via `_getitem_py` specializations (int/float/bool/string/list/struct/map); nested compose; base `GetScalar+as_py` preserved for dates/times/decimals/dict | Requires PyArrow ≥25; still one virtual call per element until #50448 | — |
| H1-3 | Quickly Expanding DuckDB Functionality with Scalar Python UDFs | https://duckdb.org/2023/07/07/python-udf.html | Blog | High | Arrow UDF = chunk-level 2048 rows, zero-copy; built-in types UDF = per-row GIL. Zero-copy PyArrow Tables + vectorization | 2023 — aggregate/table UDF not yet in Python API | 2023-07-07 |
| H1-4 | GH-28694: Arrow to Python list conversion is slow | https://github.com/apache/arrow/issues/28694 | Issue | High | Root cause: `to_pylist` = `getitem → Scalar → as_py()` per element; 20% GC, 25% GetScalar, 7% useful; ~10 µs vs 200-800 µs per element | Older issue (2021) — closed by 50327 chain but profile still valid pre-fix | 2021-06-04 |
| H1-5 | GH-50430: maps_as_pydicts scalar-free | https://github.com/apache/arrow/pull/50430 | PR | High | Extends scalar-free to `MapArray` with `maps_as_pydicts`; avoids per-element `MapScalar`+dict build | Map-specific; but proves dict materialization is separate hot path | — |
| H1-6 | GH-50448: per-range not per-element | https://github.com/apache/arrow/issues/50448 | Issue | High | Current `to_pylist` = one `_getitem_py` virtual call per element; proposes `_getitem_range_py(offset,length)` batching | Open enhancement — not yet shipped; confirms overhead remains | — |
| H1-7 | DuckDB Python UDF source `python_udf.cpp` | https://github.com/AstroVela/vane/blob/857b9f0f/src/duckdb_py/python_udf.cpp | Source | High | `PythonGILWrapper gil;` acquired per DataChunk (≤2048 rows); owning `py::object` per call | Mirror of upstream; accurate but not canonical repo | — |

### Search 2 — `Java Python subprocess Arrow IPC serialization latency vs Parquet file handoff cold start daemon` (searxng_web_search)

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| H2-1 | Replace Py4J with Native PyArrow #49 (paimon-python) | https://github.com/apache/paimon-python/issues/49 | Issue | High | Py4J + `ArrowUtils.serializeToIpc` = process-communication + ser/de bottleneck; native PyArrow removes bridge | Paimon-specific; but pattern is general Java→Python Arrow IPC | 2025-04-06 |
| H2-2 | Integrating PyArrow with Java (Arrow v19) | https://arrow.apache.org/docs/19.0/python/integration/python_java.html | Docs | High | Canonical same-host handoff: `pa.ipc.new_file(temp,schema) → sink.write_batch` and `C Stream Interface` `RecordBatchReader`; temp IPC file is documented Java↔Python path | Docs show file variant; stream sibling exists but seek requirement differs | — |
| H2-3 | vgi-rpc — Transport-agnostic RPC on Arrow IPC | https://vgi-rpc.query.farm/ | Docs | Medium-High | Pipe/subprocess/Unix/TCP/shm/HTTP over Arrow IPC; defines services as interfaces, typed client proxy, no .proto | New project (Query.Farm); microbenchmark not 180-col dirty | — |
| H2-4 | Benchmarks — vgi-rpc | https://vgi-rpc-python.query.farm/benchmarks/ | Benchmark | Medium-High | Raw RecordBatch 39 µs dataclass / 69 µs row-batch; subprocess/Unix/pool 0.07-0.16 ms, pipe slightly higher, HTTP +0.5 ms WSGI | Median microbench; no p99 at 50 k rows | — |
| H2-5 | Streaming, Serialization, and IPC (Arrow v25) | https://arrow.apache.org/docs/python/ipc.html | Docs | High | `RecordBatchStreamReader` needs no seek; `RecordBatchFileReader` does; mmap lets OS page lazily for >RAM arrays | Docs, not benchmark | — |
| H2-6 | Zerrow: True Zero-Copy Arrow Pipelines (Bauplan) | https://arxiv.org/html/2504.06151v2 | Paper | Medium-High | Writer Copy 3.8×, Zero Copy 3.9× faster than Full Copy; Zero Copy further 2.3× vs Writer Copy on reader (10 GB/10-col) | 10 GB offline; ratio applies but absolute latency scales down | — |

### Search 3 — `Dagster asset Python subprocess cold start daemon vs per run spawn overhead Kafka window 30s` (searxng_tech_search)

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| H3-1 | Reduce initial process start time #19666 | https://github.com/dagster-io/dagster/discussions/19666 | Discussion | High | 3 trivial ops: 12 s overhead with full imports, 5 s stripped; per-K8sClient.run adds cost; forkserver vs spawn matters | Local anecdotal; import cost nevertheless universal | — |
| H3-2 | Define Dagster asset that invokes subprocess (Pipes) | https://docs.dagster.io/integrations/external-pipelines/using-dagster-pipes/create-subprocess-asset | Docs | High | `PipesSubprocessClient.run` synchronously executes subprocess in pipes session, returns `PipesClientCompletedInvocation` | Still spawns per invocation by default — not pooled | — |
| H3-3 | Using Dagster for fast non-Python code #17899 | https://github.com/dagster-io/dagster/discussions/17899 | Discussion | High | Empty-op latency = per-step spawn; `forkserver` multiprocessing drastically improves per-process overhead | Multiprocess executor, not Pipes — but same forkserver principle | — |
| H3-4 | Latency step_worker_started→resource_init #25780 | https://github.com/dagster-io/dagster/issues/25780 | Issue | High | New process re-imports all packages; tune via `tuna` + `PYTHONPROFILEIMPORTTIME=1`; multiprocess spawns per step | Confirms per-step cold-start is import cost | 2024-11-07 |
| H3-5 | Forkserver preload misbehaving on 3.12 #30893 | https://github.com/dagster-io/dagster/issues/30893 | Issue | High | `preload_modules` worked on 3.11, broken on 3.12 (subsequent steps as slow as first, as if spawn) | Pin to 3.11 until fix | 2025-06-25 |
| H3-6 | Executing jobs — concurrent subprocesses & forkserver | https://docs.dagster.io/guides/build/jobs/job-execution | Docs | High | Forkserver reduces per-process overhead; max concurrent subprocesses limit configurable | Docs-level guidance | — |
| H3-7 | Avoiding ECS Cold Start #7332 | https://github.com/dagster-io/dagster/discussions/7332 | Discussion | Medium | Standing gRPC server (`DefaultRunLauncher`) avoids cold-start vs ECS per-run; custom wrapper can choose | ECS-specific; principle = standing server amortizes | — |

### Search 4 — `Python quality checks DuckDB SQL without to_pylist Arrow zero-copy vs materialized dicts` (searxng_tech_search)

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| H4-1 | SQL on Apache Arrow — DuckDB | https://duckdb.org/docs/current/guides/python/sql_on_arrow | Docs | High | `FROM arrow_table` / Scanners push filters/selection into Arrow compute, async IO; Arrow Datasets queryable as tables | Docs — confirms zero-copy query path | — |
| H4-2 | DuckDB with Apache Arrow: Zero-Copy (Dench) | https://www.dench.com/blog/duckdb-with-arrow | Blog | Medium | `FROM table` reads Arrow buffer in place (zero-copy read); write to Arrow allocates but avoids deserialization | Blog consistent with upstream docs | 2026-03-26 |
| H4-3 | DuckDB Quacks Arrow (2021-12-03) | https://duckdb.org/2021/12/03/duck-arrow | Blog | High | Zero-copy streaming DuckDB↔Arrow; compose queries across both; parallel vectorized | Original announcement; streaming Collector materialized until later | 2021-12-03 |
| H4-4 | 3.7× Faster Pipelines: Arrow & ADBC vs SQLAlchemy (dltHub) | https://dlthub.com/blog/arrow-adbc-vs-sqlalchemy | Blog | Medium | `to_pylist()` → millions of dicts = row-by-row ser cost; streaming Arrow batches as-is via ADBC bulk avoids Python objects | Marketing but measurement matches Arrow profile | 2026-02-03 |
| H4-5 | DuckDB Python Quickstart Part 2 (MotherDuck) | https://motherduck.com/learn/duckdb-python-quickstart-part2/ | Guide | Medium | `.arrow()/.pl()` materialize entire result; push filtering/sorting/aggregation into DuckDB before materializing; prefer native SQL over UDFs | Guide — states rule: keep compute inside DuckDB | 2026-07-07 |
| H4-6 | Python Function API — DuckDB UDF GIL | https://github.com/duckdb/duckdb/discussions/4797 | Discussion | High | Function call overhead + GIL prevents parallelism; scalar UDFs integrated, aggregate via C API only | Confirms why 6-checker UDF path bottlenecks | 2023-10-05 |

### Follow-up Searches (Arrow IPC/Flight throughput + daemon lifecycle + Parquet vs IPC)

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| H5-1 | Benchmarking Arrow Flight (ACM doi) | https://doi.org/10.1145/3527199.3527264 | Paper | High | Flight DoPut 1650 MB/s remote, DoGet 2000 MB/s (16 streams, 7000 MB/s fabric); local 10,000 MB/s; genomics 500 MB/s | Remote 16-stream; not single-host pipe | 2022-04-02 |
| H5-2 | ARROW-11066: [Java][FlightRPC] fix zero-copy | https://github.com/apache/arrow/pull/9354 | PR | High | Zero-copy never applied in Java; fixing +50% localhost: 580→864 MB/s avg | Java localhost gRPC — shows zero-copy > protocol tuning | — |
| H5-3 | Arrow Flight SQL Tuning (Santos 2026) | https://michael.business/en/articles/arrow-flight-sql-tuning-for-low-latency-analytical-exports | Blog | Medium | Batch sweet spot 16 k-262 k rows; <16 k gRPC frame overhead, >262 k tail; 10 M rows 1.8 GB: Flight 4.1 s vs JDBC 48.2 s (11×), CPU 94%→18% | Export workload not streaming windows — but frame tradeoff applies | 2026-08-14 |
| H5-4 | ARROW-15282: non-gRPC data planes (shared memory) | https://issues.apache.org/jira/browse/ARROW-15282 | Jira | High | gRPC-over-Unix 3324 MB/s vs shared-mem 7045 MB/s (1 stream), 10037 vs 25012 MB/s (4 streams), 16-17 µs vs 35-44 µs | C++ experimental; Linux x86/Arm only | — |
| H5-5 | py-drakkar watermark offset tracking | https://pypi.org/project/py-drakkar/1.20.0/ | Registry | Medium-High | Per-partition watermark commit only after every sink confirms; crash detection via watchdog vs SIGKILL/OOM | Framework opinion; but watermark is correct primitive | — |
| H5-6 | Vectorized Reads via Arrow IPC (Meridian) | https://rustycloud.org/data_lakes_track/module-05-query-engine-integration/lesson-02-vectorized-reads-arrow-ipc.html | Guide | Medium | Row-oriented 100 GB CPU decode vs IPC few hundred µs per batch + memcpy; 10-100× advantage compounds; 8-64 k rows per batch | Course material but consistent with Flight/Santos | — |
| H5-7 | Querying Parquet with Millisecond Latency (Arrow) | https://arrow.apache.org/blog/2022/12/26/querying-parquet-with-millisecond-latency/ | Blog | High | Parquet block-oriented, lowest unit hundreds-thousands rows; must stream batches sized to amortize decode but small enough for concurrency | Explains why Parquet handoff for 5-50 k rows pays block tax | 2022-12-26 |
| H5-8 | paimon-python #49 + Tributary deep dive (combined) | https://tributarydata.substack.com/p/apache-arrow-the-missing-deep-dive | Blog | Medium | Java→Python IPC mandatory copy in both processes → bottleneck; Serialization format may fail on rich structures | Blog but captures file vs IPC failure mode | 2025-03-08 |

---

## G-SODA-01 — Soda Wrapping — Column Contracts + Per-Column Calibration

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| 1 | Soda Contract Autopilot | https://soda.io/blog/contract-autopilot | Blog | Medium | Generate Contracts analyzes dataset patterns/distributions/structure to produce contract reflecting "good" for that table — proposal-diff workflow | Marketing not API spec |
| 2 | Soda distribution checks method reference | https://docs.soda.io/soda-documentation/soda-v3/sodacl-reference/distribution | Docs | High | `ks` default continuous / `chi_square` categorical; available `psi`, `swd`, `semd` | SodaCL vs Contracts two syntaxes |
| 3 | Contract Language reference — columns block | https://docs.soda.io/reference/contract-language-reference | Docs | High | Every contract `columns[]` with per-column `checks[]` type+threshold `must_be` etc | Does not define p99 calibration |
| 4 | sodadata/soda-core README columns example | https://github.com/sodadata/soda-core?tab=readme-ov-file | Repo | High | Canonical YAML: `columns: - name: id checks: - missing:` / `invalid valid_values` | Example only |
| 5 | Soda data contracts intro | https://docs.soda.io/soda-documentation/soda-v3/data-contracts | Docs | High | Contract stipulates schema/freshness/missing/validity; executed each ingest; failure = quarantine | Dataset-level description |
| 6 | Data contracts Airflow pipeline — in-memory gate | https://soda.io/blog/data-contracts-airflow-pipeline | Blog | Medium | In-memory gate: inbound DataFrame in DuckDB before Postgres → fails DAG if missing PK/invalid; audit runs live Postgres after write to Soda Cloud | Blog but corroborated |
| 7 | Soda contract webinar Verify (DuckDB) | https://github.com/sodadata/soda-contract-webinar-2025-12-09 | Repo | High | Verify (DuckDB) in-memory data quality gate before DB write | Webinar repo |
| 8 | DuckDB data source — register DataFrame | https://docs.soda.io/reference/data-source-reference-for-soda-core/duckdb | Docs | High | DuckDB `register` in-memory Pandas/Polars DataFrames; `DuckDBDataSource.from_existing_cursor(cursor)` | Requires cursor pattern |
| 9 | Monte Carlo vs Soda comparison 2026 | https://www.modern-datatools.com/compare/monte-carlo-vs-soda | Comparison | Medium | Data Contracts core Soda + AI generation + Git/UI workflow; Monte Carlo not contract platform | Vendor comparison |
| 10 | Verify with Spark session | https://docs.soda.io/soda-documentation/soda-v3/data-contracts/data-contracts-verify.md | Docs | High | `with_data_source_spark_session` for in-memory verify without persist | Spark path distinct |
| 11 | We Ditched Monte Carlo — Soda cut false positives 82% | https://johal.in/we-ditched-monte-carlo-moving-soda-cut-data | Blog | Medium | MC default 140+ false positives/week 18h triage; Soda tunable thresholds cut 82% month 1 | Single anecdote |
| 12 | Data Observability 2026 MC vs GX vs Soda | https://ai-de.net/insights/data-observability-2026-monte-carlo-great-expectations-soda | Blog | Medium | Start 3-5 tables tune 2 weeks FPR <10%; MC 200 anomalies day-1 unknown real; over-alerting ignored | Advice not measurement |
| 13 | When Drift Detectors Cry Wolf — PSI false alarm rates | https://arxiv.org/html/2607.17336v1 | Paper | High | PSI extremely high false alarm at batch 50-100 (~30/30 days), sharp drop after ~200, near-zero thereafter | arXiv HTML |
| 14 | drift-sentinel — p99 via 200 self-splits | https://github.laiyagushi.com/tkgo1599-max/drift-sentinel | Repo | High | Split reference window random 200 times compute PSI between halves drift-free set threshold at p99 | Prototype not Soda native |
| 15 | PR #1395 wasserstein + PSI methods | https://github.com/sodadata/soda-core/pull/1395 | PR | High | Adds `swd`/`semd` alongside psi/ks/chi_square | PR discussion |
| 16 | PR #1666 sampling for distribution checks | https://github.com/sodadata/soda-core/pull/1666 | PR | High | Supports dataset sample for distribution checks | Code PR |

## G-SPLINK-01 — Splink Adapter (Blocking + Seam)

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| 1 | 3. Blocking — Splink tutorial | https://moj-analytical-services.github.io/splink/demos/tutorials/03_Blocking.html | Docs | High | Blocking rules imply trillions comparisons → job fails; DuckDB laptop ≤20M, Spark/Athena <100M before scaling | Guideline not hard limit |
| 2 | Optimising Spark performance | https://moj-analytical-services.github.io/splink/topic_guides/performance/optimising_spark.html | Docs | High | Small cluster 8 machines start ~100M loosening to ~1B achievable | Assumes partitioning |
| 3 | What are Blocking Rules? | https://moj-analytical-services.github.io/splink/topic_guides/blocking/blocking_rules.html | Docs | High | `blocking_rules_to_generate_predictions` single most determinant of runtime; use `count_num_comparisons_from_blocking_rules` audit | Audit must be enforced |
| 4 | Salting blocking rules | https://moj-analytical-services.github.io/splink/topic_guides/performance/salting.html | Docs | High | Salting helps Spark with 100M+ and skew (e.g. John Smith blocks) avoid OOM | Spark-only |
| 5 | Performance Spark vs DuckDB #2209 | https://github.com/moj-analytical-services/splink/discussions/2209 | Discussion | High | DuckDB intrinsically faster; recommend DuckDB for most/all-but-biggest; 1M laptop ~1 min | Discussion not benchmark |
| 6 | Computational Performance blocking | https://moj-analytical-services.github.io/splink/topic_guides/blocking/performance.html | Docs | High | Efficiency of blocking rules matters beyond count; Spark additional configs | General |
| 7 | Optimising DuckDB performance | https://moj-analytical-services.github.io/splink/topic_guides/performance/optimising_duckdb.html | Docs | High | From 3.9.11 DuckDB parallelises 100% cores; `predict()` sometimes needs salting when one rule dominates | Version-bound |
| 8 | Zingg — scalable MDM via Spark ML | https://github.com/zinggAI/zingg | Repo | High | Auto-learning blocking model to millions; active learning 30-50 pairs CLI label | AGPL license |
| 9 | Best OSS ER Libraries Tilores | https://tilores.io/content/best-open-source-entity-resolution-and-record-linkage-libraries-splink-zingg-dedupe-and-when-to-move-beyond-them/ | Blog | Medium | Quadratic growth need safe reduction; blocking-key design bad blocking misses matches or overloads | Comparison |
| 10 | Splink index | https://moj-analytical-services.github.io/splink/index.html | Docs | High | Probabilistic unsupervised EM, backends including Athena, scales 100M+ | Marketing 100M+ needs infra |
| 11 | Ditto — EM as sequence-pair | https://github.com/megagonlabs/ditto/blob/master/README.md | Repo | High | Ditto serializes entry → sequence-pair classification; blocking heuristic beforehand; optimizes matching not blocking | Blocking left to user |
| 12 | Best OSS ER Tools 2026 Kanoniv | https://kanoniv.com/docs/blog/best-open-source-entity-resolution-tools | Blog | Medium | Zingg 30-50 pairs Spark messy data where rules hard | Sells Zingg |
| 13 | GoldenMatch vs Splink vs Dedupe | https://bensevern.dev/blog/2026-04-03-goldenmatch-vs-splink-dedupe-recordlinkage | Blog | Medium | Dedupe slowest failed NC Voter interactive labeling painful | Single blogger |
| 14 | Entity resolution at scale DEV | https://dev.to/gowthampotureddi/entity-resolution-record-linkage-fuzzy-matching-splink-dedupe-at-scale-ee8 | Blog | Medium | Key that only within-block comparisons; Fellegi-Sunter exact/fuzzy levels | Dev.to low authority |
| 15 | Splink vs Dedupe vs Custom Fuzzy | https://fuzzypoint.co.uk/record-linkage-tools-compared-splink-vs-dedupe-vs-custom-fuzzy-matching | Blog | Medium | No serious tool compares all pairs; tooling should help define blocking without hiding recall consequences | Opinion |

## G-DAGSTER-01 — Dagster Asset / Component + Airlift

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| 1 | Creating asset factories | https://docs.dagster.io/guides/build/assets/creating-asset-factories | Docs | High | `AssetFactory` requires `dg` scaffold, `build_defs` returns `Definitions`; dynamic `etl_job: [{bucket, source_object, target_object, sql}]` pattern | Factory not Components v2 |
| 2 | Using partitions (Components) | https://docs.dagster.io/guides/build/components/building-pipelines-with-components/using-partitions | Docs | High | Partitions via `template_vars.py` returning `PartitionsDef`; `post_processing: assets: - target:` | Requires template_vars module |
| 3 | Migrate Airflow tasks — factory per operator | https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/task-level-migration/migrate | Docs | High | Need own factory per operator args match inputs; `load_csv_to_duckdb_defs` per operator | Proves wedge must name factory |
| 4 | Using Dagster and Airflow together (airlift observing) | https://docs.dagster.io/migration/airflow-to-dagster/airflow-component-tutorial | Docs | High | Represent Airflow instance `defs.yaml` `mappings: [{dag_id}]` + `AirflowInstance` | Requires Airflow 2.10+ REST |
| 5 | Observe Airflow tasks — partitions | https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/task-level-migration/observe | Docs | High | Time-partitioned assets auto-associate to partitions; add `DailyPartitionsDefinition` when DAG `@daily` | Schedule must be known |
| 6 | dagster-airlift migration reference | https://github.com/dagster-io/dagster/blob/master/docs/docs/migration/airflow-to-dagster/airlift-v1/migration-reference.md | Docs | High | `dagster-airlift` observes/migrates DAGs; state requires additional Airflow REST calls; order idempotent | Reference not tutorial |
| 7 | Migrate from Airflow at DAG level | https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/dag-level-migration | Docs | High | DAG-level mapping rollback via single line; complex deps difficult Dagster alongside Airflow | Migration risk noted |
| 8 | Migrate DAG-mapped assets | https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/dag-level-migration/migrate | Docs | High | DAG-level proxy single task materializes all mapped assets | Loss task structure |
| 9 | Dagster-Soda integration SodaScanComponent | https://docs.dagster.io/integrations/libraries/soda | Docs | High | Precedent `SodaScanComponent` `checks_paths`+`configuration.yml`+dataset→`AssetKey` | Correct precedent plan ignored |
| 10 | Backfill with partitions | https://docs.dagster.io/guides/build/partitions-and-backfills/backfilling-data | Docs | High | Default N partitions=N runs; `BackfillPolicy` `max_partitions_per_run` reduces 90% overhead; self-dependent serialize | Well-known behavior |

## G-ELEMENTARY-01 — Elementary Lineage + Freshness + Volume

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| 1 | Elementary OSS quickstart tests | https://docs.elementary-data.com/oss/quickstart/quickstart-tests | Docs | High | Tests: anomaly detection + schema changes + Python as native dbt tests; volume/freshness/null rates/dimensions | Overview not cadence |
| 2 | Snowflake integration | https://docs.elementary-data.com/cloud/integrations/dwh/snowflake | Docs | High | Read-only Elementary schema + INFORMATION_SCHEMA metadata + `ELEMENTARY_WAREHOUSE` | Cloud docs but OSS similar |
| 3 | Postgres integration | https://docs.elementary-data.com/cloud/integrations/dwh/postgres | Docs | High | Host/port/db/Elementary schema/user/password — same schema-write requirement | Cloud portable |
| 4 | Freshness anomalies config | https://docs.elementary-data.com/data-tests/anomaly-detection-tests/freshness-anomalies | Docs | High | Config `period: [hour|day|week|month] count: int` for `time_bucket`, `training_period`, `detection_delay`, `ignore_small_changes` | Per-test not global |
| 5 | Schema changes | https://docs.elementary-data.com/data-tests/schema-tests/schema-changes | Docs | High | Alerts on deleted table, added/dropped columns, type change; `elementary.schema_changes` | Orthogonal to cadence |
| 6 | Elementary dbt-data-reliability repo | https://github.com/elementary-data/dbt-data-reliability | Repo | High | Suite anomaly+quality as native dbt tests; volume/freshness/column distributions/schema changes + AI validation | README scope |
| 7 | Volume threshold vs anomaly | https://docs.elementary-data.com/data-tests/volume-threshold | Docs | High | `volume_threshold` explicit % warning/error with caching; `volume_anomalies` z-score dbt test or ML Cloud; direction spike/drop/both | Choice of primitive |
| 8 | Schema changes from baseline + macro | https://docs.elementary-data.com/data-tests/schema-tests/schema-changes-from-baseline | Docs | High | Baseline via `generate_schema_baseline_test` macro must be generated + pinned | Baseline not free |

## G-BPM-01 — Canonical Lineage / BPM Seam

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| 1 | Trust Settings Informatica MDM 10.5 | https://docs.informatica.com/master-data-management/multidomain-mdm/10-5-hotfix-3/configuration-guide/part-4--configuring-the-data-flow/mdm-hub-processes/load-process/trust-settings-and-validation-rules/trust-settings.html | Docs | High | Trust determines survivorship when records consolidated + whether updates reliable to master; decays over time; stewards override | Enterprise but principle portable |
| 2 | FAQ trust affects BO/XREF updates | https://knowledge.informatica.com/s/article/90399?language=en_US | KB | High | Siperian Hub calculates trust per column for two BO records merged highest survives | KB not config spec |
| 3 | Configuring Trust for Source Systems | https://docs.informatica.com/master-data-management/multidomain-mdm/10-4-hotfix-3/configuration-guide/part-4--configuring-the-data-flow/configuring-the-land-process/configuring-source-systems/about-source-systems/configuring-trust-for-source-systems.html | Docs | High | Trust configurable column-by-column per source | Per-column matrix plan lacks |
| 4 | Survivorship Kanoniv | https://kanoniv.com/docs/concepts/survivorship.html | Docs | Medium | Strategies `source_priority` deterministic, `most_recent`, `most_complete`, `aggregate` combines | Kanoniv lightweight precedent |
| 5 | Updates with ranked sources Boomi | https://help.boomi.com/docs/Atomsphere/Master%20Data%20Hub/Hub%20system/c-mdm-Updates_with_ranked_sources_5b05de43-8972-45eb-ba15-4539f3028ba1 | Docs | High | Survivorship field-by-field on ranked sources golden field merged only if source more trusted | Boomi but matches |
| 6 | Cell vs Row survivorship Informatica | https://network.informatica.com/community/s/question/0D56S0000AD6idZSQR/can-we-overwrite-the-mdm-default-behavior-of-cell-value-survivorship-after-merge | Forum | Medium | Row-level Light trust recent `Last_update_date` wins row vs Cell-level Trust curve per column | Community answer |
| 7 | Survivorship rules Primentra | https://primentra.com/blog/survivorship-rules-master-data | Blog | Medium | Matching decides same entity survivorship field-by-field after source priority + completeness fallback recency tiebreaker steward review | Blog but distinguishes match vs survivorship |
| 8 | Match and Merge trusted sources TIBCO EBX | https://docs.tibco.com/pub/ebx-addon/6.2.1/doc/html/mame/admin_guide/trusted_sources.html | Docs | High | Data Steward decision override order trusted sources future merges Supersede survivorship | Proves seam→override ranked source real |

## G-TENANCY-01 — Multi-Tenancy + Pricing Integrity

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| 1 | Soda pricing SPUs $750 | https://soda.io/pricing | Docs | High | Free SPUs quota; $750 crossing for Collaborative/No-code/AI/audit logs/RBAC | Pricing page not metering formula |
| 2 | Data flows between Soda and user | https://docs.soda.io/reference/data-flows-between-soda-and-user | Docs | High | Soda Cloud multi-tenant but does not store record-level data; encrypted; failed rows to customer diagnostics warehouse; only metric/anomaly to Cloud | Critical no row leak |
| 3 | sodadata/soda-core | https://github.com/sodadata/soda-core | Repo | High | CLI+Python YAML contracts 50+ checks Postgres/Snowflake/BigQuery/Databricks/DuckDB | Not tenancy doc |
| 4 | Soda Cloud architecture dataset-q | https://docs.soda.io/soda-documentation/soda-v3/learning-resources/soda-cloud-architecture | Docs | High | Soda Library pushes scan results → creates dataset resource; must remove `checks for dataset-q` from checks.yml to delete | Per-dataset namespaced |
| 5 | Data privacy no raw data to Cloud | https://docs.soda.io/soda-documentation/soda-v3/learning-resources/data-privacy.md | Docs | High | Pushes metadata only via secure API; no raw data by default; column names/averages only | Reinforcement |
| 6 | Deployment options Soda-hosted vs self-hosted Runner | https://docs.soda.io/deployment-options.md | Docs | High | Soda-hosted fully managed + observability requires Soda-hosted; Self-hosted same but customer network | Choice impacts isolation |
| 7 | Data Stack Index Soda cost | https://datastackindex.com/data-observability/tools/soda/ | Review | Medium | Free tier pipeline testing/metrics/alerting fixed SPU quota small projects ~$750 custom | Third-party paraphrase |
| 8 | Config datasource YAML onboarding | https://docs.soda.io/reference/data-source-reference-for-soda-core | Docs | High | Each data source config YAML passed to CLI/Python API onboarding maps to Cloud resource | Per-datasource tenancy via naming |

## G-CONSIST-01 — Consistency (Replay × Kafka × Downstream Mart)

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| 1 | Message Delivery Guarantees Confluent | https://docs.confluent.io/kafka/design/delivery-semantics.html | Docs | High | Exactly-once since 0.11.0.0 via transactional producer; consumer position stored as message in topic committed same tx as output; otherwise at-least-once default | Kafka-side only not app ledger |
| 2 | KIP-98 Exactly Once Transactional Messaging | https://cwiki.apache.org/confluence/spaces/KAFKA/pages/66854913/KIP-98+-+Exactly+Once+Delivery+and+Transactional+Messaging | Spec | High | Kafka at least once producer retries may duplicate on broker crash between commit and ack | KIP design not deploy guide |
| 3 | Exactly-Once Is Three Mechanisms Petascale | https://petascalelabs.com/blog/kafka-exactly-once-idempotence-transactions-read-committed | Blog | Medium | Transactions make writes+offset atomic; but `read_uncommitted` consumers expensive at-least-once read side is half guarantee | Ops-focused |
| 4 | Processing guarantees Databricks Lakeflow | https://docs.databricks.com/aws/en/ldp/best-practices/processing-guarantees | Docs | High | Within managed tables exactly-once via Structured Streaming checkpoints + Delta transactional writes; offsets+output together retried atomically | Correct pattern: offset+output same tx |
| 5 | Doltgres Querying History AS OF | https://docs.doltgres.com/reference/version-control/querying-history | Docs | High | `AS OF` names revision at Dolt commit; system tables `dolt_history_*`/`dolt_diff_*` per-revision query | Dolt-specific |
| 6 | ACID Transactions in Dolt chunk manifest | https://dolthub.com/blog/2023-01-04-acid-transactions/ | Blog | High | Manifest stores root chunk + chunk files; commit writes chunks height-order child before parent atomic manifest overwrite | Impl detail |
| 7 | Dolt Commit Graph Prolly Tree | https://dolthub.com/blog/2024-03-05-commit-graph/ | Blog | High | Prolly Tree structural sharing branch/commit content-addressed | Background |
| 8 | Kafka Transactions coordinator 2PC markers | https://petascalelabs.com/blog/kafka-transactions-coordinator-two-phase-commit-markers | Blog | Medium | LSO first offset where all lower decided; transactional messages decided only on COMMIT/ABORT marker; LSO≤HW if open tx | Needed for visibility |

---

## G-MOAT-01 — Sato/TURL/RECA Learned Type Inference + LLM Eval Harness + Production AI Handling

**Searches:** 8 mandated (A1-A3, B1-B3, C1-C2) + 5 snowball (dirty/abbrev, Doduo, Scalable/CSP, TS-Guessing, Dagster lifecycle) — 13 queries, ~130 hits screened, ~80 cited. See `G-MOAT-01-AI-EVAL.md` Appendix screening log.

### Block A — Learned Type Inference Transfer (Sherlock/Sato/TURL/RECA) — 13 rows

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| A-1 | Sherlock KDD'19 — semantic type detection | https://sherlock.media.mit.edu/ | Docs | High | 686,765 cols × 1,588 feats (stat+char+word embeddings+PV) × 78 DBpedia types from VizNet; weighted F1 0.89 | VizNet only; no retail/CRM dirty; ⚠️ 2019 stale for SOTA but architecture invariant | 2019-07-25 |
| A-2 | Sherlock paper (KDD PDF) | https://arxiv.org/abs/1905.10688 | Paper | High | Same corpus/feats; Table T005 SAP `NMFMT`/`INTCA`/`XPLZS` coded abbrev invisible to header-match — proves abbrev transfer failure class | Header-matching induces label noise on coded abbrevs | 2019-05-25 |
| A-3 | sherlock-project repo | https://github.com/mitmedialab/sherlock-project | Repo | High | ~500 MB closure + `01-data-preprocessing.ipynb` pipeline to replicate | Single-host Python; must travel with deps in Dagster | — |
| A-4 | Sato VLDB'20 — contextual type detection | https://www.vldb.org/pvldb/vol13/p1835-zhang.pdf | Paper | High | Hybrid: Sherlock DNN + topic subnetworks (table intent) + linear-chain CRF over m cols; 26K tables; F1 0.925 support / 0.735 macro (+3.6pp over Sherlock) | Clean WebTables 26K only; CRF topic coherence fails on `col_14` soup | 2020-07-01 |
| A-5 | Sato training & latency §5.4 | https://cagataydemiralp.io/projects/table-understanding/Sato-VLDB20.pdf | Paper | High | Training 81s/367s (two settings); **+1.4s for 6.4K tables = 0.2ms/table, avg 0.8ms/table on 64c/512GB — POST-feature CRF overhead only**, excludes 1,588-feat extraction | Authors "no retrain unless significant additional data" — understates dirty-domain retrain need | 2020 |
| A-6 | Sato GH (megagonlabs/sato) | https://github.com/megagonlabs/sato | Repo | High | Pretrained on VizNet; dirty transfer is zero-shot | No retail finetune provided | — |
| A-7 | TURL VLDB'21 — table understanding via repr | https://www.vldb.org/pvldb/vol14/p307-deng.pdf | Paper | High | 570,171/5,036/4,964 tables (avg 13 rows ×2 entity cols); TinyBERT N=4 d=312 k=12, visibility matrix (row/col/caption), MER (masked entity recovery) pretraining, 80 epochs | Avg tiny — 180-col wide is >90× column-dimension OOD; MER assumes linkable entities absent in dirty CSV; 80×570K = weeks/multi-GPU | 2021 |
| A-8 | TURL GH (sunlab-osu/TURL) | https://github.com/sunlab-osu/TURL | Repo | High | Docker `xdeng/transformers:latest` PyTorch+Transformers; 570K pretrain split | Fat image; per-Pipes spawn cold-start multiplied | 2020 |
| A-9 | RECA VLDB'23 | https://vldb.org/pvldb/vol16/p1319-sun.pdf | Paper | High | Schema-similar + topic-relevant neighbour via Jaccard/topic; wide-table short sequences; F1 0.853/0.937 on WebTables, macro 0.674/0.783; data-efficient (5%/75% training) | **Requires related-table corpus** — with 20 isolated CSVs signal is zero; WebTables only | 2023 |
| A-10 | RECA GH (ysunbp/RECA-paper) | https://github.com/ysunbp/RECA-paper | Repo | High | `compute_jaccard.py` + `pre-process-webtables.py` + `tokenized_data/` corpus prereqs | Corpus curation is separate data-engineering project | — |
| A-11 | Doduo ICDE'22 — columns with PLMs | https://doi.org/10.1145/3514221.3517906 | Paper | High | Single BERT beats SOTA with **8 tokens/col**, joint type+relation, +4.0% CTA / +11.9% CPA over Sato-class; **512 token limit** → wide table splitting needed | 8 tokens from `rev/__EMPTY_3` is noise without retail retrain; narrow tables only | 2022-06-10 |
| A-12 | doduo GH (megagonlabs/doduo) | https://github.com/megagonlabs/doduo | Repo | High | `megagonlabs/doduo` pip toolbox `AnnotatedDataFrame(coltypes/colrels/colemb)` on Sato-derived VizNet | Same dirty transfer gap as Sato | — |
| A-13 | TabEmb ACL'26 | https://aclanthology.org/2026.acl-long.757.pdf | Paper | High | +4.9/+5.2/+9.2 micro-F1 over Doduo on CTA/CPA/TTA but **quadratic inter-column edges → expensive on wide** + slower than Doduo | Wide cost O(cols²)=32k edges at 180 cols — confirms wide pathology | 2026 |

### Block B — LLM Eval Harness (Valentine m:n, contamination, cardinality, tokens×calls) — 16 rows

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| B-1 | Valentine ICDE'21 + VLDB'14 framework + GH | http://disi.unitn.it/~pavel/OM/articles/Koutras_ICDE21.pdf | Paper/Repo | High | **L1 framework** 7 matchers, 4 relatedness scenarios; Precision/Recall/F1 threshold-based; no LLM native — LLM papers retrofit | 1:1-biased F1; not HitRate@K |
| B-2 | ReMatch (arXiv 2403 retrieval schema matching) | https://arxiv.org/html/2403.01567v2 | Paper | Medium | Reformulates matching **as retrieval, metric Accuracy@K Eq.1**; "**F1 not well defined for m:n — we limit to 1:1/m:1**"; at 1:1/m:1 Accuracy@1 ≡ F1 via argmax | **Explicitly punts m:n** — wedge 20-100-field explicit m:n + FK≡PK not covered |
| B-3 | Schemora (arXiv 2507 multi-stage recommendation) | http://arxiv.org/abs/2507.14376 | Paper | Medium | Vector+lexical retrieval + metadata enrichment → **HitRate@5 +7.49% / @3 +3.75% SOTA on MIMIC-OMOP**; first OSS LLM matcher | Health only; ranked-list cost not in metric |
| B-4 | LLMATCH SchemaNet (arXiv 2507) | https://arxiv.org/html/2507.10897 | Paper | Medium | **SchemaNet 135 cols/14 tabs (9× prior 15-col)**, **Def 3.1 O(target) calls for ranked lists**, FK≡PK equiv, "overly small contexts fragment and degrade" — needs largest source+target fit | Not a stratification paper per se |
| B-5 | OAEI-LLM (CEUR) + OAEI-LLM-T (arXiv 2503 TBox) | https://ceur-ws.org/Vol-3953/361.pdf | Paper | Medium | Extends 7 OAEI schema-matching datasets; classifies hallucinations into types; benchmarks 10 LLMs → leaderboard | OM hallucination focus; not dirty tabular cost |
| B-6 | Schema Matching Exp Study / TaDA 2024 | https://tabular-data-analysis.github.io/tada2024/papers/TaDA.8.pdf | Paper | High | **1-to-N dominates 1-to-1** (5/9 datasets, maximal F1 DiCO/PrDE/SeVD); GPT-4>3.5 all scopes; **F1 + decisiveness-score (fraction non-unknown) + 5 repeats** for hallucination handling | Not cardinality-stratified |
| B-7 | Towards Scalable Schema Mapping (arXiv 2505) + tech report | https://arxiv.org/html/2505.24716v1 | Paper/Report | High | **3 core issues: outputs sensitive (sampling+aggregation), GLaV strains context, cost of repeated calls → prefilter N→k >50% drop with 2 rules/prompt**; **Def 3.1 stable matching (Gale-Shapley mutually preferable)**; **70B Llama-3.1-70B-GPTQ-INT4 77.44 vs GPT-4 Matchmaker 62.20 = +15.2pp** at 70B | **70B, not 7-9B** — does NOT license qwen3:8B>GPT-4; retrieval stratification not isolated |
| B-8 | Prompt-Matcher CSP (arXiv 2408) | https://arxiv.org/html/2408.14507v1 | Paper | High | **CSP = revenue maximisation under GPT-4 budget — NP-hard, (1-1/e) approx greedy; budget/k per round; degenerate if budget ≤ cost(all) or ≤3×mean** | CSP is framework, not empirical benchmark at wedge cardinality |
| B-9 | KG-RAG4SM head-to-head (arXiv 2501 inferred) | (via search B3; arXiv 2501.08686) | Paper | Medium | **GPT-4o-mini+KG beats Jellyfish-8B 35.89% prec /30.50% F1 on MIMIC, 69.20% prec /21.97% F1 on small Synthea (8-table) — where H2 predicts hurt**; context-poisoning admission "irrelevant knowledge decreases perf, ranking/pruning required" | Conflates scale+KG quality; health lexical overlap inflates |
| B-10 | TS-Guessing NAACL'24 (contamination) | https://aclanthology.org/2024.naacl-long.482 | Paper | High | **52% ChatGPT / 57% GPT-4 exact-match on MMLU missing-option TS-Guessing; ~100% when fully contaminated**; TruthfulQA 57% EM | Most audited bench — MIMIC/Synthea worse (medical web-crawled) |
| B-11 | ConTAM (arXiv 2411) | https://arxiv.org/html/2411.03923 | Paper | High | 13 benches ×7 models×2 families: **contamination >> reported** in LLM releases | Not schema-matching specific — but bench-level |
| B-12 | GEM systematic review 2026 | https://aclanthology.org/2026.gem-main.50.pdf | Paper | High | 55-study: **health overlap inflates** — medical schemas/docs heavily web-crawled | Structural — implies MIMIC/Synthea always suspect |
| B-13 | ITD Findings-EMNLP'24 (inference-time decon) | https://aclanthology.org/2024.findings-emnlp.532.pdf | Paper | High | Detect+rewrite leaked → **−22.9% GSM8K / −19% MMLU; Phi-3 −6.7 / Mistral −3.6 on MMLU** | Quantifies phantom accuracy — 20% magnitude |
| B-14 | Rethinking Rephrased Samples 2311 | https://doi.org/10.48550/arxiv.2311.04850 | Paper | High | **8-18% HumanEval overlap in RedPajama/StarCoder pretraining**; synthetic GPT-3.5/4 data also contaminated | Pipeline contamination — even synthetic hold-out suspect |
| B-15 | Reliability Gap 2606 (OLMo2) | https://doi.org/10.48550/arxiv.2606.03305 | Paper | High | OLMo2 explicitly trained on GSM8K train split; **detectors fail to flag train vs test/valid correctly** — procedural held-out needed, not metric thresholds | Even ground-truth contaminated split not reliably detected |
| B-16 | Beyond Scale 2607 (1,215-run factorial) | (arXiv 2607.24688) | Paper | High | Larger not consistently win; scale amplifies shortcut; **technique≥scale but non-monotonic** | Not schema-matching specific — general bench |

### Block C — Production AI Handling (Ollama cold-start, prompt/cache, CHOKE, Dagster lifecycle) — 12 rows

| # | Title | URL | Type | Level | Key Claim | Limitation | Date |
|---|---|---|---|---|---|---|---|
| C-1 | Ollama system design (K8s S3 s3fs fuse) | https://markaicode.com/architecture/ollama-system-design-architecture-956/ | Blog | Medium | Stateless OpenAI-compat behind proxy, **S3 s3fs-fuse multi-Pod**, single GPU/Pod single Ollama, **HPA on GPU mem**, readiness probe `/api/generate` pre-warm → **85% GPU util 200 conc <300ms p95 Llama-3.1-8B** | Not peer-reviewed bench; topology pattern |
| C-2 | Qwen3-8B on RTX 4090 Q4_K_M GGUF | https://smeltcore.com/recipes/qwen3-8b-on-rtx-4090-q4-k-m-gguf-via-ollama-or-llama-cpp/ | Blog | Medium | **Q4_K_M 5.03 GB disk, BF16 16.4 GB**; 24GB card 131K YaRN headroom; Q3_K_M ~14GB, FP16 64GB+ sys RAM during load + KV spill | Single consumer recipe; A100 is datacenter ref |
| C-3 | Qwen3 HW requirements | https://llmhardware.io/guides/qwen3-hardware-requirements | Guide | Medium | Q4 5GB@8GB GPU ok; thinking mode without separate model; Ollama ≥0.6 supports `/think` | Guide — not bench |
| C-4 | johal.in retrospective 6-month (Ollama 0.3 vs vLLM 0.4 on A10G 7B) | https://johal.in/retrospective-building-self-hosted-llm-ollama-03-vllm-04 | Blog | Medium | **Ollama Q4 cold 1.2s p99 210ms 4.2GB 18 req/s $0.004/1M; vLLM FP16 cold 4.8s p99 89ms 14.8GB 47 req/s $0.002/1M; vLLM Q4 cold 2.1s p99 112ms 5.1GB 39 req/s $0.003/1M** ($0.12/hr A10G) | Single report; consistent with 5-phase bench |
| C-5 | model-cold-start-bench 5-phase | https://github.laiyagushi.com/JohnScheuer/model-cold-start-bench | Repo | High | **Disk→RAM (Phase1) 61%, RAM→VRAM PCIe (Phase2) 36% at 774M; PCIe fastest-growing; 70B FP16 140GB needs 2×H100 + tensor parallel** before first token | RTX 2070 microbench not A10G/H100 — ratio applies |
| C-6 | CHOKE Findings-EMNLP 792 + arXiv 2502 | https://aclanthology.org/2025.findings-emnlp.792.pdf | Paper | High | **CHOKE: consistently correct but trivial perturbation → high-certainty hallucination**; **robust to temp/top2-gap/semantic-entropy**; 9-43% prevalence (HACK) | QA/EM domain — not schema-matching confidence specifically, but mechanism transfers |
| C-7 | HACK 2510 (CM 9-43%) + DECK 2606 blind spot | https://arxiv.org/html/2510.24222v1 | Paper | High | **CM 9-43% across models; 13/15 GPT-4o scorers <0.5 AUROC; P(True) 0.675→0.331 inversion; Llama-3-8B last-layer probe fails 0.44 CI [0.35,0.53]** | SelfAware knowledge-gap regime — maps to `__EMPTY_3` / coded abbrev columns |
| C-8 | LLMs Hallucinate with Certainty Despite Knowing Answer (CHOKE html) | https://arxiv.org/html/2502.12964v2 | Paper | High | Same lineage — medical/law certainty-proxy warning; high consistency across prompts vs other hallucinations | Same — complements C-6 |
| C-9 | Calibrating Verbal Uncertainty probe | https://aclanthology.org/2025.emnlp-main.187.pdf | Paper | High | **Linear uncertainty probe on hidden states (last token of question, multi-layer)** vs multi-sample auxiliary model — cheaper calibration | Preprint cost-efficiency claim |
| C-10 | Anchored Confabulation 2604 (k=1 vs k=3) | https://arxiv.org/html/2604.25931v1 | Paper | High | **Partial evidence non-monotonically amplifies: k=1 amplifies confident hallucination before k=3 eliminates; causal AUC; formal routing** — predicts retrieval-on-small-target poisoning | Not schema-matching task — but retrieval mechanism identical |
| C-11 | Resilient LLM-DBMS pipelines IEEE CASCON 2025 | https://doi.org/10.1109/cascon66301.2025.00113 | Paper | High | Fault-injection: **p95 −18% (1403→1157ms), p99 −18% (1633→1336ms), cost halved, 96.5→100% avail, blocks 13 unsafe DDL/DML; tradeoff −17% quality on fallback** | Quality drop real — fallback not free |
| C-12 | Dagster model lifecycle (Pipes + asset checks + resources + discussions) | https://docs.dagster.io/integrations/external-pipelines/using-dagster-pipes/create-subprocess-asset | Docs/Issue | High | **PipesSubprocessClient per-invocation spawn; blocking=True asset check gates downstream; yield_for_execution warm handle; forkserver vs spawn 12s→5s; 3.11 preload ok / 3.12 broken (#30893); standing gRPC avoids ECS (#7332); AutomationCondition.on_cron tick** | L3 official — but lifecycle pattern must be composed, not copy-paste |


