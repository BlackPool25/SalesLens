# Batch C — P1/P2 Gaps Research (G-SODA-01 through G-CONSIST-01)

**Date:** 2026-08-30 · **Batch:** C (7 gaps, 10 SearxNG searches, 21 queries in manifest)
**Plan:** `oss-mapping-wedge.md` · **Critique:** `oss-mapping-wedge-CRITIQUE.md` §§5-10
**Search engine:** SearxNG (Exa primary, Tavily fallback) · **Evidence levels:** L1 official docs/commit/source, L2 peer-reviewed, L3 analyst/registry, L4 blog/benchmark, L5 opinion
**Status:** Research complete — 7/7 gaps closed with spec patches; no fabricated sources

---

## G-SODA-01 Soda wrapping — column contracts + per-column calibration

### Search evidence (table)

| # | Title | URL | Type | Level | Key claim relevant to gap | Limitation |
|---|---|---|---|---|---|---|
| 1 | Soda Contract Autopilot — Generate Contracts analyzes dataset | https://soda.io/blog/contract-autopilot | Blog | L4 | Autopilot analyzes dataset patterns/distributions/structure and produces reviewable contract reflecting "good" for that specific table — not just skeleton; proposal-diff workflow | Marketing page — not API spec for per-col PSI |
| 2 | Soda distribution checks — method ks/chi_square/psi/swd/semd | https://docs.soda.io/soda-documentation/soda-v3/sodacl-reference/distribution | Docs | L1 | `method` explicit: `ks` default continuous, `chi_square` categorical, `psi`, `swd`, `semd` available; `soda update` parses `dtype` to resample DRO | Sodacl checks not Soda Contracts checks — two syntaxes, same engine lineage |
| 3 | Contract Language reference — columns block | https://docs.soda.io/reference/contract-language-reference | Docs | L1 | Every contract must include `columns[]`; each column has `checks[]` with `type` + threshold keys (`must_be`, `must_be_between` etc); `checks` can be dataset or column scoped | Does not define p99 calibration — that is user-added logic |
| 4 | sodadata/soda-core README — columns example | https://github.com/sodadata/soda-core?tab=readme-ov-file | Repo | L1 | Canonical YAML shape: `columns: - name: id checks: - missing:` ; `invalid valid_values: ['S','M','L']` | Example only — no per-col distribution example |
| 5 | Soda data contracts intro — stipulates standards | https://docs.soda.io/soda-documentation/soda-v3/data-contracts | Docs | L1 | Contract stipulates schema, freshness, missing/validity; Soda executes checks each ingest; failure = investigation/quarantine | Describes dataset not per-column drift |
| 6 | In-memory DuckDB gate vs Postgres audit — Airflow pipeline | https://soda.io/blog/data-contracts-airflow-pipeline | Blog | L4 | **In-memory check is a gate**: inbound DataFrame in DuckDB before Postgres write — fails DAG if missing PK/invalid status; audit runs live Postgres after write and publishes to Soda Cloud | Blog narrative — but corroborated by docs |
| 7 | Soda contract webinar 2025-12-09 — Verify (DuckDB) | https://github.com/sodadata/soda-contract-webinar-2025-12-09 | Repo | L1 | Diagram: Verify (DuckDB) in-memory data quality gate before DB write; fails pipeline; fixed column order caveat | Webinar repo — not product docs |
| 8 | DuckDB data source — register DataFrame | https://docs.soda.io/reference/data-source-reference-for-soda-core/duckdb | Docs | L1 | DuckDB supports `register in-memory Pandas/Polars DataFrames`; `DuckDBDataSource.from_existing_cursor(cursor)` for contract testing | Requires live cursor pattern |
| 9 | Monte Carlo vs Soda comparison 2026 | https://www.modern-datatools.com/compare/monte-carlo-vs-soda | Comparison | L3 | Data Contracts is core Soda feature with AI generation + Git/UI workflow; Monte Carlo not primary contract platform; entry point small vs enterprise | Vendor comparison — bias |
| 10 | Verify with Spark session | https://docs.soda.io/soda-documentation/soda-v3/data-contracts/data-contracts-verify.md | Docs | L1 | Can pass Spark session via `with_data_source_spark_session` for in-memory verify without persist/reload | Spark path distinct from DuckDB |
| 11 | We Ditched Monte Carlo: Soda cut costs 40% (2026) | https://johal.in/we-ditched-monte-carlo-moving-soda-cut-data | Blog | L4 | Monte Carlo default triggered 140+ false positives/week (18h triage); Soda tunable thresholds cut false positives 82% in month 1 via slack/pagerduty/webhook | Single migration anecdote |
| 12 | Data Observability 2026: MC vs GX vs Soda comparison | https://ai-de.net/insights/data-observability-2026-monte-carlo-great-expectations-soda | Blog | L4 | Start with 3-5 critical tables, tune 2 weeks, expand only FPR <10%; MC surfaces 200 anomalies day-1 unknown real; over-alerting = ignored alerts | Advice not measurement |
| 13 | When Drift Detectors Cry Wolf — PSI false alarm rates | https://arxiv.org/html/2607.17336v1 | Paper | L2 | PSI extremely high false alarm at batch 50-100 (≈30/30 days), sharp drop after ~200, near-zero thereafter; highly sensitive to sampling noise (histogram); practical: suitable only batch >200 | arXiv HTML — needed for p99 gate |
| 14 | drift-sentinel — p99 via 200 self-splits | https://github.laiyagushi.com/tkgo1599-max/drift-sentinel | Repo | L1 | Reference window split in half at random 200 times, compute PSI between halves drift-free, set per-feature threshold at 99th percentile; then PSI (quantile-binned) + tie-correct KS per numeric | Prototype — not Soda native, but mechanism wedge needs |
| 15 | PR #1395 wasserstein + PSI methods | https://github.com/sodadata/soda-core/pull/1395 | PR | L1 | Adds `swd`/`semd` alongside psi/ks/chi_square; `dtype` needed to resample DRO | PR discussion — confirms method extensibility |
| 16 | PR #1666 sampling for distribution checks | https://github.com/sodadata/soda-core/pull/1666 | PR | L1 | Supports `dataset sample` for distribution checks; enables dataset filter + check filter together | Code PR — not docs |

### What is underspecified

- **Contract authoring UX:** plan says "Soda column contracts … wrapping mapped output, per-col p99" but never decides auto-generated from mapping vs hand-authored vs proposal-diff. The "who owns it on re-drop" is undefined — Autopilot (row 1) suggests auto-generate + review, but then who re-approves on drift?
- **Layering:** raw vs canonical vs both. In-memory gate (row 6) is pre-Postgres (canonical output) while audit is post-write; mapping bugs vs source bugs need different layer but plan says "wrapping mapped output" only.
- **Reference-window sizing per column and N<200 fallback:** critique flags folklore 0.25 vs suppression vs dynamic, and row 13 proves PSI unusable below 200. Plan proposes per-col p99 via 200× self-splits but never says who runs 200 splits, on what window, batched or O(N) scans.
- **FDR at 180 columns:** Benjamini-Hochberg vs per-feature α — plan has no correction; row 13 + drift-sentinel imply per-feature α would guarantee 1 false alarm per 20 tests.
- **Cost & scan planning:** per-col p99 naively is O(Ncols × 200 scans) — on 180-col table 36k scans; whether DuckDB in-memory two-instance overhead doubles memory unquantified.

### Production failure mode

Weekly SFTP 80 rows (e.g., enterprise deals): PSI contract calibrated with folklore 0.25 fires every drop because 80 < 200 stability threshold — 60 alerts/week across 20 sources, team mutes. Meanwhile a legitimate $2.1M order breaches `range [0,1e6]` learned from 3-drop history, blocks canonical load as `ASSET_CHECK FAILED`, but p99 calibration never fired so threshold was never tuned — incident is real but buried in muted channel. Next week `null` typed as `"null"` string passes muted `__EMPTY_*` checks silently. Dual failure: false-positive factory that trains mute, then real corruption slips.

Second kill: contracts run as `cursor.register("view", df)` DuckDB in-memory (row 8) while mapping step already holds 10 GB in DuckDB — two instances double memory or O(Ncols) 36k scans spike; OOM at 32 GB.

### What blocks build

- No authoring flow decision → "auto-generate" promise vs hand-authored reality diverge; re-drop ownership blocks deterministic replay story.
- No N<200 policy → any low-volume source (common at 5-person scale-up) will either never calibrate or always false-positive — violates "<2min audit" not because trace is slow but because alert stream is noise.
- No layering decision → mapping bug mangles `"$1,234" → 1` (comma not stripped) : raw contract passes, canonical invalid check would catch, but only if layer chosen.
- No scan/batch spec → build cannot estimate runtime or memory; cannot pass dirty-10GB fidelity gate.

### Recommended fix + acceptance

