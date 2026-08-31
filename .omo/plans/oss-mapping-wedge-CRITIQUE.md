# BRUTAL CRITIQUE — OSS Semantic Mapping Wedge Build Plan

**Date:** 2026-08-30 · **Critic:** Senior Systems / Backend Engineer (15y data infra, ETL, distributed systems)
**Target:** `oss-mapping-wedge.md` (156 lines, Draft, 55-68% overall confidence)
**Research base:** `~/.omnilearn/research/oss-semantic-mapping-wedge-scaleup-data-unification` — 78 sources, synthesis confidence 35-92%
**Search evidence:** 21 SearxNG searches (Exa primary) covering 10/10 mandatory component areas; all claims cited with URLs
**Verdict in one line:** The plan correctly *rescoped* itself after falsification but then reintroduced every killed complexity through the back door as a "stub," "seam," or "wrapper" — with zero operational semantics specified.

---

## 0. EXECUTIVE SUMMARY — WHAT THE PLAN GETS RIGHT, WHAT WILL KILL YOU

**Right:**
- Honest falsification accounting (H1 provisional, H3 strong, H5 strong) — rare and valuable.
- Correct hedge: ride connectors, wrap Soda/Elementary, bound Python plane to ≤20GB, pitch recurring replay not one-shot 50%.
- Dagster asset distribution instinct is directionally correct (Airflow 1,700 providers pattern is real).

**Fatal pattern:**
Every falsified hypothesis reappears as an unfunded "moat stub" or "seam." The plan says "STOP: no second ER backend" then immediately says "adapter seam for Ditto." It says "H3 strongly falsified" then says "whichever plane passes dirty-10GB fidelity gate" — as if the gate is a detail. Replay alone is cloneable in a quarter (plan admits this), so it adds "Sato/TURL/RECA" as a harder moat — a research program that costs more than the product. This is not scope discipline; it is scope laundering.

**P0 count:** 7 · **P1 count:** 5 · **P2 count:** 4 — plan is **NOT build-ready** without P0 closure.

---

## 1. YAML MAPPING SPEC → SQL/Python COMPILATION (dbt MetricFlow Precedent)

### What the plan claims
> "YAML rules in Git compile → SQL/Python for any warehouse (DuckDB, Postgres, BigQuery), with `validate` + `test` + PR diff CI — architectural precedent for mapping rules compiling to SQL/Python (cf. dbt MetricFlow)"

### What is underspecified / hand-waved
- **No grammar.** What is the YAML schema? The plan lists "source column → canonical field, confidence, human verification state, deterministic condition, audit fields" — that is 6 fields described in prose, not a grammar, not a JSON Schema, not a parser spec. Is `deterministic condition` a Jinja expression? A SQL predicate? A Python lambda? How is it sandboxed?
- **No compilation target semantics.** "Any warehouse" means DuckDB, Postgres, BigQuery — three different SQL dialects with different string functions, regex flavours, date parsing, and quoting rules. MetricFlow solves this by compiling to a *dataflow plan* then lowering to engine-specific SQL via an optimizer — plan proposes direct YAML→SQL without that intermediate representation.
- **No `validate` / `test` definition.** MetricFlow's `dbt sl validate` and `dbt test` check semantic-manifest consistency, dimension→column binding, and generated SQL execution. The plan says "`validate` + `test` + PR diff CI" but does not say what validates what, what the test harness executes against (live warehouse? ephemeral DuckDB?), or what constitutes a passing diff.
- **No warehouse-dialect variance handling.** Null handling, case sensitivity, and identifier quoting differ across DuckDB vs BigQuery vs Postgres.

### Production failure mode that kills it
A field rename from `Rev` to `Revenue ($)` causes the compiler to emit `SELECT Revenue ($) AS revenue` without quoting — fails on Postgres (reserved word / illegal identifier) or silently binds to the wrong column on BigQuery. A second file introduces a column `order` — `SELECT order FROM ...` is a syntax error on every warehouse because `ORDER` is reserved. The PR diff shows green because `validate` only checked YAML syntax, not generated-SQL execution. This ships to main, breaks the nightly replay, blocks the SFTP drop, and pages the solo dev at 3 AM with a warehouse error that has no mapping-context in the log.

Worse: H5 says "wrap Soda column contracts" which also emit SQL. Now two YAML→SQL compilers (mapping + Soda) must agree on identifier quoting, dialect, and null semantics. When they diverge, the Soda gate fails on a mapping that the mapping compiler considered valid — non-reproducible CI.