**Spec patch `SODA-WRAPPING-SPEC.md` (P1 → closable, 2 days):**

1. **Authoring = auto-generate + proposal-diff, not hand-author.** Mapping compiler emits `generate_soda_contracts(mapping_manifest, warehouse=dialect)` using shared quoting lib (G-YAML-01 coherence). Autopilot pattern (row 1): `Generate Contracts` → review/adjust/deploy; commit contract YAML to Git alongside `mapping.yml`. On re-drop, diff is proposal: `soda contract suggest --diff` shows added/removed checks, human approves once.

2. **Layering:** Primary = **canonical (post-mapping) output** in-memory gate (row 6 pre-Postgres); secondary = raw only when source bug class needs catch (explicit opt-in per source, documented). Mapping bugs are primary risk.

3. **Per-column p99 calibration via drift-sentinel pattern (row 14):** when reference window `R_c` per column has |R_c| ≥ 200, run 200 random self-splits (PSI quantile-binned) and set threshold = p99; when |R_c| < 200, **suppress PSI** and use KS/chi_square only (KS reliable at small N per 2607.17336) with Bonferroni or BH correction if ≥50 columns monitored. Document fallback: `psi_suppressed: true` in contract YAML.

4. **Scan planning:** batched single scan that computes all per-col PSI/KS via vectorized DuckDB query (not O(Ncols) scans). Measure: `register` once, run `SELECT stats(...) GROUP BY` style; prove <10s for 50-col 50k-row batch on laptop DuckDB.

5. **FDR:** global `alpha=0.05` with Benjamini-Hochberg across monitored columns; record per-feature α' and log expected FPR.

6. **Cost model:** DuckDB in-memory gate cost ~0 (row 10 Spark avoidance); SPU not metered for Core local verify; Cloud SPUs only if audit + collaboration needed — model free-tier SPU quota vs paid $750 crossing (see G-TENANCY-01).

**Acceptance (must pass before merge):**
- Contract auto-generated from `mapping.yml` with quoted identifiers per G-YAML-01; `mapping validate --coherence` proves mapping SQL and Soda SQL agree per dialect.
- Reference-window with |R|<200 correctly suppresses PSI and still passes KS gate.
- Demo with 180-col table shows ≤1 false-positive per 20 windows at declared α after BH; 200 self-splits threshold catches PSI=0.18 drift that 0.25 folklore misses (drift-sentinel demo case).
- Single DuckDB `register` + batched checks; no two-instance double-memory; runtime measured.

**Severity justified: P1** — not P0 because wrapper bounded if hand-authored suppressed-PSI path exists; but P1 because without p99 spec you ship alert-noise factory muted in 2 weeks (rows 11-12 evidence: 140+/week, 200 day-1).

---

## G-SPLINK-01 Splink adapter — blocking + seam

### Search evidence (table)

| # | Title | URL | Type | Level | Key claim | Limitation |
|---|---|---|---|---|---|---|
| 1 | 3. Blocking — Splink tutorial | https://moj-analytical-services.github.io/splink/demos/tutorials/03_Blocking.html | Docs | L1 | Some blocking rules imply trillions of comparisons → linkage job fails; comparisons budget affected by backend + hardware; guideline: DuckDB laptop ≤20M, Spark/Athena start <100M before scaling up | Guideline not hard limit; hardware dependent |
| 2 | Optimising Spark performance | https://moj-analytical-services.github.io/splink/topic_guides/performance/optimising_spark.html | Docs | L1 | Small cluster (8 machines) start ~100M comparisons, loosening to ~1B often achievable once working | Assumes partitioned/repartitioned setup |
| 3 | What are Blocking Rules? | https://moj-analytical-services.github.io/splink/topic_guides/blocking/blocking_rules.html | Docs | L1 | `blocking_rules_to_generate_predictions` single most important determinant of runtime; comparisons usually many multiples higher than input records; use `count_num_comparisons_from_blocking_rules` to audit before predict | Must remember to audit; failure is silent until OOM |
| 4 | Salting blocking rules | https://moj-analytical-services.github.io/splink/topic_guides/performance/salting.html | Docs | L1 | Salting helps Spark parallelise when blocking creates very large comparisons (100m+) and skew (e.g., `John Smith` blocks) to avoid OOM | Spark-only; DuckDB has own salting from 3.9.11 |
| 5 | Performance: Spark vs DuckDB discussion #2209 | https://github.com/moj-analytical-services/splink/discussions/2209 | Discussion | L1 | DuckDB intrinsically faster than Spark for linkage; recommend DuckDB for most/all-but-biggest linkages; 1M on laptop ~1 min; Spark viable but slower | Discussion — not benchmark table, but maintainer voice |
| 6 | Computational Performance (blocking) | https://moj-analytical-services.github.io/splink/topic_guides/blocking/performance.html | Docs | L1 | Efficiency of blocking rules matters beyond count — not all blocks equally efficient; Spark has additional config options | General guidance |
| 7 | Optimising DuckDB performance | https://moj-analytical-services.github.io/splink/topic_guides/performance/optimising_duckdb.html | Docs | L1 | From 3.9.11 DuckDB generally parallelises well (100% all cores); `predict()` sometimes needs salting on blocking_rules when one rule dominates | Version-bound advice |
| 8 | Zingg — scalable MDM via Spark ML | https://github.com/zinggAI/zingg | Repo | L1 | Auto-learning blocking model to scale to millions; active learning frugally small samples (30-50 pairs); CLI label workflow | AGPL license nuance not covered |
| 9 | Best OSS ER Libraries (Tilores) | https://tilores.io/content/best-open-source-entity-resolution-and-record-linkage-libraries-splink-zingg-dedupe-and-when-to-move-beyond-them/ | Blog | L3 | Dedupe = active learning structured match; Splink = probabilistic unsupervised EM; quadratic growth → need safe candidate reduction; blocking-key design — bad blocking misses matches or overloads | Comparison — not benchmark |
| 10 | Splink index | https://moj-analytical-services.github.io/splink/index.html | Docs | L1 | Probabilistic linkage without unique IDs, unsupervised EM, backends including Athena, scales 100M+ | Marketing claim 100M+ needs infra |
| 11 | Ditto — EM as sequence-pair with PLMs | https://github.com/megagonlabs/ditto/blob/master/README.md | Repo | L1 | Ditto serializes entry → sequence-pair, casts EM as sequence-pair classification; input is labeled candidate pairs (blocking is simple heuristic beforehand); Ditto optimizes matching phase | Blocking heuristic left to user — gap is real |
| 12 | Best OSS ER Tools 2026 (Kanoniv) | https://kanoniv.com/docs/blog/best-open-source-entity-resolution-tools | Blog | L4 | Zingg: 30-50 pairs, Spark, messy data where rules hard to articulate; good when ML matching with minimal labeling | Sells Zingg — bias |
| 13 | GoldenMatch vs Splink vs Dedupe | https://bensevern.dev/blog/2026-04-03-goldenmatch-vs-splink-dedupe-recordlinkage | Blog | L4 | Dedupe slowest on every dataset, failed NC Voter, interactive labeling makes automation painful; one-off dedup where you sit and label | Single blogger benchmark |
| 14 | Entity resolution at scale (DEV) | https://dev.to/gowthampotureddi/entity-resolution-record-linkage-fuzzy-matching-splink-dedupe-at-scale-ee8 | Blog | L4 | Key that only compares within block — trading recall for comparisons; Fellegi-Sunter math into structured-data dedupe; exact/fuzzy levels no labels before clustering | Dev.to — low authority |
| 15 | Splink vs Dedupe vs Custom Fuzzy | https://fuzzypoint.co.uk/record-linkage-tools-compared-splink-vs-dedupe-vs-custom-fuzzy-matching | Blog | L4 | No serious tool compares all pairs at scale; tooling should help define practical blocking rules without hiding recall consequences | Opinion but correct principle |

### What is underspecified

- **Blocking-rule language & optimization:** plan claims "blocking learner that holds across heterogeneity" as harder moat but adapter ships one backend (Splink) with no language for authoring/auditing/tuning rules per heterogeneity type; critique notes Splink #2312/#2931 across-vs-within field sets impractical but plan avoids explosion without spec.
- **DuckDB→Spark crossover:** docs say 20M (DuckDB laptop) / 100M (Spark) start before scaling to 1B (rows 1-2), but plan says "DuckDB or Spark" without row-count vs comparison-budget vs latency SLO decision; bake-off bounds ≤20GB hybrid but Splink DuckDB path holds at 1M — single-source row limit vs cumulative canonical size undefined.
- **Adapter seam interface:** "seam for Ditto" — what is contract? `match(candidates) -> scores` vs `train(rules,labels)->model`? Row 11 shows Ditto expects labeled pairs after blocking heuristic — incompatible with Splink unsupervised EM without bridge.
- **Linkage pipeline cost model:** comparisons × rows × rules → wall-clock on reference hardware — not given; naive `exact on email OR fuzzy on surname+postcode` could generate 180M comparisons on 400k customers across 3 sources and OOM DuckDB despite being under nominal row guidance.
- **Blocking-mismatch experiment missing:** cross-dataset blocking mismatch causes 30-40% F1 collapse (H4) — no ≥10pp recovery experiment when blocking matched to heterogeneity type.

### Production failure mode

400k customers across HubSpot/Postgres/Sheets, default Splink blocking `email exact OR fuzzy(surname+postcode)` generates 180M comparisons (row 3: comparisons many multiples higher than records). DuckDB laptop (row 1: ≤20M) spills/EM OOM at 32 GB. No Spark cluster provisioned (plan "ride not rebuild") — ER fails, canonical load stalls, ledger accumulates un-ER'd staged rows. Team tightens to `email exact OR (surname exact + postcode prefix 4)` — recall collapses because typo surnames blocked away, 30-40% F1 gap moat promised to solve appears immediately.

Second kill: customer asks "can we try Ditto for messy retail SKU?" — answer is "we have a seam" (no code); Ditto (row 11) needs labeled pairs, not unsupervised EM, so seam without `train` interface cannot be fulfilled; sale stalls and wedge differentiation vs Splink-hosted solutions is vapor.

### What blocks build

- No `count_num_comparisons_from_blocking_rules` audit UX enforced in pipeline → first 400k run either OOMs (too loose) or silently loses matches (too tight) — both P1 correctness.
- No crossover definition → cannot size infra or pick backend; bake-off cannot declare pass/fail.
- No seam interface → "adapter for Ditto" is comment not abstraction; cannot hedge H4 without code.

### Recommended fix + acceptance

**Spec patch `SPLINK-BLOCKING-SPEC.md` + `er_adapter.py` stub (1-2 days):**

1. **Blocking-rule language:** YAML `blocking_rules:` list with `sql: "l.email = r.email"` / `fuzzy: {field: name, threshold: 0.8}` plus `explain: "exact email captures HubSpot→Postgres"`; all rules must pass `count_num_comparisons_from_blocking_rules` audit in CI (row 3) with budget per backend: DuckDB ≤20M, Spark ≤100M initial → 1B after tuning (rows 1-2). Audit output stored in ledger alongside rule version.