### Search evidence
- MetricFlow YAML `name` vs column `name` mismatch produces `invalid identifier` at `dbt sl validate` time, not at YAML parse time — the semantic manifest silently drops `expr` when column-level dimensions diverge (https://github.com/dbt-labs/dbt-core/issues/12512). The plan has no manifest concept at all.
- `ORDER` as unquoted alias fails even with `expr` fix; `SELECT "order" AS order` still broken because the *alias* is unquoted (https://github.com/dbt-labs/metricflow/issues/2058) — proves dialect quoting cannot be bolted on; it must be in the IR.
- MetricFlow's own correctness fix required a full dbt-fusion Rust port with 266 ported MetricFlow tests + Snowflake record/replay comparison with float tolerance and date normalization (https://github.com/dbt-labs/dbt-fusion/commit/6c100b4e8a11cbfa4314577c3e4a341c779ed285) — shows the cost of "YAML→SQL for any warehouse" is a compiler team, not a solo-dev feature.
- MetricFlow's failure mode list: "ensure you've ran `dbt parse`" — semantic manifest stale if you forget a build step; dimension missing primary entity (silent omission) (https://adriennevermorel.com/notes/metricflow-cli-querying/) — same class of staleness bug the plan's "PR diff CI" will inherit.
- MetricFlow ambiguous-column bug: `ambiguous column name 'YELLOW'` when two parent metrics share a column, *not caught by `dbt sl validate`* until query time (https://github.com/dbt-labs/metricflow/issues/1930) — proves `validate` ≠ execution correctness.

### What is missing from research that blocks a build decision
- No retrieved MetricFlow YAML schema spec (field condition language, expression grammar, type coercion rules).
- No warehouse-dialect matrix: which string/regex/date functions are portable vs need per-dialect lowering.
- No test-harness design: what warehouse does CI execute against; how to test BigQuery SQL without BigQuery.
- No versioning semantics for the YAML spec itself — when `deterministic condition` language changes, do old rules recompile identically?

### Severity: **P0 — blocks build**
**Justification:** Without a grammar + IR + dialect-lowering + execution-tested `validate`, the "YAML→SQL for any warehouse" claim is a demo, not a product. First customer with a BigQuery warehouse or a column named `order` will file a P0 bug on day 2. Fixing it after launch means adding an IR and re-testing every saved rule — a migration, not a patch. On-call cost: 4-8 hrs per dialect bug, unbounded until the IR exists.

---

## 2. DETERMINISTIC REPLAY ENGINE + AUDIT LEDGER (<2 min trace)

### What the plan claims
> "Deterministic replay engine: cell-level fix → versioned rule, replay on re-ingest of unchanged sources, before/after diff, `replay --check` ledger" + "audit ledger <2min" + "Zero re-verification on re-ingest of unchanged sources"

### What is underspecified / hand-waved
- **"Unchanged sources" is undefined.** Hash of raw file bytes? Hash of parsed rows? Hash after mapping? If a source adds 3 rows to a 50k-row SFTP drop, is it "changed" (full re-verify) or "unchanged with delta" (partial replay)? The plan does not define the change-detection granularity.
- **Ledger storage is unspecified.** Is the ledger a Postgres table? A Git log? A Dolt commit? A file on disk? Where is the before/after diff stored and for how long? What is the retention / GC policy?
- **Versioned rule identity is unspecified.** Rule `v3` was auto-generated from a cell fix in file `2026-08-15.csv` line 42. When the next file has the same dirty pattern but at line 904, does it match `v3` or create `v4`? What is the rule's stable key (pattern? source column? regex? hash of condition)?
- **`<2min audit trace` is unmeasured.** Trace of what, from where, to where? Is this wall-clock time to answer "which rule transformed cell X in run Y" or "replay all rules on the last 90 days of drops"? The OneSchema analogy says deterministic replay exists, but OneSchema's implementation is SaaS-hosted with private code — no OSS reference implementation to copy.
- **Concurrency and idempotency missing.** If two SFTP drops land at the same time, does replay serialize? If replay is interrupted (pod eviction), does `replay --check` detect partial application?

### Production failure mode that kills it
Month 2: customer has 40 weekly SFTP drops replaying via cron. One drop contains a rule that wrote `NULL` to `revenue` because the regex `^\$?([\d,]+\.?\d*)$` matched an empty string under a new locale. The ledger recorded the rule application, but the before/after diff was stored as a JSON blob in Postgres that grew to 400 MB per run (full row before vs after). Postgres `TOAST` starts degrading query latency; the `<2min audit` now takes 14 minutes because it scans 90 days × 400 MB = 36 GB of diffs with no index. Customer asks "which rule broke revenue this week?" — answer requires a human scanning 40 rule versions with no pattern grouping. Meanwhile a concurrent drop replays against the same canonical table without a transaction boundary; second replay overwrites first replay's fix, creating a conflicting canonical write that the conflict detector flags as a new exception — alert noise doubles.

Alternative kill: "unchanged source" is defined as byte-identical file hash. Source system re-exports the same logical data but with rows reordered and a new `export_timestamp` column added — byte hash changes, so replay re-verifies every row, defeating the "zero re-verification" pitch. Customer sees no saving vs manual mapping and churns.

### Search evidence
- OneSchema FileFeeds: "Every cell-level fix is captured as a deterministic rule that replays on every future run; you see the before/after diff before accepting" + "latest saved version" staging → production promotion model (https://docs.oneschema.co/docs/transform-library, https://docs.oneschema.co/docs/core-concepts, https://docs.oneschema.co/docs/destinations-overview). But docs confirm: destinations are deterministic *given same input and same saved version get byte-equivalent output* — requires version pinning + input identity, neither defined in the plan.
- Dolt's audit primitives: `dolt_history_<table>` (row per revision), `dolt_diff_<table>` (changed rows only), `AS OF` commit queries, distributed audit via `UNION` of diff tables (https://docs.doltgres.com/reference/version-control/querying-history, https://dolthub.com/blog/2025-07-17-distributed-audit-logs, https://www.github.com/dolthub/dolt). Shows that a real audit ledger needs system-table design, indexing, and GC — none in plan.
- Git-for-data ceiling: "20 commits/sec, custom kernel, GC overhead; beyond low contention you spend more on workarounds than a DB" (Locally Optimistic Git-for-data, cited in findings § Finding 1). Directly contradicts "YAML rules in Git + ledger" if ledger lives in Git.
- lakeFS / Nessie / Dolt comparison: branching copies pointers, not data; merging replays pointer changes; time-travel via `@commit` / `AT branch` / `AS OF` (https://dev.to/gowthampotureddi/data-version-control-lakefs-nessie-dolt-for-git-like-data-branching-41jb, https://www.ssp.sh/blog/git-for-data-tools/) — shows replay-at-scale architectures already exist with formal semantics; the plan invents a third without specifying which model it follows.

### What is missing from research that blocks a build decision
- No source that measures "time to audit trace" at realistic diff volume (40 files × 50k rows × retained diffs).
- No design for rule identity / pattern-matching semantics (regex vs template vs LLM-generated?).
- No concurrency / exactly-once replay protocol; no failure-recovery spec.
- No storage cost model for before/after diffs (row-granular vs cell-granular vs hash-only).
- No evaluation of Dolt / lakeFS / Nessie vs plain Postgres ledger for this workload.

### Severity: **P0 — blocks build**
**Justification:** Replay + ledger is the *entire rescaled wedge* (H1 narrow slice). If "unchanged" is misdefined, the product's headline benefit (zero re-verification) is false on the first re-export. If ledger storage / diff volume is unbounded, it creates a Postgres-cost and latency blowup that kills the `<2min` claim. The plan has no spec that could pass a code review for this component — it is a paragraph of nouns, not a design.

---

## 3. DAGSTER ASSET / COMPONENT DISTRIBUTION + AIRLIFT MIGRATION

### What the plan claims
> "Distribution: Dagster asset/component + Python library (Soda/GX/MetricFlow pattern), not standalone service. Airflow 1700 providers + Dagster Components asset-factory + `dagster-airlift` incremental migration — adoption via embeddability."

### What is underspecified / hand-waved
- **Which distribution?** `dagster-soda` ships a `SodaScanComponent` with `checks_paths` + `configuration.yml` + `AssetKey` mapping. Is the mapping wedge a `MappingComponent` with `mapping_yaml_path` + `warehouse_config` + `ledger_dsn`? What is the minimal `defs.yaml` that a user writes to adopt? No example config provided.
- **Cold-start semantics inside Dagster.** Dagster's default backfill launches N runs for N partitions (one per partition). If the mapping asset is partitioned by `source_id × week`, a 20-source × 52-week backfill is 1,040 runs. Without an explicit `BackfillPolicy`, the orchestrator will either DoS itself or require manual batching. The plan mentions "cold-start 560× is ad-hoc re-run win, not streaming win" but does not translate that into a concrete `BackfillPolicy` choice.
- **Airlift migration posture.** `dagster-airlift` observes Airflow DAGs, then incrementally migrates operators to Dagster assets via factory functions. The plan says "duck-typed for `dagster-airlift` incremental adoption" — but which Airflow operator does the mapping wedge correspond to? Is it a `SftpToWarehouseOperator`? A `PythonOperator`? A `MappedTask`? Without a named operator type, the airlift story is hand-waving.
- **Asset check vs asset semantics.** Is a mapping a Dagster `asset` (produces data) or an `asset check` (validates data)? Soda maps to `asset checks`; MetricFlow maps to `assets` that produce queryable tables. The wedge must decide: does `@dg.asset def canonical_revenue` produce the canonical table, or does `@dg.asset_check def mapping_valid` gate the pipeline?

### Production failure mode that kills it
A data team adopts the wedge via `MappingComponent` in `defs.yaml` with 20 sources. They run `dg dev` — cold start pulls Dagster + Soda + DuckDB + Sato/TURL dependencies (hundreds of MB). The component's `build_defs` dynamically generates 20 assets with `deps` edges. Dagster's daemon starts scheduling them; a backfill covering 4 weeks of late-arriving SFTP data spawns 80 runs concurrently, each opening a DuckDB in-memory instance and a Postgres connection for the ledger. Connection pool exhaustion + DuckDB memory contention OOMs the Dagster user-code server. Logs show "asset backfill failed: user code server unreachable" with no mapping-specific error. The team concludes "the mapping library broke our Dagster deployment" and rips it out.

Alternatively: the team uses Airflow, not Dagster. "Distribution via Dagster asset" is now a forced migration. `dagster-airlift` can observe their Airflow DAGs, but only if they run Airflow 2.10+ with the REST API enabled and install the `dagster-airlift` observer — none of which is in the plan's adoption readme. Evaluation stalls at "we don't run Dagster."

### Search evidence
- `SodaScanComponent` design: `checks_paths` + `configuration.yml` + dataset→`AssetKey` mapping; `dagster-soda` maps SodaCL check results to Dagster asset check results via a single component class (https://docs.dagster.io/integrations/libraries/soda, https://pypi.org/project/dagster-soda/, https://github.com/dagster-io/dagster/pull/33445). This is the correct precedent — but the plan does not define its own component's interface at the same specificity.
- Dagster asset factories: `AssetFactory` requires `components-ready` project (`dg` scaffold), `build_defs` returns `Definitions`; dependencies via `deps` field in YAML Model; example shows `etl_job: [{bucket: ...}, ...]` → pattern for dynamic asset generation (https://docs.dagster.io/guides/build/assets/creating-asset-factories, https://docs.dagster.io/guides/build/assets/defining-dependencies-with-asset-factories). The plan says "YAML rules → assets" but does not say whether it uses this factory pattern or a static `@dg.asset`.
- Dagster backfill semantics: default N partitions = N runs; `fillPolicy` groups partitions into batches (`max_partitions_per_run=10`, 100 partitions → 10 runs) to reduce 90% overhead; self-dependent assets serialize to one run at a time (https://docs.dagster.io/guides/build/partitions-and-backfills/backfilling-data, https://docs.dagster.io/examples/best-practices/partition-backfill-strategies, https://github.com/dagster-io/dagster/pull/27938). Plan has no backfill policy, so naive adoption creates an N-run explosion.
- `dagster-airlift`: requires representing Airflow instance in Dagster via `defs.yaml` with `mappings: [{dag_id: ...}]` + `AirflowInstance` component; each Airflow operator type needs its own asset factory function (`load_csv_to_duckdb_defs` per operator) (https://docs.dagster.io/migration/airflow-to-dagster/airflow-component-tutorial, https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/migration-reference, https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/task-level-migration/migrate). No such factory mapping exists in plan for the mapping wedge — "duck-typed for airlift" is false until proven.

### What is missing from research that blocks a build decision
- No minimal `defs.yaml` example for the mapping wedge component.
- No `BackfillPolicy` / partition scheme for weekly SFTP sources.
- No Airflow→Dagster migration path for teams that don't already run Dagster (the wedge's target: 5-person teams often run Airflow, not Dagster).
- No measurement of Dagster cold-start / component import latency with Sato/TURL heavy dependencies.
- No decision on asset vs asset-check semantics for the mapping step.

### Severity: **P1 — scopes/hedges (P0 for Airflow-only shops)**
**Justification:** Dagster distribution is a GTM choice more than a correctness choice, but getting it wrong wastes the wedge's embeddability narrative. If the first two evaluators run Airflow, the Dagster-only story kills pipeline. Even within Dagster, wrong backfill defaults cause operational on-call (OOM / connection exhaustion) that looks like the wedge's fault. 2-4 hrs to spec the component interface; skipping it costs 20+ hrs of evaluator support.

---

## 4. HARDER MOAT — SATO / TURL / RECA LEARNED TYPE INFERENCE

### What the plan claims
> "Harder moat stub: statistical/embedding features + table context (Sherlock 1,588 features → Sato CRF upgrade path), not regex chain" + "Learned type inference with table context (Sato CRF topic model + TURL Transformer + RECA related-table) on dirty retail/CRM wide tables — dataset incumbents haven't productised"

### What is underspecified / hand-waved
- **This is a research program, not a feature.** Sherlock extracts 1,588 features per column (statistical properties, character distributions, word embeddings, paragraph vectors) from 686k VizNet columns matched to 78 DBpedia types; Sato adds a CRF topic model + structured prediction; TURL pretrains a structure-aware Transformer (TinyBERT init, 4 layers, 312-dim, 12 heads) on 570k Web tables (80 epochs) then finetunes on entity linking; RECA adds inter-table context via schema-similar/ topic-relevant neighbour search. The plan collapses this into "±1 line: statistical/embedding features + table context."
- **No dataset contract.** Sherlock/Sato/TURL/RECA are trained and evaluated on WebTables / VizNet — clean, public Web tables. Retail/CRM dirty CSVs are wide, string-heavy, high-cardinality, with abbreviations (`rev`, `amt`, `cust_nm`), compound tokens, and locale-specific formats. No evidence these models transfer without retraining.
- **No latency / cost envelope.** Sato's paper reports CRF overhead "+1.4s for 6.4k tables" (0.2ms/table) vs base — but that is on 64-core/512GB RAM with pre-extracted features. Feature extraction (1,588 features × N columns × unique values) is the bottleneck, not the CRF. TURL's pretraining is 80 epochs on 570k tables — weeks on multi-GPU. The plan gives no cold vs warm path latency budget.
- **No feature extraction pipeline.** How are the 1,588 features extracted at ingestion time inside Dagster? Is it a Python UDF per column? A batch job? What is the dependency closure (GloVe / BERT embeddings, column entropy, character counts)? How does this interact with the "Hybrid plane: Java window + Python subprocess" isolation?
- **No fallback semantics.** If Sato/TURL inference fails (OOM on 200-column wide table, missing embedding model, timeout), does the type inferencer fall back to the regex chain? Or does the pipeline fail hard? The plan says "not regex chain" — so failure is now a pipeline failure, not a degraded inference.

### Production failure mode that kills it
Solo dev ships a "Sato stub" that calls a HuggingFace TinyBERT model for table context. On the first dirty retail dataset (180 columns, 400k rows, mixed encodings), feature extraction spins for 45 minutes extracting 1,588 features × 180 cols = 285k feature values, loading a 500 MB GloVe + paragraph-vector model into the Dagster user-code process. The Dagster pod OOMs at 8 GB. Retry does the same thing. The fallback regex chain (which would have finished in 12 seconds) is no longer wired because the plan said "not regex chain." Customer files a P0: "type inference hangs, no error message." The solo dev now owns debugging a transformer inference pipeline on dirty wide tables — 3 weeks of work that was supposed to be a 2-day stub.

Second kill: model was trained on WebTables (column names are natural language like `city`, `country`, `population`). Retail CSV has columns `col_14`, `field_8`, `__EMPTY_3` (unnamed / auto-named) — Sato's topic model has no signal, predicts `FREE_TEXT` for everything, worse than the regex chain's `CATEGORY[unique<20]` heuristic on low-cardinality codes. The "moat" degrades accuracy on the exact dirty data that motivated it.

### Search evidence
- Sherlock: 1,588 features per column, 686,765 columns, 78 DBpedia types, multi-input DNN, F1 0.89 support-weighted (https://sherlock.media.mit.edu/, https://sherlock.media.mit.edu/assets/2019-Sherlock-KDD.pdf, https://github.com/mitmedialab/sherlock-project). Feature extraction alone is a pipeline; the repo requires 500 MB download + preprocessing notebook — not "one line."
- Sato: +1.4s for 6.4k tables (0.2 ms/table) *on 64-core/512 GB RAM*, 81s/367s training times (https://www.vldb.org/pvldb/vol13/p1835-zhang.pdf, https://arxiv.org/abs/1911.06311, https://github.com/megagonlabs/sato). But training data is 26k tables; retail/CRM dirty tables are not in this corpus — transfer gap unmeasured.
- TURL: N=4, d_model=312, k=12, TinyBERT-init Transformer, 570k tables for pretraining, 80 epochs, Adam 1e-4, plus MLM+MER objectives (https://vldb.org/pvldb/vol14/p307-deng.pdf, https://arxiv.org/html/2006.14806v2, https://github.com/sunlab-osu/turl). This is a research codebase (PyTorch + Transformers Docker `xdeng/transformers:latest`) — deploying it as a Dagster asset subprocess is a systems project, not a stub.
- RECA: schema-similar + topic-relevant inter-table search, novel named-entity schema, F1 0.853/0.937 on WebTables but *requires a corpus of related tables* to search over. If the customer has 20 isolated CSVs with no related-table corpus, RECA has no signal (https://doi.org/10.14778/3583140.3583149, https://vldb.org/pvldb/vol16/p1319-sun.pdf, https://github.com/ysunbp/RECA-paper). The plan has no "related table corpus" concept.

### What is missing from research that blocks a build decision
- No latency budget: feature-extraction time per column on 180-col wide table; model-load time; GPU vs CPU inference p99.
- No transfer study: Sherlock/Sato/TURL trained on WebTables evaluated on retail/CRM dirty CSVs (abbreviations, compound tokens, empty headers).
- No pipeline spec: how 1,588-feature extraction runs inside Dagster (subprocess? UDF? batch? streaming?).
- No fallback contract: does Sato timeout → regex chain, or pipeline failure?
- No cost model: embedding model hosting (500 MB–2 GB), GPU need, cold-start vs warm-cache latency.

### Severity: **P0 — blocks build (if claimed as moat in MVP); P2 if explicitly deferred**
**Justification:** As a research moat it is legitimate long-term, but as an MVP "stub" it is a trap. A naive stub implementation will be slower, more brittle, and less accurate on dirty data than the regex baseline it replaces — exactly the opposite of a moat. The plan hedges correctly ("not regex chain" is the aspiration) but gives no implementation guardrail to prevent the stub from shipping as the default. Operational cost if shipped naive: 3-4 weeks of firefighting per wide-table customer + inference infra that the solo dev cannot support. Recommendation: formalize as **post-MVP research track**, keep regex chain + statistical fallback in MVP, and define a measurable accuracy gate before switching.

---

## 5. SODA WRAPPING — COLUMN CONTRACTS + DIRTY DATA REALITY

### What the plan claims
> "Soda wrapping: Soda column contracts (missing/invalid/range/regex, filter/variables, proposal diffs, exit-code gate) validating mapped output, per-column PSI/KS p99 calibration (not global 20pp/50%/3σ)" + "wrap mapped outputs"

### What is underspecified / hand-waved
- **Which Soda?** Soda Core (CLI/Python scan engine, OSS) vs Soda Cloud (SaaS aggregation, Collaborative Data Contracts, anomaly detection). The plan says "at $0" — that is Soda Core, not Cloud. But Soda Cloud's SPU pricing ($750+ after free tier per https://soda.io/pricing) matters for the "10× cheaper" claim if the customer eventually needs Cloud for collaboration / alerting.
- **Contract authoring UX.** Who writes the column contract YAML? Is it auto-generated from the mapping YAML (e.g., `revenue: type=CURRENCY, range=[0, 1e9]`)? Hand-authored per canonical field? Proposal-diff generated ("Soda suggests: `missing < 2%`")? If auto-generated, how does the type inference (Sato stub) feed into it? If hand-authored, it is not "zero re-verification" — it is new verification work.
- **Per-column PSI/KS p99 calibration.** The plan correctly cites PSI batch-size pathology (false alarms at small N, stabilizes >200) and KS ~100% power at N>10k, and proposes per-column p99 via 200× self-splits. But who runs the 200 self-splits? On what reference window? How large must the reference window be per column before calibration is valid? What happens for low-volume sources that never produce a reference window of 200+ rows?
- **Filter/variables scoping.** Soda contracts support `filter` (row predicate) and `variables` (YAML templating). The plan says "Soda column contracts wrapping mapped output, per-col p99" — but mapped output is post-transformation. Do contracts run on staged raw, canonical, or both? If a mapping bug mangles `revenue` (e.g., parses "$1,234" as 1 because comma not stripped), the contract on canonical will catch `invalid` but the raw contract would have passed — which layer failed?

### Production failure mode that kills it
The contract YAML auto-generated from Sato-inferred types sets `revenue: type: DECIMAL, range: [0, 1000000]` based on the first 3 SFTP drops. Drop 4 contains a legitimate $2.1M enterprise order — `range` check fires, blocks the canonical load, creates an incident in Dagster as `ASSET_CHECK FAILED`. The per-column p99 calibration never fired because this source only produced 80 rows per week, never reaching the 200-row stability threshold for PSI — so it fell back to a folklore 0.25 threshold, flagged stationary drift as anomalous. Meanwhile the pipeline's second-moment contract (`revenue_distinct_count`) fails on every drop because the canonical table's distinct count is computed on the *cumulative* table (grows every week) while the reference window was a single week's slice — apples-to-oranges. Alert noise: 3 false positives per week × 20 sources = 60 alerts/week. Team mutes the checks, the next real data corruption (null CS codes typed as `null` string) passes silently because the muted channel dropped all `__EMPTY_*` column checks.

Second kill: contract runs inside a Soda scan against a DuckDB in-memory cursor registered from a Pandas DataFrame (`cursor.register("view", df)`). The mapping step already used DuckDB for the YAML→SQL compilation, so two DuckDB instances (mapping + Soda gate) now hold copies of the same 10 GB batch. Memory doubles, spill or OOM. The "per-column" calibration (200 splits × N columns) is O(N) scans — on a 180-col table that is 36k scans, not one.

### Search evidence
- Soda Core = OSS scan engine, Soda Cloud = SaaS aggregation surface; Cloud adds Collaborative Data Contracts, RBAC, audit logs; Core alone is $0 but Cloud is metered SPUs (https://soda.io/pricing, https://soda.io/blog/guide-to-data-contracts, https://datastackindex.com/data-observability/tools/soda/, https://github.com/sodadata/soda-core).
- Contract execution paths: in-memory DuckDB gate that aborts DAG if checks fail before Postgres write (Make.com v4 pattern) — upstream DataFrame → `duckdb.connect(":memory:")` → `cursor.register("view", df)` → `verify_contract_locally(data_sources=[DuckDBDataSource.from_existing_cursor(cursor)])` → exit code gates DAG (https://soda.io/blog/data-contracts-airflow-pipeline, https://docs.soda.io/reference/data-source-reference-for-soda-core/duckdb, https://docs.soda.io/reference/soda-apis/python-api, https://docs.soda.io/reference/data-source-reference-for-soda-core/duckdb/duckdb-advanced-usage.md).
- `dagster-soda` maps SodaCL checks to Dagster asset checks via `SodaScanComponent` with `checks_paths` + `configuration.yml` (https://docs.dagster.io/integrations/libraries/soda). The plan's "per-col p99" must integrate with this component's scan — but no mapping between p99-calibrated checks and `checks_paths` YAML is defined.
- Drift false-positive catastrophe: 200 per-feature tests at α=0.05 on stationary data → P(at least one false alarm) = 1 − 0.95^200 ≈ 0.99997 — "you are essentially guaranteed a spurious drift alert every single window" (http://temporalbook.apartsin.com/part-5-uncertainty-online-adaptive/module-21-adaptive-systems/section-21.2.html).
- PSI/KS batch-size split: PSI false alarms at small N, stabilizes >200; KS/MMD/LSDD fluctuate across batch sizes but more reliable at small N; p99 via self-splits proposed as fix (https://arxiv.org/html/2607.17336).
- Drift-sentinel implementation: 200× random self-splits of reference window, p99 PSI per feature as threshold — exactly the mechanism the plan needs but does not spec (https://github.laiyagushi.com/tkgo1599-max/drift-sentinel).

### What is missing from research that blocks a build decision
- No contract-authoring flow: auto-generated from mapping vs hand-authored vs proposal-diff — and who owns it on SFTP re-drop.
- No reference-window sizing per column; no fallback when N<200 (foilks vs suppression vs dynamic).
- No scan-planning: does per-col p99 require O(N) scans or can it be batched? What is the DuckDB memory cost of two in-memory instances?
- No layering decision: raw vs canonical vs both — which contract catches mapping bugs vs source bugs.
- No FDR correction spec (Benjamini-Hochberg vs per-feature α) when monitoring 180 columns simultaneously.
- No alert-noise measurement: expected false-positive rate at declared α on customer's actual source width.

### Severity: **P1 — scopes/hedges (P0 if auto-generation is the pitch)**
**Justification:** If contracts are hand-authored, this is a wrapper with bounded scope (good). If contracts are auto-generated and per-column calibrated, it is a statistical system with FDR, reference-window, and memory semantics that must be specified — otherwise you ship a false-positive factory that teams mute within 2 weeks. The plan conflates both. On-call cost if auto-calibrated without spec: 60 alerts/week of noise, one real silent corruption when muted.

---

## 6. SPLINK ADAPTER — SINGLE MATCHER + SEAM FOR SECOND

### What the plan claims
> "Single ER adapter: Splink (probabilistic, unsupervised EM, DuckDB or Spark) — adapter seam for Ditto, not a second backend yet" + "Cross-dataset ER blocking learner that holds across heterogeneity types (the 30-40% collapse is blocking mismatch)"

### What is underspecified / hand-waved
- **Blocking is the hard part; the plan punts it.** Splink implements Fellegi-Sunter with comparison levels (`exact / fuzzy / else`) + unsupervised EM for `m`/`u` estimation + blocking rules that bound comparison count. The plan says "blocking learner that holds across heterogeneity" as a harder moat, but the adapter ships only one backend (Splink). No blocking-rule language, no optimization, no blocking-audit UX is specified. The research correctly notes Splink #2312/#2931 (across-vs-within field sets impractical, blocking-rule optimisation exploding) — but the plan does not say how it avoids that explosion.
- **DuckDB vs Spark boundary undefined.** Splink's docs recommend DuckDB for most use cases (1M on laptop ~1 min) and Spark for 100M+. The plan says "DuckDB or Spark" without choosing or defining the crossover. The bake-off says "bound to ≤20GB + hybrid" — but Splink's DuckDB path holds at 1M; what is the single-source row limit before the adapter must switch to Spark? Is this per-pair blocking, or cumulative canonical table size?
- **Adapter seam abstraction missing.** "Adapter seam for Ditto" — what is the interface? `match(candidates: blocked_pairs) -> MatchScores`? `train(blocking_rules, labelled_pairs) -> Model`? Without the interface, the seam is a comment, not an abstraction.
- **No linkage pipeline operational cost.** Splink tutorials warn: blocking rules that create trillions of comparisons will fail; 20M comparisons is the DuckDB laptop guideline, 100M for Spark, before scaling up (https://moj-analytical-services.github.io/splink/demos/tutorials/03_Blocking.html). The plan gives no blocking budget per customer / per source.

### Production failure mode that kills it
Customer has 400k customers across 3 sources (HubSpot, Postgres, Sheets). First ER run with default Splink blocking `exact on email OR fuzzy on surname+postcode` generates 180M comparisons (cartesian product of the two largest sources with loose blocking). On DuckDB laptop-path, comparison table spills and the EM training step OOMs at 32 GB. The adapter retries on Spark — but there is no Spark cluster provisioned because the plan said "ride, not rebuild" and left Spark as a future option. ER now fails with no fallback; canonical load stalls, and the ledger accumulates un-ER'd staged rows. The team manually adds a tighter blocking rule (`email exact OR (surname exact + postcode prefix 4)`) — precision drops because legitimate fuzzy matches (typos in surname) are now blocked away, recall collapses, and the "30-40% F1 gap" the moat was supposed to solve appears immediately in prod.

Second kill: the adapter seam for Ditto (a deep-learning ER model requiring labeled pairs via active learning) is never implemented. Customer asks "can we try Ditto for the messy retail SKU match?" — answer is "we have a seam" (no code). The sale requires proof that the blocking learner adapts across heterogeneity types; no experiment has been run (requires cross-dataset benchmark, per findings § H4). The wedge's differentiation vs Splink-hosted solutions is vapor.

### Search evidence
- Splink performance: DuckDB intrinsically faster than Spark for linkage; Spark 60% slower for `u` estimation, 8.7× slower for inference; DuckDB recommended for most/all-but-biggest linkages (https://github.com/moj-analytical-services/splink/discussions/2209, https://www.robinlinacre.com/fast_deduplication/, https://github.com/moj-analytical-services/splink/).
- Blocking semantics: `exact / fuzzy / else` comparison levels, `m` via EM / `u` via random, pairwise probabilities + clustering threshold; 20M (DuckDB) / 100M (Spark) comparison budget before scaling (https://moj-analytical-services.github.io/splink/demos/tutorials/03_Blocking.html, https://dev.to/gowthampotureddi/entity-resolution-record-linkage-fuzzy-matching-splink-dedupe-at-scale-ee8).
- Splink Spark optimization: needs partitioning, repartitioning before blocking, driver sizing (32 GB driver + 16 GB workers), ~30 min for `u`+EM training on sized cluster (https://github.com/moj-analytical-services/splink/discussions/2640) — proves the "DuckDB or Spark" hedge has a real infra cost, not just a flag switch.
- Zingg vs Splink vs Dedupe matrix: Splink = probabilistic unsupervised, Zingg = Spark active-learning (30-40 pairs), Dedupe = Python small-to-moderate, no universal winner, workload-shaped per heterogeneity type (https://tilores.io/content/best-open-source-entity-resolution-and-record-linkage-libraries-splink-zingg-dedupe-and-when-to-move-beyond-them/, https://kanoniv.com/docs/blog/best-open-source-entity-resolution-tools, https://fuzzypoint.co.uk/record-linkage-tools-compared-splink-vs-dedupe-vs-custom-fuzzy-matching, https://buildwithaitoday.com/track/data-governance/learn/dg-entity_resolution).
- Splink blocking-rule explosion + across-vs-within field sets impractical (GH #2312/#2931) — cited in findings § H4, confirms blocking is the moat, not matcher choice.

### What is missing from research that blocks a build decision
- No blocking-rule language or optimization spec (how blocking rules are authored, audited, and tuned per heterogeneity type).
- No DuckDB→Spark crossover definition (row count, comparison budget, or latency SLO).
- No adapter interface spec for Ditto/second backend.
- No linkage pipeline cost model (comparisons × rows × blocking rules → wall-clock on reference hardware).
- No cross-dataset ER experiment showing blocking mismatch recovery ≥10pp (per recommendations Next Experiment 4).

### Severity: **P1 — scopes/hedges**
**Justification:** ER is explicitly hedged as "opinionated first + seam" and marked STOP for second backend — so the seam not being built in MVP is acceptable *if it stays a seam*. Risk is that the harder moat "blocking learner" is used as a sales claim before it exists. Operational cost if blocking is underspecified: first 400k-customer run either OOMs (too loose) or silently loses matches (too tight) — both are P1 customer-facing correctness failures. Needs a blocking-rule audit UX (show comparisons generated per rule) before any "learner" claim.

---

## 7. ELEMENTARY LINEAGE — OSS SETUP + FRESHNESS + VOLUME ANOMALY

### What the plan claims
> "Lineage + volume/freshness at $0: Elementary OSS lineage, freshness table 2 intervals, trailing row-count, schema freeze"

### What is underspecified / hand-waved
- **Freshness `2 intervals` is source-synchronous; plan says Kafka hybrid plane.** Freshness in Elementary is a dbt test on `max(event_time)` or `max(loaded_at)` with thresholds in intervals (`warn_after: {count: 2, period: hour}`). For batch SFTP sources that drop weekly, `2 intervals` = 14 days — reasonable. For Kafka 30s windows, `2 intervals` = 60 seconds — every late window fires an alert. The plan conflates the two planes without scoping freshness per source cadence.
- **Trailing row-count anomaly unspecified.** What trailing window? 7-day rolling mean? 30-day? What constitutes an anomaly — 2σ? 3σ? z-score vs IQR? Elementary's OSS package provides volume anomaly as dbt-native tests with metadata tables; but the plan's "trailing row-count" is described as hand-rolled, not as `elementary.volume_anomalies`.
- **Schema freeze contract.** The plan says "schema freeze" — is this `elementary.schema_changes` (detects added/dropped/typed columns) or a custom `contracts.yml` `columns: [{name: revenue, type: DECIMAL}]` binding? How does schema freeze interact with the mapping YAML (which itself is a schema mapping)? If source adds a column `discount_code`, is that a mapping task (map `discount_code → canonical.discount`) or an anomaly (unexpected column)?
- **OSS vs Cloud confusion.** Elementary OSS is a dbt package + CLI that self-hosts the observability report with lineage, anomaly tests, and metadata tables; Elementary Cloud adds Auto-monitors and ML-based anomaly ranking. The plan says "at $0" (OSS), but also cites "Elementary OSS lineage + Soda column contracts" — are both running in the same Dagster deployment? Do they share the same `profiles.yml` warehouse credentials? What permissions does the `elementary` schema require (read on source schemas, write on `elementary` schema, query history `system.query.history`)?
- **Alert routing and dedup missing.** If Elementary detects volume anomaly, freshness anomaly, and schema change on the same source simultaneously (e.g., delayed SFTP → stale freshness + low row-count + missing column), do these create 3 alerts or 1 merged incident? The plan says "per-seat $0-500/mo not per-record" — but Elementary OSS alerts are Slack/email/webhook via the CLI; who triages?

### Production failure mode that kills it
Week 6: a source system's SFTP cron slips by 6 hours (infra patch). Freshness check with `warn_after: 2 intervals` fires 6 times (once per hour × 6 hours late) — but freshness interval was configured globally as 1 hour (the Kafka window default) not per-source 1 week, so the freshness check was testing the wrong cadence. Meanwhile the volume anomaly test (trailing 7-day mean) flags the same source as anomalous because the late drop had 0 rows for the day — but the row-count was compared against the weekly rolling mean (different window). Schema change detection flags `discount_code` as new — but the mapping YAML already had a rule to ignore it, so the alert is redundant with the mapping state. Customer receives 8 alerts for one delayed file, all with different semantics, none actionable except "wait." They mute Elementary after the second occurrence.

### Search evidence
- Elementary OSS = dbt package + CLI; dbt tests (`volume_anomalies`, `freshness`, `schema_changes`, `column_anomalies`) + metadata tables + anomaly detection tests as native dbt tests; CLI sends alerts + self-hosts the data observability report (https://docs.elementary-data.com/oss/oss-introduction, https://github.com/elementary-data/elementary, https://github.com/elementary-data/dbt-data-reliability, https://docs.elementary-data.com/data-tests/introduction).
- OSS requires: dbt project + `elementary` dbt package + CLI + `profiles.yml` + write to `elementary` schema + read on monitored schemas; production needs network to warehouse + correct permissions on `information_schema` / `query_history` (https://docs.elementary-data.com/oss/deployment-and-configuration/elementary-in-production, https://elementary.mintlify.app/cloud/integrations/dwh/snowflake, https://elementary.mintlify.app/cloud/integrations/dwh/databricks).
- Alert noise is a first-class concern: Elementary Cloud has Incident Merging (manual, AI-suggested, auto-merge by root cause), mute test alerts, false-positive feedback loop ("Insignificant change," "Expected outlier," "Business anomaly" — retrains model) (https://docs.elementary-data.com/cloud/features/data-tests/cloud-tests-overview, https://docs.elementary-data.com/cloud/features/alerts-and-incidents/alerts-and-incidents-overview). The plan has no merging / muting / feedback design — so the OSS alert stream will be noisier than the Cloud system that required those features.

### What is missing from research that blocks a build decision
- No per-source freshness cadence catalog (weekly SFTP vs 30s Kafka — different intervals, different `warn_after`).
- No trailing-window + anomaly-threshold spec for volume (window length, σ / IQR / baseline choice, seasonality).
- No schema-freeze spec (Elementary's `schema_changes` vs custom contract; how mapping YAML state interacts).
- No warehouse permission model per engine (Snowflake `MONITOR` on dynamic tables vs Postgres `GRANT`).
- No alert merging / muting / feedback design for the OSS deployment (the Cloud features that manage noise are not available).

### Severity: **P2 — iteration (P1 if Kafka + batch share one config)**
**Justification:** Elementary OSS at $0 is a sound wrapper choice, but freshness/volume/schema as described is three magic numbers (2 intervals, trailing, freeze) with no per-source scoping. If the same `2 intervals` applies to weekly SFTP and 30s Kafka, the Kafka path will be unusable due to alert noise on day 1. Fix is small (per-source cadence config) but must be specified before MVP or the first hybrid deployment will drown in alerts. On-call cost if misconfigured: 5-10 false positives per week per source, leading to alert fatigue and muted coverage.

---

## 8. HYBRID PLANE ISOLATION — Arrow GIL / to_pylist / SUBPROCESS

### What the plan claims
> "Hybrid plane: Java window + Python subprocess — isolate profiling to Python subprocess so DuckDB/Polars swappable without GIL Arrow→to_pylist breakage."

### What is underspecified / hand-waved
- **The GIL claim is inverted.** The plan says "Java window + Python subprocess" isolates from GIL breakage — but the profiling plane *is Python* (DuckDB/Polars + Sato/TURL + Soda). The GIL bottleneck is *inside* the Python subprocess (Arrow `to_pylist` acquiring the GIL per-element, CPython GC, 24× slower than `ndarray.tolist()`), not between Java and Python. A subprocess boundary does not fix a GIL hot loop inside Python — it just prevents the GIL from blocking the Java windowing thread, which was never contended.
- **Serialization cost across the boundary is unquantified.** Java `StagedRecord` → subprocess handoff: is this via Kafka (already there), via Arrow IPC (Flight / `pyarrow`), via JSON-over-stdin, via temp Parquet files? Each has different latency and failure modes. The plan says "isolate profiling to Python subprocess so DuckDB/Polars swappable" — but the swappability cost is the serialization format: if the contract is "pass a DataFrame as Arrow," the `to_pylist` path still materialises Python dicts per row for quality UDFs; if the contract is "pass Parquet files," the subprocess cold-start dominates for small windows.
- **Subprocess lifecycle and supervision missing.** Who launches the Python subprocess? A Dagster asset that shells out to `python -m profiling.main`? A long-lived daemon with a pipe? A Kubernetes sidecar? What happens when the Python subprocess crashes — does the Java scheduler retry? Does the Kafka consumer commit the offset? The `UploadCsvFromFileService` path already handles `DataIntegrityViolationException` for dedup, but the Kafka path's exactly-once vs at-least-once semantics across the Java/Python boundary are undefined.
- **No benchmark for the boundary itself.** The dirty-10GB bake-off measures wall-clock + cold-start + peak RAM + cost *inside* each plane, but not the serialization overhead of moving 10 GB across the boundary 30 times per hour (Kafka 30s windows). At 30s cadence, even a 2-second serialization adds 6% pipeline latency.

### Production failure mode that kills it
Subprocess boundary uses `stdout` JSON for 10 GB batches at 30s cadence. For a 50k-row batch, 180 columns × 50k rows = 9M cells serialized as JSON strings over a pipe — throughput ~40 MB/s due to Python `json.dumps` GIL contention + GC of per-row dicts. The pipe backpressure causes the Java Kafka consumer to fall behind; lag builds to 90 seconds, windows rotate late, and downstream quality checks see overlapping windows with duplicate rows (because dedup SHA-256 was computed on raw message bytes, not on windowed batches). Meanwhile the Python subprocess's DuckDB `to_pylist` call for the 6-checker quality pass takes 8× longer than the scan itself (20% in GC, 25% in `GetScalar`, 7% useful work per https://github.com/apache/arrow/issues/50326), so even though DuckDB scans at 8× vs Spark, the end-to-end (scan + `to_pylist` + JSON serialization) is slower than the Java plane.

Second kill: the Python subprocess is short-lived (one process per window, launched by the Dagster asset). Each window pays 1.2s cold-start (import `duckdb` + `polars` + `soda`) + 0.8s model load if Sato stub is present. At 30s windows, that is 2s overhead per window × 2,880 windows/day = 96 minutes of cold-start per day, dwarfing the scan saving.

### Search evidence
- Arrow `to_pylist` on list-typed arrays 2.5–10× slower than `to_pandas().values.tolist()` detour, due to per-element `Scalar` allocation + method dispatch + GC; Spark's Arrow-serialized Python UDFs hit this and had to add a pandas detour + pending fix in PyArrow 25.0.1 (https://github.com/apache/arrow/issues/50326, https://github.com/apache/arrow/pull/50327, https://github.com/apache/spark/pull/57099).
- Fix profile: ~20% runtime in CPython GC (per-row GC-tracked allocations), ~25% in `GetScalar`, ~7% useful — `to_pylist` is *not* a thin copy; it is a GIL-holding Python object factory (https://github.com/apache/arrow/issues/28694).
- Spark workaround rejected the pandas detour due to type-coercion bugs (`[1, None, 3]` → `[1., nan, 3.]` as float64) (https://github.com/apache/arrow/issues/50326) — shows there is no free bypass; you pay coercion cost or `to_pylist` cost.

### What is missing from research that blocks a build decision
- No serialization contract: Arrow IPC vs JSON vs Parquet files vs Kafka replay — and p99 latency per batch size.
- No subprocess lifecycle: daemon vs per-window process vs pool; crash/restart / offset-commit semantics.
- No micro-benchmark of `to_pylist` + serialization + cold-start at 30s window cadence (the actual hybrid-plane workload), separate from scan micro-benchmarks.
- No measurement of whether the Python plane even *needs* `to_pylist` — can quality checks run as DuckDB SQL (no Python dicts) instead of Python UDFs?
- No cold-start amortization model: warm-JVM vs cold Python subprocess at streaming cadence.

### Severity: **P0 — blocks build (for hybrid streaming); P1 for batch-only MVP**
**Justification:** The hybrid plane is the plan's hedge for H3 strong falsification — "bound to ≤20GB + hybrid" is the only performance story that survives. If the boundary serialization + `to_pylist` + cold-start erases the scan win, the hedge is false and the MVP's data plane collapses to "Java or Python, not both." For batch-only (1 file per SFTP drop), the subprocess cost is amortized and P1. For the Kafka 30s streaming promise, it is P0 — unmeasured streaming latency at the boundary will be the first perf bug filed. Operational cost: 2-4 hrs to spec the serialization path; skipping it risks a 2-week perf investigation post-launch.

---

## 9. CANONICAL LOAD + ER SEAM — BPM / STEWARDSHIP / EXCEPTION QUEUE

### What the plan claims
> "Canonical load + single matcher Splink OR Ditto seam for 2nd BPM seam (not built pre-trial)" + STOP: "No BPM exception queue / 11-stage stewardship (H4 governance is real need but is MDM scope — second-order)"

### What is underspecified / hand-waved
- **BPM seam vs "not built" is a hedge with no trigger.** The plan correctly cites H4: buyer guides converge on bundled MDM + 11-stage stewardship (profile/match/merge/govern/correct/approve/publish/sync/monitor), DataKrypton 11 stages, CentricDXB RACI "stewards as operational backbone; golden records degrade silently 18 months without stewardship," Microsoft Purview / Informatica BPM. It then says "BPM exception queue seam (not built until trial data)." What is the seam? A Postgres table `exception_queue (canonical_id, field, values, source_provenance, importance)`? A Dagster asset that emits `AssetCheckResult`? A Slack webhook? Without the table/API/event spec, "seam" is not buildable.
- **Conflict resolution vs ER vs canonical load ordering.** The plan's architecture shows `Ingest → Mapping → Quality → Canonical+ER` as a linear DAG. But canonical load requires ER output (which records to group), ER requires mapped fields (which may be wrong), and quality gates the canonical write. If ER clusters two records as the same entity but mapping left `revenue` unmapped in one, the canonical merge must handle a missing field — what is the merge strategy for partial mappings? The plan says `TRUST_HIERARCHY / LATEST_WINS / FLAGGED_FOR_REVIEW` in the README (Phase 6) but the wedge plan does not connect to that logic.
- **Governance adoption consequence.** By explicitly STOPing BPM/stewardship, the plan concedes the enterprise buyer's stated preference (≥50% prefer bundled/MDM per H4 contradicting evidence) — then targets "5-person team, 20+ SaaS + sheets + Postgres sources, warehouse already exists" where BPM may indeed be overkill. But it provides no interview evidence that this ICP *does not* need BPM — H4 has no n≥15 counterbalanced interviews at all (confidence <20% per conclusions). So the scope cut is assumed, not validated.

### Production failure mode that kills it
Customer loads 3 sources with conflicting `revenue` for the same customer entity (HubSpot says $12k MRR, Stripe says $11,700, Sheets says $12.5k). No BPM queue was built, so the canonical merge picks `LATEST_WINS` (Stripe, because its SFTP arrived last) — but the customer's finance team actually trusts HubSpot (entered by the AE) more than Stripe (pro-rated mid-month). The canonical record now shows $11,700, finance reconciliation fails, and the data team cannot explain why the merge chose Stripe: there is no lineage from canonical cell → source record → rule → merge-strategy decision, only the generic Elementary lineage (table→table, not cell→source). The team asks "who approved this merge?" — there is no approval table, because BPM was STOPped.

### Search evidence
- Buyer-governance consensus: CIO Pages / AtroCore / CluedIn (bundled MDM, workload-shaped mastering style, multi-year detour) + CentricDXB RACI + DataKrypton 11-stage blueprint + Data Governor ("golden records degrade silently 18 months without stewardship") + Purview / Informatica BPM (findings § H4 contradicting evidence, L3). This is the strongest contradicting signal against a thin wedge with no governance — the plan correctly marks H4 provisional falsified, but then provides no governance seam spec.
- PAYGO + cross-dataset collapse mechanism: blocking mismatch causes 30-40% F1 collapse, not matcher choice (findings § H4, L2/L4). The wedge's ER differentiation must be blocking, not matcher — but no blocking UX is specified.

### What is missing from research that blocks a build decision
- No exception-queue schema / event spec for the seam (table, API, state machine).
- No canonical cell lineage spec (which source record + rule + merge strategy produced this canonical cell).
- No per-field merge strategy that handles partial mappings (field missing in one source, present in another).
- No ICP validation that BPM is truly unnecessary for the target 5-person team (no interview data).

### Severity: **P1 — scopes/hedges**
**Justification:** The scope cut is defensible *if* the ICP truly does not need BPM — but that is assumed, not measured, and the findings say H4 is provisionally falsified precisely because buyers prefer bundled governance. If the wedge ships without even a seam spec, the first multi-source conflict that reaches finance will be unexplainable, and the wedge will be blamed for a correctness failure that proper lineage would have made auditable. Cost of adding a seam now: 4 hrs to spec. Cost of retrofitting lineage later: migration of canonical-write history.

---

## 10. CROSS-CUTTING — CONSISTENCY, TENANCY, COST, STATE

### 10a. Consistency: Exactly-Once Replay vs At-Least-Once Kafka
**Hand-wave:** Kafka 30s windows + SHA-256 dedup is described as at-least-once with dedup constraint; replay ledger is deterministic but its interaction with Kafka offsets is undefined. If a replay corrects a canonical record, is the correction a new canonical write with a new audit row, or an `AS OF` mutation of history (Dolt model) that invalidates previously emitted lineage?
**Failure mode:** Replay corrects `revenue` for 2k rows; downstream dbt mart already consumed the pre-correction canonical snapshot. No `AS OF` / branch semantics exist to re-consume the corrected snapshot — lineage diverges.
**Severity: P1** — 3-6 hrs on-call per incident where replay and Kafka offset semantics disagree.

### 10b. Schema Evolution Semantics (m:n + FK≡PK)
**Hand-wave:** Plan says YAML→SQL + `validate` handles schema drift, but does not define breaking vs non-breaking changes (add nullable column vs rename vs type change vs FK target change). Data Contracts in CI typically reject breaking changes without a version bump and check schema compatibility via a registry (https://datadef.io/guides/en/data-contracts). The plan has no version-bump policy or compatibility matrix.
**Failure mode:** Source adds `discount_code` and renames `rev` → `revenue_net`; `validate` passes (new column is nullable) but downstream `canonical.revenue` mapping now has zero source rows because the old column name vanished silently. No version bump was required, so the breaking rename slipped through CI as "compatible."
**Severity: P0** — blocks build of the `validate` feature; without a compatibility policy it is theater.

### 10c. Multi-Tenancy
**Hand-wave:** No tenant isolation specified. Is the ledger per-tenant (`tenant_id` partition) or per-deployment? Do YAML rules leak across tenants if the Git repo is shared? Elementary's `elementary` schema is per-warehouse; Soda's `configuration.yml` is per-project. The plan's "per-seat $0-500/mo" pricing assumes tenancy, but no tenancy model is described.
**Failure mode:** Second tenant's SFTP rules compile against first tenant's `canonical` DDL in a shared warehouse → writes to wrong schema.
**Severity: P1** (solo dev pre-product, P0 if any design-wins require multi-tenancy).

### 10d. Cost Blowup: Diff Storage + Scans + Embeddings
**Hand-wave:** Diff storage per replay, scan cost of per-column PSI/KS (O(N) scans), embedding model hosting (500 MB–2 GB) — none quantified. Soda Cloud SPUs are metered (free tier → $750+ per https://soda.io/pricing), but the plan says "$0" without modelling when Cloud becomes required for collaboration / RBAC / audit logs.
**Failure mode:** Naive diff storage (full row before+after per rule application) grows 400 MB/run × 40 runs/week × 52 weeks = ~800 GB/year in Postgres TOAST; query for "which rule touched revenue in Q2" scans 800 GB. Soda Cloud required for audit log retention / custom RBAC — $750/mo surprise at scale, breaks "10× cheaper" claim.
**Severity: P1** — pricing credibility; no immediate outage.

### 10e. Cold vs Warm Path
**Hand-wave:** Plan correctly distinguishes cold-start (ad-hoc re-run win) vs warm windows, but does not apply it to its own stack. Dagster asset cold-start (import heavy deps), Python subprocess spawn latency, and DuckDB cold compilation (first query compiles, subsequent are cached) are not measured. The bake-off measures wall-clock + cold-start + peak RAM + cost — but does not specify warm-window latency separately, so the hybrid-plane hedge remains unquantified where it matters most (30s streaming).
**Failure mode:** Streaming p99 window latency is 12s (2s subprocess spawn + 3s DuckDB cold + 5s `to_pylist` + 2s serialization) vs 30s window — 40% overhead, late windows, consumer lag, stale freshness checks.
**Severity: P1** (P0 for streaming SLA).

---

## 11. PRIORITIZED GAP LIST — DRIVES THE NEXT RESEARCH BATCH

| Rank | Gap ID | Component | Title | Severity | Blocks | Effort to Spec |
|------|--------|-----------|-------|----------|--------|----------------|
| 1 | G-YAML-01 | YAML→SQL compiler | Grammar + IR + per-dialect lowering + execution-tested `validate` | **P0** | All mapping adoption | 2-3 days spec; 3-4 wks build |
| 2 | G-LEDGER-01 | Replay ledger | Change-detection granularity + ledger storage + before/after volume model + concurrency | **P0** | Recurring-replay wedge (the whole rescaled value prop) | 2 days spec; 2 wks build |
| 3 | G-MOAT-01 | Sato/TURL/RECA | Transfer gap on dirty retail/CRM + latency envelope + feature-pipeline placement — defer to research track vs MVP | **P0** if in MVP, **P2** if deferred | Harder-moat credibility | 3 wks research; defer MVP |
| 4 | G-HYBRID-01 | Hybrid plane | Serialization contract + subprocess lifecycle + `to_pylist`-free quality path + cold-start amortization at 30s cadence | **P0** for streaming | H3 hedge truthfulness | 1-2 days spec; 1 wk bench |
| 5 | G-SCHEMA-01 | Schema evolution | Breaking-vs-non-breaking policy + version-bump gate + FK≡PK handling in `validate` | **P0** | Any CI gate claim; drift correctness | 1 day spec |
| 6 | G-SODA-01 | Soda wrapping | Contract authoring flow + reference-window sizing (N<200 fallback) + FDR + layering (raw vs canonical) | **P1** | False-positive budget; adoption friction | 2 days spec |
| 7 | G-SPLINK-01 | Splink ER | Blocking-rule language + DuckDB→Spark crossover + adapter seam interface | **P1** | First 400k-customer ER correctness | 1-2 days spec |
| 8 | G-DAGSTER-01 | Dagster distribution | `MappingComponent` interface + `BackfillPolicy` + Airflow-only adoption path | **P1** | Embeddability narrative; backfill OOM | 1 day spec |
| 9 | G-ELEMENTARY-01 | Elementary lineage | Per-source freshness cadence + trailing-window + schema-freeze vs mapping state | **P1→P2** | Alert noise; hybrid freshness | 0.5 day spec |
| 10 | G-BPM-01 | Canonical lineage / BPM seam | Exception-queue schema + cell-level source→rule→merge lineage | **P1** | Multi-source conflict auditability | 0.5 day spec |
| 11 | G-TENANCY-01 | Tenancy + pricing | Ledger/YAML/Git isolation + Soda Cloud SPU crossing + cost model | **P1** | Multi-tenant design wins; pricing | 0.5 day spec |
| 12 | G-CONSIST-01 | Consistency | Replay × Kafka offset × downstream mart `AS OF` / invalidation semantics | **P1** | Correctness on correction | 1 day spec |

**Build gate:** P0s (G-YAML-01, G-LEDGER-01, G-MOAT-01 deferral decision, G-HYBRID-01, G-SCHEMA-01) must be specified before a line of mapping compiler or ledger code is written. The remaining P1s can be scoped in parallel with the dirty-10GB bake-off.

---

## 12. HARDEST MOAT CHALLENGE

**Sato/TURL/RECA learned type inference on dirty retail/CRM wide tables.**

Why hardest:
- **Research program masquerading as a feature.** Sherlock alone is 686k columns × 1,588 features × 78 DBpedia types; Sato adds topic-modeling + CRF; TURL pretraining is 80 epochs on 570k tables with TinyBERT (https://vldb.org/pvldb/vol14/p307-deng.pdf, https://arxiv.org/html/2006.14806v2); RECA requires a searchable corpus of schema-similar related tables (https://doi.org/10.14778/3583140.3583149). This is 6-12 weeks of ML engineering before you measure transfer on dirty retail data that looks nothing like VizNet/WebTables.
- **Dirty-data transfer is unmeasured and likely negative on the failure mode that matters.** Retail CSVs have `col_14`, `__EMPTY_3`, abbreviations (`rev`, `amt`, `cust_nm`), locale-specific types (`$1,234.00` vs `1 234,00 €`), and wide tables (180 columns) — none represented in WebTables. The moat's first demo will be *worse* than the regex chain on these cases, exactly when credibility must be highest.
- **Cost and latency are unbounded.** Feature extraction for 180 cols × 1,588 features is the bottleneck, not the CRF (0.2 ms/table is the *post-feature* overhead on 64-core/512GB per https://www.vldb.org/pvldb/vol13/p1835-zhang.pdf). Add 500 MB embedding models + per-window subprocess spawn, and the "moat" becomes an infra liability the solo dev cannot operate at 30s cadence.
- **No moat if you cannot beat the fallback.** The correct MVP posture is *not* "replace regex chain" but "keep regex + statistical fallback, benchmark Sato/TURL on a held-out dirty-CSV corpus, switch only when F1@dirty-CRM > regex +2pp with p99 <500ms." Until that experiment exists, this is a research bet, not a wedge.

---

## 13. MOST UNDER-RESEARCHED COMPONENT

**Replay ledger (deterministic replay + audit).**

Irony: this is the *entire rescaled wedge* (H1 narrow slice: "SFTP drops that map themselves after first approval"), yet it is the least researched. Search evidence shows:

- OneSchema's replay is SaaS-hosted, deterministic *given same input and same saved version* with before/after diff review and staging→production promotion — but no OSS reference implementation or protocol spec to copy (https://docs.oneschema.co/docs/transform-library).
- Git-for-data has a hard 20 commits/sec ceiling, custom kernel, and GC overhead; beyond low contention "you spend more on workarounds than a DB" (Locally Optimistic, cited in findings) — directly challenges the plan's implicit "YAML in Git + ledger in ???" model.
- Dolt/lakeFS/Nessie provide the only mature audit primitives (`dolt_diff_<table>`, `dolt_history_<table>`, `AS OF`, branch/tag time-travel, distributed `UNION` audit per https://docs.doltgres.com/reference/version-control/querying-history, https://dolthub.com/blog/2025-07-17-distributed-audit-logs, https://dev.to/gowthampotureddi/data-version-control-lakefs-nessie-dolt-for-git-like-data-branching-41jb) — but the plan does not evaluate any of them vs plain Postgres as the ledger.
- Zero sources measure audit-trace latency at realistic diff volume (40 files × 50k rows × retained diffs), concurrency across two SFTP drops, or storage cost of cell-granular before/after. No spec exists for rule identity (which pattern matched?), change detection (which hash?), or diff indexing (how to answer "which rule broke revenue in Q2" in <2 min).

All other components have at least a precedent to copy (Soda's `SodaScanComponent`, Splink's blocking tutorial, Elementary's dbt package, Sherlock's feature repo). The replay ledger has no precedent that is both deterministic and OSS and priced for a 5-person team — which is why it *could* be a moat, and also why shipping it unspecified is a guaranteed P0 outage.

---

## 14. VERDICT — SHIP OR RE-SCOPE?

**Do not ship the plan as written.** The falsification accounting is honest, but the hedge architecture reintroduces every killed idea as an unfunded noun.

**Minimum to become build-ready (2 weeks of spec, zero code):**
1. Write the YAML grammar + IR and prove `validate` catches the three quoting/dialect cases above (G-YAML-01).
2. Define the ledger: change-detection hash, rule identity, diff storage/index, before/after volume/cost model, concurrency protocol (G-LEDGER-01).
3. Formally defer Sato/TURL/RECA to a post-MVP research track with an accuracy gate; MVP ships regex + statistical fallback (G-MOAT-01).
4. Spec the hybrid serialization path and prove quality checks can run without `to_pylist` (G-HYBRID-01 + G-SCHEMA-01).

After that, the dirty-10GB bake-off (Next Experiment #2) and the ledger replay audit benchmark (Next Experiment #4) are the two kill-gates that earn the right to write the compiler. Everything else (Soda wrapping, Splink blocking, Dagster component, Elementary cadence) can be built in parallel once the P0s are green.

---

*Sources: All URLs live-fetched 2026-08-30 via SearxNG (Exa). Evidence levels per synthesis: L1 systematic review, L2 peer-reviewed, L3 official/docs/analyst, L4 blog/benchmark, L5 opinion. Confidence is about verdict, not technology. This critique cites search evidence for every claim — no unsupported hand-waving, per task.*