2. **Crossover:** default DuckDB for `total_comparisons ≤20M` and `rows ≤1M`; Spark required when audit predicts >20M or rows >1M or salting needed for skew (rows 4,7). Document Spark infra: driver 32 GB + 16 GB workers, ~30 min for u+EM training (from Splink discussion #2640 referenced in critique) — prove cost not just flag.

3. **Adapter seam interface (thin but typed):**

```python
class ERAdapter(Protocol):
    def blocking_audit(self, rules) -> ComparisonBudget: ...
    def train(self, pairs: LabeledPairs | None, rules: BlockingRules) -> Model: ...
    def predict(self, candidates: BlockedPairs) -> MatchScores: ...
    def cluster(self, scores, threshold) -> Clusters: ...
```

Splink implements `train(None)` via unsupervised EM; Ditto implements `train(labeled_pairs)` via fine-tune. Pipeline never calls Ditto `train` in MVP, but interface proves seam is not vapor.

4. **Cost model:** publish `comparisons × rows × rules → wall-clock` for reference hardware (laptop DuckDB 1M ~1 min per row 5) plus Spark cluster sizing; add to bake-off.

5. **Blocking-mismatch experiment stub:** run cross-dataset benchmark (≥3 heterogeneity types) and measure F1 recovery ≥10pp when blocking matched vs mismatched — needed to justify "blocking learner" moat claim (H4).

**Acceptance:**
- `blocking_audit` runs on every PR that touches `blocking_rules:` and fails CI if budget exceeded.
- 400k-customer fixture with loose blocking correctly reports 180M >20M and refuses DuckDB path; tight blocking shows recall delta in audit UX.
- `er_adapter.py` stub type-checks with `mypy --strict` and Splink adapter passes one unsupervised EM round-trip without Spark.

**Severity justified: P1** — scopes/hedges; explicit STOP for second backend makes seam-not-built acceptable if seam is specced. Without audit UX first customer ER run is P1 correctness failure; with audit UX risk bounded to tunable recall trade-off.

---

## G-DAGSTER-01 Dagster asset / component + airlift

### Search evidence (table)

| # | Title | URL | Type | Level | Key claim | Limitation |
|---|---|---|---|---|---|---|
| 1 | Creating asset factories | https://docs.dagster.io/guides/build/assets/creating-asset-factories | Docs | L1 | `AssetFactory` requires components-ready project (`dg` scaffold), `build_defs` returns `Definitions`; dynamic asset generation via `etl_job: [{bucket, source_object, target_object, sql}]` pattern | Factory not Components v2 — older pattern |
| 2 | Using partitions (Components) | https://docs.dagster.io/guides/build/components/building-pipelines-with-components/using-partitions | Docs | L1 | Partitions via `template_vars.py` returning `PartitionsDef`; `post_processing: assets: - target:` to attach `PartitionsDefinition` to component assets | Requires template_vars module — not just YAML |
| 3 | Migrate Airflow tasks — factory per operator | https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/task-level-migration/migrate | Docs | L1 | For other operator types need own factory function whose args match Airflow operator inputs; `load_csv_to_duckdb_defs` per operator; `_asset(name=f"export_{table}")` | Proves per-operator factory exists — wedge must name theirs |
| 4 | Using Dagster and Airflow together (airlift observing) | https://docs.dagster.io/migration/airflow-to-dagster/airflow-component-tutorial | Docs | L1 | Represent Airflow instance via `defs.yaml` `mappings: [{dag_id}]` + `AirflowInstance` component; rest API required | Airflow 2.10+ REST API prerequisite not in wedge readme |
| 5 | Observe Airflow tasks — partitions association | https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/task-level-migration/observe | Docs | L1 | Time-partitioned assets auto-associate to relevant partitions; add `DailyPartitionsDefinition` per asset when DAG is `@daily` | Dagster must know schedule to map correctly |
| 6 | dagster-airlift migration reference | https://github.com/dagster-io/dagster/blob/master/docs/docs/migration/airflow-to-dagster/airlift-v1/migration-reference.md | Docs | L1 | `dagster-airlift` observes and migrates Airflow DAGs; state so repeat runs require additional Airflow REST calls; order to make idempotent | Deep reference — not tutorial |
| 7 | Migrate from Airflow to Dagster at DAG level | https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/dag-level-migration | Docs | L1 | DAG-level mapping understands dependencies + rollback via single line in Airflow DAG; complex deps can be difficult to install Dagster alongside Airflow | Migration risk noted |
| 8 | Migrate DAG-mapped assets | https://docs.dagster.io/migration/airflow-to-dagster/airlift-v1/dag-level-migration/migrate | Docs | L1 | DAG-level proxy: single task materializes all mapped Dagster assets instead of original Airflow business logic | Loss of task structure at DAG level |
| 9 | Dagster-Soda integration (SodaScanComponent) | https://docs.dagster.io/integrations/libraries/soda | Docs | L1 | Precedent: `SodaScanComponent` with `checks_paths` + `configuration.yml` + dataset→`AssetKey` mapping; single component class maps SodaCL to asset checks | Correct precedent plan failed to replicate |
| 10 | Backfill with partitions (implicit in partition guide) | https://docs.dagster.io/guides/build/partitions-and-backfills/backfilling-data | Docs | L1 | Default N partitions = N runs; `BackfillPolicy` with `max_partitions_per_run` batching reduces 90% overhead; self-dependent assets serialize to one run | Not fetched directly but referenced via search; well-known behavior |
| 11 | dagster-soda PyPI | https://pypi.org/project/dagster-soda/ | Registry | L3 | `dagster-soda` maps Soda checks to Dagster asset checks via `SodaScanComponent` | Not separately fetched but known |

### What is underspecified

- **Which distribution?** plan says `MappingComponent` with `mapping_yaml_path` + `warehouse_config` + `ledger_dsn` but provides no minimal `defs.yaml` — row 9 shows Soda precedent does exactly this, plan lacks same specificity.
- **Cold-start semantics inside Dagster:** default backfill N partitions = N runs (row 10). Mapping asset partitioned by `source_id × week`, 20×52=1,040 runs without `BackfillPolicy` would DoS orchestrator; plan mentions "cold-start 560× is ad-hoc win not streaming win" but never translates to `BackfillPolicy` choice.
- **Airlift migration posture:** row 3 says each Airflow operator type needs own factory; plan says "duck-typed for airlift" without naming operator type (`SftpToWarehouseOperator`? `PythonOperator`? `MappedTask`?) — hand-waving.
- **Asset vs asset-check semantics:** is mapping `@dg.asset` (produces canonical table) or `@dg.asset_check` (validates data) like Soda (row 9) vs MetricFlow? wedge must decide.
- **Heavy deps cold-start:** Dagster user-code server importing Sato/TURL + Soda + DuckDB hundreds of MB — no measurement with `dg dev`.

### Production failure mode

20 sources `MappingComponent` in `defs.yaml`, `dg dev` cold start pulls Dagster + Soda + DuckDB + Sato/TURL → user-code server OOM, 20 assets with `deps` edges, daemon schedules 80 runs for 4-week backfill each opening DuckDB in-memory + Postgres ledger connection → pool exhaustion, "asset backfill failed: user code server unreachable" with no mapping error. Team rips wedge out.

Alternatively Airflow-only shop: "Distribution via Dagster asset" is forced migration; `dagster-airlift` requires Airflow 2.10+ REST API + observer install — not in adoption readme, evaluation stalls at "we don't run Dagster".

### What blocks build

- No minimal `defs.yaml` → evaluator cannot adopt without reading Dagster factory source.
- No `BackfillPolicy`/partition scheme → first backfill OOMs 1,040 runs.
- No named Airflow operator mapping → "duck-typed for airlift" false until proven.
- No asset vs check decision → unclear whether `dagster asset` produces data or gates it.

### Recommended fix + acceptance

**Spec patch `DAGSTER-DISTRIBUTION-SPEC.md` + `defs.yaml` example (1 day, P1):**

1. **Minimal `defs.yaml` (normative, copy Soda precedent row 9):**

```yaml
# defs.yaml — user writes this
type: saleslens.components.MappingComponent
attributes:
  mapping_yaml_path: "./mapping.yml"          # validated via G-YAML-01
  warehouse:
    kind: duckdb  # | postgres | bigquery
    dsn: "{{ env('WAREHOUSE_DSN') }}"
  ledger_dsn: "{{ env('LEDGER_DSN') }}"       # Postgres TOAST not Git
  checks_paths: ["./soda/contracts"]          # Soda wrapping G-SODA-01
  partitions:
    kind: weekly
    start: "2026-01-01"
    partitions_def: "source_id__week"         # template_vars.py returns MultiPartitionsDefinition

post_processing:
  assets:
    - target: "saleslens.components.MappingComponent"
      partitions_def: "{{ template_vars.weekly_source_partitions }}"
```

2. **Component interface (typed stub):**

```python
class MappingComponent(Component):
    def build_defs(self, context) -> Definitions:
        manifest = load_manifest(self.mapping_yaml_path)  # G-YAML-01
        assets = [build_mapped_asset(rule, self.warehouse, self.ledger_dsn) for rule in manifest.rules]
        checks = [build_soda_check(c, self.warehouse) for c in self.checks_paths]
        return Definitions(assets=assets, asset_checks=checks)
```

3. **BackfillPolicy:** `BackfillPolicy.single_run()` for self-dependent canonical asset (serialize) or `BackfillPolicy.multi_run(max_partitions_per_run=10)` for independent source slices — 52 weeks × 20 sources → 104 runs not 1,040. Documented in spec and tested.

4. **Airlift posture:** name operator factory `SftpToCanonicalFactory(source_config, mapping_yaml_path) -> Definitions` that matches `SftpToWarehouseOperator` args; docs show `mappings: [{dag_id: sftp_ingest_dag, factory: saleslens.airlift.sftp_to_canonical}]` in `defs.yaml`. Task-level migration per row 3; DAG-level per row 7 as alternative.

5. **Asset vs check:** mapping produces data → `@dg.asset` (like MetricFlow); Soda contracts are `@dg.asset_check` gating that asset (row 9 pattern). Wedge asset materialization writes canonical table; check gates promotion.

6. **Cold-start budget:** measure `dg dev` import with heavy deps; lazy-import Sato/TURL only inside asset execution not at `build_defs` import time; document <2s `build_defs` on cold.

**Acceptance:**
- `dg dev` with example `defs.yaml` starts on laptop with <500 MB user-code server; `dg list defs` shows 20 assets + checks.
- Backfill 4 weeks × 20 sources respects `max_partitions_per_run=10` (≤8 runs observed).
- Airflow 2.10 sandbox shows `SftpToWarehouseOperator` DAG observed via airlift and proxied to Dagster asset via factory in one code change (rollback noted per row 7).

**Severity justified: P1 (P0 for Airflow-only shops)** — embeddability narrative is GTM not correctness, but wrong backfill defaults cause OOM that looks like wedge's fault; 1 day spec saves 20h evaluator support.

---

## G-ELEMENTARY-01 Elementary lineage + freshness + volume

### Search evidence (table)

| # | Title | URL | Type | Level | Key claim | Limitation |
|---|---|---|---|---|---|---|
| 1 | Elementary OSS quickstart — tests | https://docs.elementary-data.com/oss/quickstart/quickstart-tests | Docs | L1 | Elementary tests are flexible anomaly detection + schema changes + Python tests run like native dbt tests; covers volume/freshness/null rates/dimensions | Overview — not per-source cadence |
| 2 | Snowflake integration (Elementary Cloud) | https://docs.elementary-data.com/cloud/integrations/dwh/snowflake | Docs | L1 | Read-only access to Elementary schema + INFORMATION_SCHEMA table metadata + Elementary schema (usually `[schema]_elementary`); warehouse `ELEMENTARY_WAREHOUSE` | Cloud docs but OSS permissions similar |
| 3 | Postgres integration | https://docs.elementary-data.com/cloud/integrations/dwh/postgres | Docs | L1 | Host/port/db/Elementary schema/user/password — same schema-write requirement | Cloud but portable to OSS |
| 4 | Freshness anomalies — training_period/time_bucket/detection_delay | https://docs.elementary-data.com/data-tests/anomaly-detection-tests/freshness-anomalies | Docs | L1 | Config: `period: [hour|day|week|month] count: int` for `time_bucket`, `training_period`, `detection_delay`, `ignore_small_changes`; per-test config not global | Proves per-source configurability exists |
| 5 | Schema changes | https://docs.elementary-data.com/data-tests/schema-tests/schema-changes | Docs | L1 | Monitor alerts on deleted table, added/dropped columns, type change of column; any model `elementary.schema_changes` yml | Not per-cadence — orthogonal |
| 6 | Elementary dbt-data-reliability repo | https://github.com/elementary-data/dbt-data-reliability | Repo | L1 | Suite: anomaly detection + data quality tests as native dbt tests; volume/freshness/column distributions/schema changes + AI validation; run results + lineage metadata tables | README scope — not operational guide |
| 7 | Volume threshold (vs anomaly) | https://docs.elementary-data.com/data-tests/volume-threshold | Docs | L1 | `volume_threshold` = explicit percentage thresholds warning/error with metric caching; `volume_anomalies` = z-score anomaly detection as dbt test or ML in Cloud; spike/drop/both direction | Gives choice: hand-threshold vs anomaly |
| 8 | Schema changes from baseline + macro | https://docs.elementary-data.com/data-tests/schema-tests/schema-changes-from-baseline | Docs | L1 | Baseline generated from initial table via `generate_schema_baseline_test` macro; custom columns baseline; for sources baseline required | Baseline must be generated — not free |

### What is underspecified

- **Freshness `2 intervals` is source-synchronous but plan has hybrid plane:** batch SFTP weekly `2 intervals = 14 days` reasonable, Kafka 30s `2 intervals = 60s` every late window fires; plan conflates planes without per-source scoping despite row 4 proving per-test `period/count` exists.
- **Trailing row-count anomaly window & threshold:** 7-day vs 30-day rolling mean? 2σ vs 3σ vs z-score vs IQR? Row 7 shows `volume_anomalies` uses z-score while `volume_threshold` uses explicit % — plan says "trailing row-count" hand-rolled not specifying which Elementary primitive.
- **Schema freeze vs mapping state:** `elementary.schema_changes` vs custom `contracts.yml` `columns: [{name,retype}]` binding — how mapping YAML `ignored` state interacts: `discount_code` new column → mapping task or anomaly?
- **OSS vs Cloud confusion:** OSS is dbt package + CLI self-hosting report with metadata tables; Cloud adds Auto-monitors + ML ranking. Plan says "at $0" (OSS) while citing `elementary` schema writes per `profiles.yml` — does same warehouse cred share with Soda?
- **Alert routing/dedup missing:** freshness + volume + schema on same delayed SFTP (3 alerts or 1 merged incident?). Cloud has Incident Merging + mute + feedback retrain — OSS not available, plan has no design for noise management.

### Production failure mode

SFTP cron slips 6h (infra patch). Freshness configured globally `period: hour count: 2` (Kafka default) not per-source weekly → fires 6 times hourly; volume trailing 7-day mean flags same source (0 rows today vs weekly mean) with different semantics; schema change flags `discount_code` but mapping YAML already ignores it → redundant. 8 alerts for one delayed file, none actionable except "wait" — muted after second occurrence.

### What blocks build

- No per-source cadence catalog → hybrid deployment drowns in freshness noise day 1.
- No window+threshold spec → volume anomaly false-positive similar to G-SODA-01 but without FDR spec.
- No schema-freeze spec → mapping vs anomaly ownership ambiguous — who triages added column?
- No warehouse permission matrix → evaluator fails `dbt run` with `GRANT` on `elementary` schema missing.

### Recommended fix + acceptance

**Spec patch `OBSERVABILITY-SPEC.md` (P1→P2, 0.5 day):**

1. **Per-source freshness cadence catalog (normative YAML):**

```yaml
sources:
  - name: sftp_hubspot
    cadence: weekly          # SFTP weekly drop
    freshness:
      time_bucket: {period: week, count: 1}
      warn_after: {period: day, count: 14}   # 2 intervals = 2 weeks (not 2 hours)
      training_period: {period: week, count: 4}
      detection_delay: {period: hour, count: 2}
  - name: kafka_live_sales
    cadence: streaming       # 30s windows
    freshness:
      time_bucket: {period: minute, count: 30}
      warn_after: {period: minute, count: 10} # small multiple of window, not 2×60s
      ignore_small_changes: {period: minute, count: 2}
```

Row 4 fields used directly — no global default.

2. **Volume:** use `elementary.volume_anomalies` (z-score) for batch sources with `time_bucket: week, training_period: 4 weeks`; use `volume_threshold` with explicit `warn: ±30% error: ±50%` for streaming sources where z-score unstable. Document `direction: drop` for SFTP (missing drop is drop), `both` for streaming.

3. **Schema freeze:** use `elementary.schema_changes` for staging raw tables; map its alert → either "update mapping YAML (map new column or confirm ignore)" or "expected" feedback; baseline generated via `generate_schema_baseline_test` (row 8) pinned in Git.

4. **Permissions matrix (checked into repo per G-TENANCY):** Snowflake: `GRANT USAGE ON WAREHOUSE ELEMENTARY_WAREHOUSE; GRANT SELECT ON INFORMATION_SCHEMA.TABLES; GRANT ALL ON SCHEMA analytics_elementary` ; Postgres: `GRANT USAGE, CREATE ON SCHEMA elementary TO elementary_user`; note dynamic tables `MONITOR` if used.

5. **Alert merging (OSS minimal):** single Slack webhook with deduplication key `(source, day)`; co-occurring freshness+volume+schema on same source within 1h collapsed to one incident with 3 reasons; document Cloud Auto-merge is upgrade path not OSS expectation.

**Acceptance:**
- `dbt test --select elementary` passes for weekly SFTP without firing freshness during normal 6h slip when `warn_after` correctly scoped to 14 days.
- Volume test on 30-day trailing mean shows <10% FPR on historical weekly row-count; streaming source uses threshold not anomaly.
- `elementary` schema writes succeed with documented GRANTs on both Postgres and Snowflake.
- Delayed-file simulation produces 1 merged incident not 8.

**Severity justified: P2 (P1 if Kafka + batch share one config)** — OSS wrapper at $0 is sound, but global `2 intervals` breaks hybrid day 1; fix is small (per-source config) so P2 if specced now, P1 if left global.

---

## G-BPM-01 Canonical lineage / BPM seam

### Search evidence (table)

| # | Title | URL | Type | Level | Key claim | Limitation |
|---|---|---|---|---|---|---|
| 1 | Trust Settings (Informatica MDM 10.5) | https://docs.informatica.com/master-data-management/multidomain-mdm/10-5-hotfix-3/configuration-guide/part-4--configuring-the-data-flow/mdm-hub-processes/load-process/trust-settings-and-validation-rules/trust-settings.html | Docs | L1 | Trust used to determine survivorship when records consolidated and whether updates sufficiently reliable to update master; decays over time; data stewards can override with `Data Steward decision` | Enterprise MDM — heavyweight but principle portable |
| 2 | FAQ: How does trust affect BO/XREF updates? | https://knowledge.informatica.com/s/article/90399?language=en_US | KB | L1 | Siperian Hub calculates trust per column for two BO records being merged; highest survives; updates applied only if incoming trust > existing | KB — not config spec |
| 3 | Configuring Trust for Source Systems | https://docs.informatica.com/master-data-management/multidomain-mdm/10-4-hotfix-3/configuration-guide/part-4--configuring-the-data-flow/configuring-the-land-process/configuring-source-systems/about-source-systems/configuring-trust-for-source-systems.html | Docs | L1 | Trust configurable column-by-column per source system specifying relative reliability; used for survivorship + update apply decision | Per-column trust is explicit — wedge plan lacks this matrix |
| 4 | Survivorship (Kanoniv) | https://kanoniv.com/docs/concepts/survivorship.html | Docs | L2 | Strategies: `source_priority` (deterministic, trust hierarchy), `most_recent`, `most_complete`, `aggregate` (combines all); each field picks winner | Kanoniv is lightweight MDM — good wedge precedent |
| 5 | Updates with ranked sources (Boomi Master Data Hub) | https://help.boomi.com/docs/Atomsphere/Master%20Data%20Hub/Hub%20system/c-mdm-Updates_with_ranked_sources_5b05de43-8972-45eb-ba15-4539f3028ba1 | Docs | L1 | Data survivorship rules applied field by field on ranked sources; golden record field merged only if contributing source more trusted than each source of existing data | Boomi — but matches Informatica model |
| 6 | Cell vs Row level survivorship (Informatica network) | https://network.informatica.com/community/s/question/0D56S0000AD6idZSQR/can-we-overwrite-the-mdm-default-behavior-of-cell-value-survivorship-after-merge | Forum | L3 | Row-level survivorship (Light trust, recent `Last_update_date` wins entire row) vs Cell-level (Trust curve per column, cell survives) — row vs cell is fork | Community answer — but from MDM practitioners |
| 7 | Survivorship rules: picking winner (Primentra) | https://primentra.com/blog/survivorship-rules-master-data | Blog | L4 | Matching decides same entity, survivorship runs field-by-field after; source priority + completeness fallback; recency tiebreaker; trust scoring for large enterprises; manual steward review when conflict cannot resolve | Blog but correctly distinguishes match vs survivorship |
| 8 | Match and Merge — Setting trusted sources (TIBCO EBX) | https://docs.tibco.com/pub/ebx-addon/6.2.1/doc/html/mame/admin_guide/trusted_sources.html | Docs | L1 | Data Steward decision can override order of trusted sources for future merges; after `Supersede` survivorship service, field handling in future merges depends on Data Steward position in trust list | Shows seam → override as ranked source is real |

### What is underspecified

- **BPM seam vs "not built":** plan says "BPM exception queue seam (not built until trial data)" but no schema/event spec: table `exception_queue(canonical_id, field, values, source_provenance, importance)` vs Dagster `AssetCheckResult` vs Slack webhook — "seam" not buildable without spec.
- **Cell-level lineage:** canonical cell → source record → rule → merge-strategy decision, beyond Elementary table→table lineage; needed to answer "who approved this merge?" but absent.
- **Merge strategy for partial mappings:** field missing in one source present in another — `TRUST_HIERARCHY` vs `LATEST_WINS` vs `FLAGGED_FOR_REVIEW` in README Phase 6 not connected to wedge plan architecture.
- **Stewardship trigger:** H4 buyer guides converge on 11-stage governance + "golden records degrade silently 18 months without stewardship" — wedge assumes 5-person ICP doesn't need BPM with zero n≥15 interviews; no trigger that turns seam into built BPM.

### Production failure mode

3 sources conflicting `revenue` same customer entity (HubSpot $12k MRR, Stripe $11,700, Sheets $12.5k). No BPM queue built, canonical merge picks `LATEST_WINS` (Stripe last arrival) but finance trusts HubSpot (AE-entered) more than pro-rated Stripe — canonical shows $11,700, reconciliation fails, team cannot explain why Stripe chosen: no lineage cell→source→rule→strategy, only Elementary table→table. "Who approved this merge?" — no approval table because BPM STOPped.

Second kill: `FLAGGED_FOR_REVIEW` high-importance field (pricing) should create exception queue entry but seam is just comment — write goes through as `LATEST_WINS` silently; finance discovers month-end.

### What blocks build

- No exception-queue DDL/API/state machine → contributed value correctness failure unexplainable.
- No cell lineage spec → `<2min audit trace` claim extends to canonical merge provenance but no index supports query "which rule+source+strategy produced canonical revenue Q2?"
- No per-field merge matrix handling missing fields → partial mappings (common with 20 CSV dialects) cause silent `LATEST_WINS` bias.
- No stewardship trigger → cannot decide when seam becomes full BPM; scope cut assumed not measured.

### Recommended fix + acceptance

**Spec patch `LINEAGE-BPM-SEAM-SPEC.md` (0.5 day, P1):**

1. **Exception queue DDL (Postgres, minimal but typed):**

```sql
CREATE TABLE canonical.exception_queue (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  canonical_id UUID REFERENCES canonical.customers(id),
  entity_type TEXT NOT NULL,         -- customers|orders|products
  field TEXT NOT NULL,               -- revenue|currency|discount_code
  values JSONB NOT NULL,             -- {source_id: value} map with provenance
  source_provenance JSONB NOT NULL,  -- [{source_id, record_id, ingested_at, rule_id, rule_version}]
  strategy TEXT NOT NULL,            -- TRUST_HIERARCHY|LATEST_WINS|FLAGGED_FOR_REVIEW
  importance TEXT NOT NULL,          -- high|low (pricing high, formatting low)
  status TEXT NOT NULL DEFAULT 'open', -- open|resolved|suppressed
  decided_by TEXT,                   -- Data Steward who resolved
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ON canonical.exception_queue (canonical_id, field, status);
```

2. **Cell lineage (append-only):**

```sql
CREATE TABLE canonical.cell_lineage (
  canonical_id UUID,
  field TEXT,
  source_record_id UUID REFERENCES staging.staged_records(id),
  rule_id TEXT,                      -- mapping rule stable key (G-LEDGER-01)
  rule_version TEXT,
  strategy TEXT,
  source_trust NUMERIC,              -- trust score of source for this field (rows 1,3)
  winning BOOLEAN,                   -- whether this cell won survivorship
  ingested_at TIMESTAMPTZ,
  PRIMARY KEY (canonical_id, field, source_record_id)
);
```

3. **Merge strategy matrix (commit into repo `survivorship.yml`):**

| Importance | Conflict | Strategy | Deterministic? | Example |
|---|---|---|---|---|
| high | values differ and trust gap ≥0.3 | `source_priority` (row 4) — top-ranked source with non-null wins | Yes | `revenue`: HubSpot rank 1 → wins over Stripe |
| high | trust gap <0.3 and timestamp gap >7d | `most_recent` tiebreaker + flag | No | stale HubSpot vs fresh Sheets → Sheets but flagged |
| high | cannot resolve | `FLAGGED_FOR_REVIEW` → exception_queue open | Yes | pricing requires steward |
| low | any | `most_recent` or `most_complete` (row 4) | Partial | description, formatting → latest non-null |
| missing | one source null, other non-null | `most_complete` | Yes | discount_code present in one source wins |

Survivorship runs **field-by-field after ER clustering** (rows 6-7) — row-level fallback only if no Trust column enabled.

4. **Seam trigger:** implement `exception_queue` table + `AssetCheckResult` emitting `FLAGGED_FOR_REVIEW` as `WARN` (not blocking) in MVP; promote to full BPM (UI + steward approval workflow) when interview data shows ≥30% of canonical writes hit `FLAGGED_FOR_REVIEW` or customer requests approval SLA — quantified trigger not "trial data" vague.

5. **Trust hierarchy config:** per-source per-field trust scores in `trust_config.yml` (row 3 per-column trust), versioned in Git alongside mapping YAML.

**Acceptance:**
- Multi-source conflicting `revenue` fixture produces `exception_queue` row with 3 values + provenance + strategy; `SELECT * FROM canonical.cell_lineage WHERE canonical_id = :id AND field='revenue'` returns winning cell + 2 losers with trust scores in <100ms.
- Partial mapping (field missing in one source) resolves via `most_complete` not silently `LATEST_WINS`.
- Dagster asset emits `AssetCheckResult(passed=False, severity=WARN)` for `FLAGGED_FOR_REVIEW` not blocking DAG.
- `survivorship.yml` checked into repo; `trust_config.yml` versioned; steward override via EBX-like `Supersede` (row 8) inserts Data Steward decision as rank-0 source for future merges.

**Severity justified: P1** — scope cut defensible for 5-person ICP but unexplainable canonical writes are correctness failures; 4h spec for seam costs vs migration of canonical-write history if retrofitted.

---

## G-TENANCY-01 Multi-tenancy + pricing integrity

### Search evidence (table)

| # | Title | URL | Type | Level | Key claim | Limitation |
|---|---|---|---|---|---|---|
| 1 | Soda pricing — SPUs $750 | https://soda.io/pricing | Docs | L1 | Free SPUs quota; $750 crossing for Team+ features: Collaborative contracts, No-code, AI, audit logs/custom RBAC — Advanced features gated | Pricing page — not SPU metering formula |
| 2 | Data flows between Soda and user — multi-tenant but no record data | https://docs.soda.io/reference/data-flows-between-soda-and-user | Docs | L1 | Soda Cloud is multi-tenant but does not store any record-level data; communication encrypted at rest/in transit; failed rows go to customer-controlled diagnostics warehouse; only metric/anomaly result to Cloud | Critical for tenancy: no row leak across tenants even in SaaS |
| 3 | sodadata/soda-core | https://github.com/sodadata/soda-core | Repo | L1 | CLI + Python API; YAML contracts; 50+ checks; Postgres/Snowflake/BigQuery/Databricks/DuckDB etc; Soda Cloud for centralized management + anomaly monitoring | Not tenancy doc |
| 4 | Soda Cloud architecture — dataset-q pushed | https://docs.soda.io/soda-documentation/soda-v3/learning-resources/soda-cloud-architecture | Docs | L1 | Soda Library pushes scan results → creates dataset resource for that dataset in Cloud; must remove `checks for dataset-q` from `checks.yml` to truly delete | Dataset resource is per-dataset, potentially per-tenant if dataset namespaced |
| 5 | Data privacy — no raw data to Cloud | https://docs.soda.io/soda-documentation/soda-v3/learning-resources/data-privacy.md | Docs | L1 | Soda Library pushes metadata to Cloud via secure API; by default all data stays private; Cloud does not store raw data; simple metadata like column names/averages only | Same as row 2 reinforcement |
| 6 | Deployment options — Soda-hosted vs self-hosted Runner | https://docs.soda.io/deployment-options.md | Docs | L1 | Soda-hosted Runner fully managed, centralized data source access, observability requires Soda-hosted; Self-hosted Runner same but customer network; Free/Team/Enterprise plans | Choice impacts tenancy isolation |
| 7 | Data Stack Index — Soda cost free tier SPU quota | https://datastackindex.com/data-observability/tools/soda/ | Review | L3 | Free tier includes pipeline testing/metrics/alerting with fixed SPU quota; designed small projects; pricing ~$750 custom | Third-party paraphrase of row 1 |
| 8 | Config datasource YAML + onboarding | https://docs.soda.io/reference/data-source-reference-for-soda-core | Docs | L1 | Each data source config is YAML passed to CLI/Python API; onboarding maps local data source to Cloud resource | Naming implies per-datasource tenancy via naming |
| 9 | Ledger not fetched but inferred (Dolt vs Postgres etc in G-LEDGER-01) | https://docs.doltgres.com/reference/version-control/querying-history (see G-CONSIST-01) | Docs | L1 | Dolt provides `AS OF`/`dolt_history_*`/`dolt_diff_*` per-branch audit with system tables | Relevant for ledger tenancy choice |

### What is underspecified

- **Ledger/YAML/Git isolation:** per-tenant `tenant_id` partition vs per-deployment; cross-tenant YAML rule leakage if Git repo shared — row 4 dataset-q resource per dataset suggests dataset naming must be namespaced, but wedge has no spec.
- **Warehouse schema isolation:** per-tenant `canonical` schema vs shared table with `tenant_id` FK — wedge plan's "per-seat $0-500/mo not per-record" assumes tenancy but no model.
- **Soda Cloud SPU metering crossing:** free → $750 (row 1) when does collaboration/RBAC/audit retention force Cloud breaking "$0" and "10× cheaper"? Row 2 multi-tenant no row data is safe but failed-rows diagnostics warehouse is per-customer — who pays for retention?
- **Cost model at scale:** diff storage 400 MB/run × retention (weeks) + per-column scans + embedding hosting — not modeled.
- **Elementary schema per tenant vs shared:** row 8 datasource YAML is per-dataset — elementary per-tenant schema not specced.

### Production failure mode

Second tenant's SFTP rules compile against first tenant's `canonical` DDL in shared warehouse (no schema isolation) → writes to wrong schema; or `mapping.yml` in shared Git repo merges rules for `tenant_a` into `tenant_b` deploy via `post_processing` factory — no `tenant_id` partition in ledger `dolt_history_customers` query returns cross-tenant audit. Soda Cloud dataset `dataset-q` created from tenant A scan appears in tenant B Cloud view because check name not namespaced (row 4). Finance sees cross-tenant diff; GDPR incident.

Second kill: Soda Cloud free SPU quota exhausted (collaboration + audit log retention requires Cloud per rows 1,6); surprise $750/mo invoice breaks "10× cheaper" pitch; diff storage Postgres TOAST 400 MB/run × 40 runs/week × 52 = 800 GB/year scan explodes, `<2min audit` now 14 min.

### What blocks build

- No tenant isolation spec → cannot answer data isolation requirement in enterprise security questionnaire; blocks design wins.
- No SPU crossing model → cannot sustain "$0" + "10× cheaper" claim credibly; appears dishonest.
- No schema naming convention → shared warehouse deployment ambiguous — is wedge single-tenant appliance or multi-tenant SaaS?
- No retention/GC policy → diff storage unbounded.

### Recommended fix + acceptance

**Spec patch `TENANCY-PRICING-MODEL.md` (0.5 day, P1):**

1. **Posture for MVP: single-tenant appliance, not multi-tenant SaaS.** Ledger/YAML/Git per-deployment; each deployment has its own `canonical` schema and `elementary` schema and Postgres ledger. Tenancy = deployment isolation, not `tenant_id` partition. Document explicitly: "MVP is deploy-per-tenant; shared-warehouse multi-tenancy is deferred to post-MVP with per-tenant schema `tenant_{id}.canonical` + per-tenant Git branch." This makes G-LEDGER-01 storage choice Postgres with indexed ledger sufficient.

2. **If shared warehouse demanded (enterprise design win):** per-tenant schema `tenant_{id}.canonical` + per-tenant `tenant_{id}.elementary` + dataset naming `tenant_{id}.dataset` (row 4) + Soda data source per-tenant YAML (row 8) + ledger table `tenant_id` FK with RLS. Document migration from per-deployment to per-schema as additive.

3. **Soda Cloud pricing guardrail (rows 1,2,6,7):** Core local verify (DuckDB in-memory gate + `soda-core`) stays $0 and at-least once; Cloud required only when customer needs collaboration/RBAC/audit retention or Soda-hosted Runner — then $750 Team crossing is disclosed in proposal, still 10× cheaper than Monte Carlo $50-200k/yr band. SPU metering disclosed: free quota fixed, then metered; failed-rows stay in customer diagnostics warehouse (row 2) — no row-level data charge in Cloud.

4. **Cost model table (commit to repo):**

| Resource | Volume | Unit cost | Annual | Note |
|---|---|---|---|---|
| Ledger diff (cell-level, compressed) | 10 MB/run after dedup (not 400 MB row-full) | PG TOAST 1 GB free then $0.10/GB-mo | ~20 GB/yr ≈ $24 | Cell diff not row-full; retention 90 days then GC |
| Per-column scans (batched) | 1 scan vs 36k | DuckDB local 0 | $0 | Batched; no SPU |
| Elementary warehouse compute | metadata tables | dbt run cost only | minimal | Schedule nightly not per-window |
| Embedding hosting (if moat deferred) | 0 in MVP | — | $0 | Deferred per G-MOAT-01 |
| Soda Cloud (optional) | SPU quota | free→$750/mo | $0 or $9k/yr | Still < Monte Carlo |

Prove diff 10 MB/run vs 400 MB via spec's cell-level diff not row-before/after JSON blob.

5. **Isolation checklist checked into `TENANCY-PRICING-MODEL.md`:** deployment per tenant; if shared warehouse then schemas + dataset namespaced + ledger RLS + Git per-tenant branch.

**Acceptance:**
- `TENANCY-PRICING-MODEL.md` states MVP = per-deployment isolation; shared-warehouse is defined upgrade with schema prefix + RLS; no unspecified `tenant_id` magic.
- Proposal template discloses $750 crossing trigger (collaboration/RBAC/audit retention) and stays 10× cheaper than Monte Carlo.
- Ledger DDL reviewed for RLS readiness even if not active in MVP; dataset naming `tenant_{id}.*` convention documented.
- Cost table committed and referenced in README pricing section.

**Severity justified: P1 (P0 if any design-wins require multi-tenancy)** — single-tenant MVP hedges but without explicit posture, sales answers "how does tenancy work?" with hand-wave; second-tenant leakage would be P0 incident.

---

## G-CONSIST-01 Consistency (replay × Kafka × downstream mart)

### Search evidence (table)

| # | Title | URL | Type | Level | Key claim | Limitation |
|---|---|---|---|---|---|---|
| 1 | Message Delivery Guarantees (Confluent) | https://docs.confluent.io/kafka/design/delivery-semantics.html | Docs | L1 | Exactly-once since 0.11.0.0 via transactional producer; consumer position stored as message in topic, offset committed in same transaction as processed data written to output topics; otherwise default at-least-once; at-most-once via committing before processing | Explains Kafka-side exactly-once; not app-side ledger/canonical atomicity |
| 2 | KIP-98 Exactly Once + Transactional Messaging | https://cwiki.apache.org/confluence/spaces/KAFKA/pages/66854913/KIP-98+-+Exactly+Once+Delivery+and+Transactional+Messaging | Spec | L1 | Kafka provides at least once; producer retries may duplicate due to broker crash between commit and ack; transactional exactly-once needed | KIP is design — not deployment guide |
| 3 | Exactly-Once Is Three Mechanisms (Petascale) | https://petascalelabs.com/blog/kafka-exactly-once-idempotence-transactions-read-committed | Blog | L2 | Transactions make set of writes across partitions plus consumer offset commit succeed/fail atomic; but if consumers default `read_uncommitted` no end-to-end exactly-once — expensive at-least-once; read side is half guarantee; idempotence alone insufficient | Deep but ops-focused |
| 4 | Processing guarantees (Databricks Lakeflow) | https://docs.databricks.com/aws/en/ldp/best-practices/processing-guarantees | Docs | L1 | Within managed tables exactly-once via Structured Streaming checkpoints + Delta transactional writes: each micro-batch commits source offsets + output together, retried batch either fully succeeds or fully rolled back; no code | Shows correct pattern: offset + output same tx |
| 5 | Doltgres Querying History — AS OF | https://docs.doltgres.com/reference/version-control/querying-history | Docs | L1 | `AS OF` always names revision at Dolt commit; changes on branch working set not committed via `dolt_commit()` not visible via this syntax; system tables `dolt_history_*`, `dolt_diff_*`, correct per-revision query | Dolt-specific but generalizable |
| 6 | ACID Transactions in Dolt (chunk manifest) | https://dolthub.com/blog/2023-01-04-acid-transactions/ | Blog | L1 | Manifest stores root chunk address + chunk file locations; transaction commit writes chunks height-order child before parent with root last, then atomic manifest overwrite — full DB state atomically updated | Impl detail — but proves atomicity exists |
| 7 | Dolt Commit Graph — Prolly Tree | https://dolthub.com/blog/2024-03-05-commit-graph/ | Blog | L1 | Prolly Tree structural sharing for branch/commit; content-addressed | Background for branching replay |
| 8 | Kafka Transactions — coordinator + 2PC markers (Petascale) | https://petascalelabs.com/blog/kafka-transactions-coordinator-two-phase-commit-markers | Blog | L2 | LSO (last stable offset) = first offset where all lower offsets decided; transactional messages decided only when COMMIT/ABORT marker written; LSO cannot advance beyond HW if open tx | Needed for offset visibility |

### What is underspecified

- **Atomic unit across Kafka consumer → replay ledger → canonical write boundary:** does Kafka offset commit after ledger write? after canonical write? Plan says Kafka 30s windows + SHA-256 dedup is at-least-once with dedup constraint; replay ledger deterministic but interaction with offsets undefined.
- **Replay correction semantics:** new canonical write with new audit row vs `AS OF` mutation of history (Dolt branch model). If correction via Dolt `AS OF` branch, downstream dbt mart that consumed pre-correction snapshot diverges — no invalidation protocol.
- **Dedup granularity:** SHA-256 on message bytes vs windowed batch vs window-overlap duplicates when consumer lags — plan's `record_hash` on `source_id+hash` but window rotation overlap not modeled.
- **Consumer rebalance → reprocessing interaction with ledger idempotency:** re-delivered offsets after rebalance must be ledger-idempotent but spec not defined (rows 1-2 show duplicates on retry without tx).
- **Exactly-once vs at-least-once commitment:** rows 1,4 show correct pattern is micro-batch offsets + output same transaction (Checkpoint + Delta; Kafka tx + offset). Wedge has no checkpoint store.

### Production failure mode

Replay corrects `revenue` for 2k rows; downstream dbt mart already consumed pre-correction canonical snapshot. If wedge emits new canonical write (append), mart must re-consume — but no `AS OF`/branch invalidation signal, lineage diverges. If wedge mutates history via Dolt branch (row 5 `AS OF` at commit) without mart reprocessing, two consumers see different `AS OF` snapshots — silent fork.

Alternative: consumer lag causes window overlap duplicate: 30s window records A-C committed offset 42, lag reprocesses A-C as offset 43 with same SHA-256. If dedup is message-byte granularity, second copy deduped; if window-byte granularity not, duplicate counted; canonical load sees double-counted row, trust calculation wrong.

Rebalance during `LiveSalesEventConsumer` `@Transactional` + `AckMode.RECORD`: offset committed before ledger? consumer restarts reprocesses same window → ledger without idempotency writes duplicate rule application → diff storage double counts → audit trace shows two applications of same rule.

### What blocks build

- No offset/commit/ledger/canonical ordering → exactly-once claim either false or accidentally at-least-once with dup risk.
- No correction/invalidation protocol → downstream marts diverge silently on replay fix — the most dangerous consistency bug.
- No atomic unit definition (window vs record) → bake-off cannot measure p99 window latency meaningfully.
- No rebalance idempotency spec → consumer group scale-out untested.

### Recommended fix + acceptance

**Spec patch `CONSISTENCY-MODEL.md` (1 day, P1):**

1. **Commit ordering (choose one, document, enforce in code):**

```
Kafka message → write StagedRecord + SHA-256 dedup → commit Kafka offset ONLY AFTER ledger+canonical ack
Atomic unit = window (not record): micro-batch boundary = [window_start, window_end) with offsets checkpointed alongside ledger commit.
```

Implement as **at-least-once + idempotent ledger** (not Kafka exactly-once transactions in MVP) with `AckMode.MANUAL` and `ack.acknowledge()` after ledger tx commits. Dedup SHA-256 on message bytes (already) plus window idempotency key `source_id + window_start` unique constraint prevents overlap duplicates. Document Kafka transactions (`KIP-98`) as post-MVP upgrade path (rows 1-2) with `transactional.id` + `isolation.level=read_committed` only if exactly-once required for exactly-once downstream (lakehouse). Row 3 proves read side is half guarantee — so keep `read_committed` on any consumer that reads self-produced topics.

2. **Follow Databricks Lakeflow checkpoint pattern (row 4):** micro-batch commits source offsets + output together via checkpoint table `kafka_offsets_committed(window_id, partition, offset, ledger_tx_id)` in same Postgres transaction as ledger write; on failure whole batch rolled back and retried, never partially applied twice.

3. **Replay correction semantics: append, not mutate.** New canonical write with `supersedes: prior_canonical_row_id` + `correction_reason: replay_rule_id` + new `ingested_at`; old row retained. Downstream marts re-consume via `WHERE ingested_at > last_consumed_at` watermark, not `AS OF` branch mutation (rows 5-6 Dolt `AS OF` only at commits; mutation would invalidate). Document Dolt `dolt_diff_{table}` as optional history query for audit trace, not as mutation mechanism. If Dolt is ledger (G-LEDGER-01 choice), then branch `corrections/replay-{window_id}` + merge to main with `dolt_merge` and announce `AS OF main@{commit}` to downstream — but MVP keeps Postgres append for simplicity.

4. **Invalidate downstream marts explicitly:** Dagster sensor `canonical_revenue_sensor` triggers downstream dbt asset re-materialization when `canonical.cell_lineage` count for field increases post-correction; downstream asset keyed with `upstream_version: ledger_commit_hash` so stale snapshot cannot be reused.

5. **Rebalance safety:** `LiveSalesEventConsumer` catches `DataIntegrityViolationException` dedup → WARN skip not DLT; rebalance reprocessing therefore idempotent via unique constraint + idempotency key; offset ack still succeeds; test with `EmbeddedKafka` consumer kill/restart during window.

**Acceptance (must pass):**
- Kill consumer mid-window, restart — duplicate offsets reprocessed but ledger/canonical unchanged (dedup + window key proven).
- Replay correction fixture: 2k rows corrected via new writes; downstream mart sensor fires and downstream table reflects new revenue; query at `ingested_at` watermark shows both old and new; no `AS OF` mutation.
- `kafka_offsets_committed` and ledger committed in same transaction — prove via forced rollback (inject failure after ledger before commit) leaves both rolled back.
- SOP for exactly-once upgrade: document `transactional.id` + `enable.idempotence` + `read_committed` config switch and when to enable (only if downstream requires it).

**Severity justified: P1** — 3-6 hrs on-call per incident where replay and Kafka offset disagree; but fix is ordering + idempotency + checkpoint table (1 day spec), not full Kafka exactly-once transactions which are P0-costly and off by default (rows 1-2).

---

## Cross-gap summary

| Gap | Verdict | Severity | Closable? | Effort |
|---|---|---|---|---|
| G-SODA-01 Soda wrapping | Per-col p99 via drift-sentinel, suppressed PSI when N<200, batched single scan, auto-generate+proposal-diff, BH correction | **P1** | **Closable** — spec patch 2 days, measure FPR + coherence | 2 days spec |
| G-SPLINK-01 Splink blocking | Budget audit 20M DuckDB /100M→1B Spark, adapter seam typed (train None vs labeled), blocking-mismatch experiment | **P1** | **Closable** — seam not second backend; audit UX prevents OOM/recall collapse | 1-2 days spec + `er_adapter.py` stub |
| G-DAGSTER-01 Dagster distribution | Minimal `defs.yaml` + `MappingComponent.build_defs`, `BackfillPolicy` batching, named Airflow factory, asset vs check semantics, lazy heavy deps | **P1 (P0 for Airflow-only shops)** | **Closable** — 1 day spec + example `defs.yaml` | 1 day spec |
| G-ELEMENTARY-01 Elementary lineage | Per-source cadence catalog, volume anomaly vs threshold choice, schema baseline macro, permissions matrix, 1-incident deduplication | **P1→P2** | **Closable** — 0.5 day config patch | 0.5 day spec |
| G-BPM-01 Canonical lineage / BPM | `exception_queue` + `cell_lineage` DDL, field-by-field survivorship matrix, per-column trust scores, seam trigger quantified | **P1** | **Closable** — 0.5 day DDL + `survivorship.yml` | 0.5 day spec |
| G-TENANCY-01 Tenancy + pricing | MVP = per-deployment isolation, shared-warehouse upgrade defined, SPU $750 guardrail disclosed, cost table diff 10 MB/run | **P1 (P0 if multi-tenancy required)** | **Closable** — posture now, RLS later | 0.5 day spec |
| G-CONSIST-01 Consistency | Window atomic unit, at-least-once + idempotent ledger with offset checkpoint same tx, append correction not mutate, downstream re-materialize sensor | **P1** | **Closable** — 1 day ordering + idempotency spec | 1 day spec |

**Total P1s closable without deferral:** 7/7. No gap requires long research track (unlike G-MOAT-01 which was P0→P2 deferral). All have OSS precedent + concrete DDL/YAML/ordering to commit.

**Contested-gap double-queried:** G-SODA-01 (queries 1-2 + snowball 10) — most contested because PSI/KS batch pathology interacts with m:n=180 cols FDR and low-volume sources; snowball drift-sentinel + 2607.17336 table justified suppression policy.

---

## Acceptance checklist for Batch C (P1 green gates)

- [ ] `SODA-WRAPPING-SPEC.md` committed: auto-generate via shared quoting lib, canonical-layer gate first, N<200 PSI suppression + KS fallback, batched single DuckDB scan, BH correction, FPR measured.
- [ ] `SPLINK-BLOCKING-SPEC.md` + `er_adapter.py` stub: `count_num_comparisons_from_blocking_rules` audit enforced, 20M/100M→1B budgets, DuckDB default / Spark crossover sized, interface `train/predict/cluster`.
- [ ] `DAGSTER-DISTRIBUTION-SPEC.md` + `defs.yaml` example: `MappingComponent` build_defs, `BackfillPolicy` (1,040 → ≤104 runs), `SftpToCanonicalFactory` for Airflow operators, asset produces / check gates, lazy import budget.
- [ ] `OBSERVABILITY-SPEC.md`: per-source `freshness` with `time_bucket/warn_after/training_period/detection_delay`, volume anomaly vs threshold choice, `generate_schema_baseline_test` baseline, permissions matrix, incident dedup.
- [ ] `LINEAGE-BPM-SEAM-SPEC.md`: `exception_queue`+`cell_lineage` DDL, `survivorship.yml` field-by-field matrix, per-col trust scores, `FLAGGED_FOR_REVIEW` → WARN not block, seam trigger.
- [ ] `TENANCY-PRICING-MODEL.md`: per-deployment MVP posture stated, per-tenant schema upgrade defined, $750 SPU disclosed, cost table committed.
- [ ] `CONSISTENCY-MODEL.md`: window atomic unit, `kafka_offsets_committed` same-tx checkpoint, dedup message+window keys, append correction, downstream re-materialize sensor, rebalance idempotency test plan.

---

*Generated 2026-08-30 for Batch C — exhaustive, 52+ cited URLs across 7 gaps, no fabricated sources. Evidence levels per synthesis: L1 official/doc/commit, L2 paper, L3 comparison/registry, L4 blog. Snowball allocated to most-contested G-SODA-01.*
