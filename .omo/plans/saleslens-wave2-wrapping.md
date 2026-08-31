# saleslens-wave2-wrapping - Work Plan

## TL;DR (For humans)

**What you'll get:** P1 wrapping & distribution that turns the P0 compiler+ledger into an operable product — auto-generated Soda contracts with per-col p99 (200× self-splits + BH), Splink blocking audit (≤20M DuckDB) + typed `ERAdapter` seam, Dagster `MappingComponent`+`defs.yaml`+`BackfillPolicy`, Elementary per-source freshness/volume, `exception_queue`+`cell_lineage`+`survivorship.yml`, single-tenant pricing disclosure, and window-atomic `kafka_offsets_committed` (promoted P0).

**Why this approach:** Critique proved folklore PSI 0.25, missing cumulative audit, backfill N→N runs, global `2 intervals`, and `PK(partition,offset)` all page at 3AM. Each is bounded by CI gate (BH ≤1 FP/20 windows, cumulative OR, `max_partitions_per_run=10`, per-source `warn_after 14d`).

**What it will NOT do:** No second ER backend, no BPM UI, no Spark, no learned wiring — seams typed not built.

**Effort:** Large (7 todos, 2 weeks, parallel after wave 1 green)
**Risk:** Low (each closable in 0.5-2d)
**Decisions:** (1) two-artifact Soda (contract+checks) (2) `cumulative_..._data` not sum (3) `MultiPartitionsDefinition` not `Daily`

Your next move: approve this wave, then run `$start-work` on this wave's plan — each wave is independently implementable.

---

> TL;DR (machine): Wave wrapping — 7-13 — 7 todos — gates-before-code.

> **PIVOT 2026-08-31 FileFeeds:** This wave now ships as part of **B2B SaaS FileFeeds for Revenue** (ICP: GTM ops, customer CSV via SFTP/S3/email → `contacts/companies/opportunities` template). Pricing `$50k+/yr` [wetransform.com] vs self-hostable `$0-500/seat`; build cost `$100k+75k/yr 2×` [oneschema.co]. See parent `saleslens-final-build-plan.md` pivot header.

## Scope
### Must have
- `src/mapping/soda.py` `generate_soda_contracts(mapping_manifest, dataset_fqn, warehouse_kind)` → `(contract_yaml, checks_yml)` shared `quote()` + `dataset_fqn` `my_duckdb/main/canonical` → `ds_config.yml name`, Contracts `columns: [{name,data_type,checks:[{missing}]}]` + SodaCL `distribution_difference(col) < threshold: method: ks/chi_square/psi` + `dro` ref, per-col p99 `drift-sentinel` 200× self-splits `p99` when `|R_c|>=200` else `psi_suppressed:true`→`KS`+BH `alpha=0.05`, batched single `register("view",df)` <10s, FDR BH ≤1 FP/20, PSI 0.18 caught
- `src/mapping/er_adapter.py` `ERAdapter(Protocol)` `blocking_audit→Budget, train(pairs|None), predict, cluster`, blocking YAML `sql:"l.email=r.email"` / salted `{blocking_rule, salting_partitions, explain}` / `sql:"levenshtein<3"`, audit `count_comparisons_from_blocking_rule` + `cumulative_..._data` OR dedup, budgets `≤20M DuckDB / ≤100M→1B Spark` salting, crossover `total_comparisons≤20M and rows≤1M` else Spark, cost model, `blocking-mismatch` ≥10pp
- `src/saleslens/components/mapping_component.py` `MappingComponent(Component): build_defs` loads manifest + `warehouse{kind,dsn}` + `ledger_dsn` + `checks_paths` + `partitions source_id×week` via `template_vars.py` `MultiPartitionsDefinition`, `BackfillPolicy single_run or multi_run(max_partitions_per_run=10)` 1040→104, `SftpToCanonicalFactory` for `dagster-airlift` Airflow 2.10+ REST, `@dg.asset` vs `@dg.asset_check`, `<2s build_defs` lazy
- `observability/elementary_catalog.yaml` per-source freshness `time_bucket/training_period/detection_delay/warn_after/ignore_small_changes` (weekly `week:1 warn 14d` vs streaming `minute:30 warn 10m`), `volume_anomalies` z-score batch vs `volume_threshold +-30%/50%` streaming, `generate_schema_baseline_test` pinned, GRANT matrix Snowflake+Postgres, dedup `(source,day)` 1h
- `V20__bpl_seam.sql` `canonical.exception_queue` + `canonical.cell_lineage` PK `(canonical_id,field,source_record_id)` + `survivorship.yml` matrix `source_priority/most_recent/most_complete` + `trust_config.yml` per-field, seam trigger `≥30% FLAGGED` → WARN (EBX `Supersede` rank-0)
- `TENANCY-PRICING-MODEL.md` single-tenant appliance per-deployment, shared upgrade `tenant_{id}.canonical/.elementary/.dataset` + RLS, `$750 crossing` + `$0.86/yr` disclosure
- `V21__kafka_offsets_committed.sql` HWM `PK(consumer_group,topic,partition)` (or history `PK(topic,partition,offset)`) same PG tx as ledger + `pg_advisory_xact_lock` + `UNIQUE`, `AckMode.MANUAL` after tx, append `supersedes:prior_id` + `canonical_revenue_sensor` → dbt re-materialize

### Must NOT have (guardrails, anti-slop, scope boundaries)
- **FileFeeds MVP defer:** Splink ER `400k dedup` + `per-col p99 drift-sentinel` are **harness not gate** for FileFeeds MVP (contacts files ≠ 400k ER day 1). Wave 2 ships Soda `batched single register` + Splink `blocking YAML EBNF` as spec, not enforced gate — full p99 + 180M audit becomes P2 for FileFeeds, P1 for generic wedge.
- No second ER/Ditto built, no BPM UI, no Spark plane, no `fuzzy:{field,threshold}` key, no `count_num_...` wrong API, no global freshness `2 intervals`, no `warn_after` invent, no `PK(partition,offset)` alone, no shared tenancy on day 1

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- `soda contract verify -ds ds_config.yml -c contract.yml` quoted `"order"` + `` `order` `` per dialect, `mapping validate --coherence` pass, `|R|<200` suppress PSI passes KS, 180-col ≤1 FP/20, 0.18 caught
- `count_comparisons_from_blocking_rule` + `cumulative_..._data` audit fails CI >20M, 400k 180M refuses DuckDB, `mypy --strict`, EM round-trip
- `dg dev` <500MB <2s `build_defs`, `dg list defs` 20 assets+checks, backfill 4wks×20 ≤8 runs (from asset graph), Airflow 2.10 observer+factory rollback
- `dbt test --select elementary` 6h slip no fire when `warn_after 14d`, volume FPR <10%, GRANTs, 1 merged incident
- `SELECT * FROM cell_lineage WHERE field='revenue'` <100ms winner+2 losers, `exception_queue` lifecycle, `AssetCheckResult WARN`
- `TENANCY-PRICING-MODEL.md` states per-deployment + `tenant_{id}.*` + RLS, proposal discloses $750
- Kill mid-window `kill -9` proves window atomic+idempotent, `kafka_offsets_committed` same TX, sensor invalidation
- Evidence: `.omo/evidence/task-<N>-saleslens-wave2-wrapping.<ext>` per todo (outside ulw-loop `.omo/evidence/`, inside `omo ulw-loop status --json`)

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. This wave is itself one parallel wave; sub-parallelism per Dependencies below.

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| 7 | 4,5 | 20 | 8,9,10,11 |
| 8 | 4 | - | 7,9,10 |
| 9 | 1,4 | - | 7,8,10 |
| 10 | 4,5 | 20 | 7,8,9 |
| 11 | 4 | - | 7,8,9,10 |
| 12 | 4 | - | 7,8,9,10,11 |
| 13 | 4 | 18 | 7,8,9,10,11,12 |

## Todos

- [ ] 7. Soda wrapping — auto-gen + drift-sentinel p99 + single register
  What to do / Must NOT do: Implement `src/mapping/soda.py` `generate_soda_contracts(mapping_manifest, warehouse_kind)` using shared quoting lib (G-YAML-01 coherence) — NOT hand-authored; layering canonical (post-mapping) in-memory gate pre-Postgres primary via `DuckDBDataSource.from_existing_cursor(cursor)` `cursor.register("view", df)` (Soda docs `verify_locally`) + audit after write; per-col p99 via `drift-sentinel` 200 x random self-splits threshold=p99 when `|R_c|>=200` else suppress PSI (`psi_suppressed:true`) -> `KS/chi_square` + BH `alpha=0.05` per `arxiv.org/html/2607.17336` (PSI false alarm 30/30 at N<200 sharp drop >200) + FDR `alpha' = alpha * rank / N` (Benjamini-Hochberg) logged; batched single `register` + vectorized `SELECT stats GROUP BY` not O(Ncols) scans, prove <10s 50col/50k; FDR target 180-col <=1 FP/20 windows, PSI=0.18 caught where folklore 0.25 misses; `checks_paths` + `configuration.yml` + `checks.yml` committed to Git, proposal-diff `soda contract suggest --diff` on re-drop human approves once; contract YAML `columns: [{name,revenue,checks:[{missing},{invalid},{range},{distribution:method:ks/psi}]}` per Soda Contract Language. Must NOT folklore 0.25, must NOT O(Ncols) x 200 scans, must NOT two DuckDB instances double memory.
  Parallelization: Wave 2 | Blocked by: 4,5 | Blocks: 20
  References: `.omo/plans/research/G-BATCH-C-P1.md G-SODA-01` + `docs.soda.io/reference/contract-language-reference` + `docs.soda.io/reference/data-source-reference-for-soda-core/duckdb` + `github.laiyagushi.com/tkgo1599-max/drift-sentinel` + `arxiv.org/html/2607.17336` + `src/main/java/com/shreyas/saleslens/service/quality/QualityEngineService.java:24 List<QualityChecker>` + `src/main/java/com/shreyas/saleslens/service/quality/ProfilingService.java:52 MIN_BATCHES 3`
  Acceptance criteria: Auto-gen contract from `mapping_manifest` has quoted `"order"` + `` `Revenue ($)` `` per dialect; `mapping validate --coherence` pass; `|R|<200` suppress PSI passes `KS` gate; 180-col demo <=1 FP/20 windows after BH, 200 self-splits p99 catches PSI=0.18 drift that 0.25 misses per `research/G-BATCH-C-P1.md:62-64`; single `register("view",df)` + batched checks no two-instance OOM, runtime <10s.
  QA scenarios: happy batched in-memory gate on 50col/50k (pass <10s) + failure 80-row weekly source with `|R|=80` -> PSI must suppress else 60 alerts/week FAIL + failure folklore 0.25 misses 0.18 drift -> sentinel p99 must catch; Evidence `.omo/evidence/task-7-saleslens-final-build-plan.md` with Soda YAML + pg `register` log + BH α' table.
  Commit: Y | feat(quality): add Soda auto-gen + sentinel p99 + batched register

- [ ] 8. Splink adapter — blocking budget + `er_adapter.py` seam
  What to do / Must NOT do: Implement `src/mapping/er_adapter.py` `class ERAdapter(Protocol): blocking_audit(rules)->ComparisonBudget; train(pairs|None, rules)->Model; predict(candidates)->MatchScores; cluster(scores,threshold)->Clusters` per `research/G-BATCH-C-P1.md:132-138`; blocking language `blocking_rules:` YAML `sql:"l.email = r.email"` / `fuzzy:{field:name,threshold:0.8}` + `explain`, audit via `splink.count_num_comparisons_from_blocking_rules` in CI must enforce budgets DuckDB <=20M (laptop guideline) / Spark <=100M initial -> 1B after tuning (splink docs 03_Blocking), salting for skew 100M+ Spark + DuckDB >=3.9.11 parallel; crossover default DuckDB for `total_comparisons<=20M and rows<=1M` else Spark (driver 32GB+16GB workers ~30 min for u+EM); Splink `train(None)` unsupervised EM, Ditto `train(labeled_pairs)` fine-tune (Ditto serializes entry->sequence-pair, needs labeled pairs per `megagonlabs/ditto`); cost model `comparisons x rows x rules -> wall-clock` (DuckDB 1M ~1 min per `discussions/2209`); blocking-mismatch experiment cross-dataset >=3 heterogeneity types measure F1 recovery >=10pp when blocking matched. Must NOT loose blocking without audit (trillions -> OOM), must NOT hide seam as comment (typed Protocol required).
  Parallelization: Wave 2 | Blocked by: 4 | Blocks: -
  References: `.omo/plans/research/G-BATCH-C-P1.md G-SPLINK-01` + `moj-analytical-services.github.io/splink/demos/tutorials/03_Blocking.html` + `topic_guides/performance/optimising_spark.html` + `topic_guides/blocking/performance.html` + `discussions/2209` + `github.com/megagonlabs/ditto` + `tilores.io/...splink` + `src/main/java/com/shreyas/saleslens/model/Customer.java` + `model/Product.java`
  Acceptance criteria: `count_num_comparisons_from_blocking_rules` audit fails CI if >20M DuckDB / >100M Spark, 400k-customer fixture with `email exact OR fuzzy(surname+postcode)` reports 180M >20M refuses DuckDB (audit UX), tight `email exact OR (surname exact + postcode prefix4)` shows recall delta; `mypy --strict src/mapping/er_adapter.py` 0 errors, Splink adapter unsupervised EM round-trip passes without Spark.
  QA scenarios: happy 400k loose audit reports 180M (refuse) + failure missing audit -> loose rule OOM at 32GB (must fail before fix) + failure tight blocking silently loses typo surname -> recall collapse 30-40% F1 gap must be visible; Evidence `.omo/evidence/task-8-saleslens-final-build-plan.md` with audit CI log + 1M benchmark + seam mypy.
  Commit: Y | feat(er): add Splink blocking audit + adapter seam

- [ ] 9. Dagster distribution — `MappingComponent` + `defs.yaml` + `BackfillPolicy`
  What to do / Must NOT do: Implement `src/saleslens/components/mapping_component.py` `class MappingComponent(Component): build_defs(context)->Definitions` where `build_defs` loads manifest (`src/mapping/ir.py`) + `warehouse_config{kind:duckdb|postgres|bigquery,dsn:"{{ env('WAREHOUSE_DSN') }}"}` + `ledger_dsn` + `checks_paths` + `partitions source_id x week` via `template_vars.py` `MultiPartitionsDefinition` + `post_processing assets: [{target: saleslens.components.MappingComponent, partitions_def: "{{ template_vars.weekly_source_partitions }}"}]` per `research/G-BATCH-C-P1.md:198-219` normative; `BackfillPolicy.single_run()` for self-dependent canonical or `multi_run(max_partitions_per_run=10)` 52 wks x 20 sources -> 104 runs not 1,040; `SftpToCanonicalFactory(source_config, mapping_yaml_path)->Definitions` per Airflow `load_csv_to_duckdb_defs` pattern for `SftpToWarehouseOperator` args, `defs.yaml` `mappings:[{dag_id: sftp_ingest_dag, factory: saleslens.airlift.sftp_to_canonical}]` for `dagster-airlift` (Airflow 2.10+ REST API, observer); asset `@dg.asset` produces canonical table (like MetricFlow) vs Soda `@dg.asset_check` gates (like `SodaScanComponent checks_paths+configuration.yml`); lazy import Sato/TURL only inside asset exec not `build_defs` (<2s cold). Must NOT `build_defs` heavy deps at import (OOM), must NOT N partitions = N runs without policy.
  Parallelization: Wave 2 | Blocked by: 1,4 | Blocks: -
  References: `.omo/plans/research/G-BATCH-C-P1.md G-DAGSTER-01` + `docs.dagster.io/guides/build/assets/creating-asset-factories` + `guides/build/components/building-pipelines-with-components/using-partitions` + `migration/airflow-to-dagster/airlift-v1/task-level-migration/migrate` + `migration/airflow-to-dagster/airflow-component-tutorial` + `integrations/libraries/soda SodaScanComponent` + `src/main/java/com/shreyas/saleslens/service/ingestion/IngestionOrchestrator.java:51 JobOperator` + `docker-compose.yml:backend 8500:8080`
  Acceptance criteria: `dg dev` with example `defs.yaml` (mapping_yaml_path + warehouse + ledger_dsn + checks_paths + weekly partitions) starts laptop <500MB user-code, `dg list defs` shows 20 assets+checks; backfill 4 wks x 20 sources respects `max_partitions_per_run=10` <=8 runs observed (not 80); Airflow 2.10 sandbox `SftpToWarehouseOperator` DAG observed via `dagster-airlift` and proxied via `SftpToCanonicalFactory` one-line rollback works.
  QA scenarios: happy `dg dev` cold <500MB (pass) + failure missing `BackfillPolicy` -> 80 runs for 4 wks x 20 sources OOM pool exhaustion (must fail before fix) + failure Airflow shop without `rest api` -> stall (must show docs prerequisite); Evidence `.omo/evidence/task-9-saleslens-final-build-plan.md` with `dg` logs + `defs.yaml` + backfill audit.
  Commit: Y | feat(dagster): add MappingComponent + defs.yaml + backfill + airlift

- [ ] 10. Elementary OSS — per-source cadence + volume + schema baseline + GRANT matrix
  What to do / Must NOT do: Implement `observability/elementary_catalog.yaml` per-source freshness `sources:[{name:sftp_hubspot,cadence:weekly,freshness:{time_bucket:{period:week,count:1},warn_after:{period:day,count:14},training_period:{period:week,count:4},detection_delay:{period:hour,count:2}}, ...},{name:kafka_live_sales,cadence:streaming,freshness:{time_bucket:{period:minute,count:30},warn_after:{period:minute,count:10},ignore_small_changes:{period:minute,count:2}}}]` per `docs.elementary-data.com/data-tests/anomaly-detection-tests/freshness-anomalies` row L1; volume `volume_anomalies` z-score for batch (`time_bucket week training 4w`) vs `volume_threshold warn +-30% error +-50%` for streaming (`direction drop` SFTP, `both` streaming); schema freeze `elementary.schema_changes` + baseline `generate_schema_baseline_test` macro pinned in Git, new column `discount_code` maps to `update mapping YAML (map or confirm ignore)` vs expected feedback; permissions matrix `analytics_elementary` schema `GRANT USAGE ON WAREHOUSE ELEMENTARY_WAREHOUSE; GRANT SELECT ON INFORMATION_SCHEMA.TABLES; GRANT ALL ON SCHEMA analytics_elementary` Snowflake + `GRANT USAGE,CREATE ON SCHEMA elementary TO elementary_user` Postgres (`docs.elementary-data.com/cloud/integrations/dwh/snowflake+postgres`); dedup `(source,day)` 1h collapse single Slack webhook. Must NOT global `2 intervals` (Kafka 60s fire) or hand `trailing row-count` without z-score vs threshold choice.
  Parallelization: Wave 2 | Blocked by: 4,5 | Blocks: 20
  References: `.omo/plans/research/G-BATCH-C-P1.md G-ELEMENTARY-01` + `docs.elementary-data.com/data-tests/*` + `github.com/elementary-data/dbt-data-reliability` + `src/main/resources/db/migration/V6__data_profiles.sql` + `docker-compose.yml:Postgres`
  Acceptance criteria: `dbt test --select elementary` passes for weekly SFTP without firing freshness during normal 6h slip when `warn_after 14d`; volume trailing 30d <10% FPR on historical weekly row-count, streaming uses threshold not anomaly; `elementary` schema writes succeed with documented GRANTs PG+Snowflake; delayed-file simulation single file 6h late produces 1 merged incident not 8.
  QA scenarios: happy 6h slip weekly SFTP (no fire) + failure global `period:hour count:2` on Kafka -> fires every 60s (must be caught by per-source) + failure `volume_anomalies` on streaming -> unstable z-score (must use threshold); Evidence `.omo/evidence/task-10-saleslens-final-build-plan.md` with per-source YAML + `dbt test` log + GRANTs.
  Commit: Y | feat(observability): add per-source Elementary catalog + baseline + GRANTs

- [ ] 11. BPM seam + cell lineage — `exception_queue` + `cell_lineage` + `survivorship.yml`
  What to do / Must NOT do: Create `V20__bpl_seam.sql` with `canonical.exception_queue(id uuid pk gen_random_uuid(), canonical_id uuid fk canonical.customers, entity_type text customers|orders|products, field text, values jsonb {source_id:value}, source_provenance jsonb [{source_id,record_id,ingested_at,rule_id,rule_version}], strategy text TRUST_HIERARCHY|LATEST_WINS|FLAGGED_FOR_REVIEW, importance high|low, status open|resolved|suppressed, decided_by text, decided_at timestamptz, created_at) + idx (canonical_id,field,status)` + `canonical.cell_lineage(canonical_id,field,source_record_id uuid fk staged_records, rule_id, rule_version, strategy, source_trust numeric, winning bool, ingested_at) pk (canonical_id,field,source_record_id)` per `research/G-BATCH-C-P1.md:365-399`; implement `survivorship.yml` matrix `high+trust_gap>=0.3->source_priority (deterministic top-ranked non-null wins, Informatica trust per-column)`, `high+trust<0.3+timestamp_gap>7d->most_recent+flag`, `cannot->FLAGGED_FOR_REVIEW->exception_queue open`, `low->most_recent/most_complete`, `missing->most_complete` (per `primentra`/`kanoniv` `source_priority/most_recent/most_complete/aggregate` + row vs cell survivorship) field-by-field after ER clustering (matching vs survivorship); `trust_config.yml` per-source per-field trust scores Git-versioned (Informatica column-by-column trust); seam trigger `>=30% FLAGGED_FOR_REVIEW` or customer SLA -> promote `WARN` to full BPM UI (EBX `Supersede` rank-0), `AssetCheckResult WARN` not block DAG. Must NOT table-level lineage only (cannot answer `revenue` who approved), must NOT row-level fallback when Trust column enabled.
  Parallelization: Wave 2 | Blocked by: 4 | Blocks: -
  References: `.omo/plans/research/G-BATCH-C-P1.md G-BPM-01` + `docs.informatica.com/.../trust-settings` + `kanoniv.com/docs/concepts/survivorship.html` + `help.boomi.com/...ranked_sources` + `docs.tibco.com/ebx-addon/...trusted_sources` + `src/main/java/com/shreyas/saleslens/service/canonical/CanonicalLoadService.java:59 PASS1` + `src/main/java/com/shreyas/saleslens/service/conflict/ConflictDetectionService.java:37 fieldsToCheck` + `src/main/java/com/shreyas/saleslens/service/canonical/LineageService.java:43 writeLineage`
  Acceptance criteria: Multi-source conflicting `revenue` fixture (HubSpot $12k, Stripe $11,700, Sheets $12.5k) produces `exception_queue` row with 3 values+provenance+strategy; `SELECT * FROM cell_lineage WHERE canonical_id=:id AND field='revenue'` <100ms returns winner+2 losers with trust scores; partial mapping missing field resolves via `most_complete` not `LATEST_WINS`; Dagster `AssetCheckResult(passed=False,severity=WARN)` for `FLAGGED_FOR_REVIEW` not block; `survivorship.yml`+`trust_config.yml` checked in; steward `Supersede` inserts rank-0 future merges.
  QA scenarios: happy 3-source revenue conflict -> queue+lineage <100ms (pass) + failure 11-stage UI built in MVP -> over-scope FAIL + failure row-level fallback when Trust enabled -> cell-level must win; Evidence `.omo/evidence/task-11-saleslens-final-build-plan.md` with `exception_queue` SELECT + `survivorship.yml` + `WARN` log.
  Commit: Y | feat(lineage): add exception_queue + cell_lineage + survivorship

- [ ] 12. Tenancy & pricing — single-tenant MVP + shared upgrade + cost disclosure
  What to do / Must NOT do: Document `TENANCY-PRICING-MODEL.md` posture MVP single-tenant appliance per-deployment `canonical/elementary/ledger` (isolated Postgres + `profiles.yml` per tenant) — no cross-tenant `tenant_id` partition vs shared risk; shared upgrade `tenant_{id}.canonical` + `tenant_{id}.elementary` + `tenant_{id}.dataset` Soda (dataset name namespaced per `soda Cloud dataset-q` per-dataset resource + `soda.io/reference/data-flows-between-soda-and-user` multi-tenant but no record data) + Postgres RLS+`GRANT` matrix per engine; cost table ledger cell 10MB/run after dedup ~20GB/yr ~$24 vs row-full 400MB/run rejected + DuckDB batched $0 + Soda Cloud free SPU quota vs paid $750 crossing >10 engineers disclosed in proposal (`toolradar.com/tools/soda-core` Team $750/mo, `soda.io/pricing` Collaborative contracts audit RBAC gated), Elementary OSS $0 vs Cloud Auto-monitors ML ranking; dataset `tenant_{id}.*` convention + `soda Cloud architecture dataset-q` naming. Must NOT shared on day 1, must NOT hide $750 crossing, must NOT dataset name without tenant prefix (cross-tenant rule leakage).
  Parallelization: Wave 2 | Blocked by: 4 | Blocks: -
  References: `.omo/plans/research/G-BATCH-C-P1.md G-TENANCY-01` + `docs.soda.io/reference/data-flows-between-soda-and-user` + `docs.soda.io/.../soda-cloud-architecture` + `docs.soda.io/deployment-options.md` host vs self-hosted Runner + `soda.io/pricing` + `toolradar.com/tools/soda-core` + `research/G-LEDGER-01.md storage cost 38/41/98 GB`
  Acceptance criteria: `TENANCY-PRICING-MODEL.md` states MVP isolation per-deployment + shared upgrade `tenant_{id}.*` + RLS-ready DDL; proposal includes `$750 crossing at >10 engineers` and cost table ledger cell vs naive + DuckDB $0; dataset convention `tenant_{id}.*` enforced in `generate_soda_contracts` naming; no per-tenant `tenant_id` partition in MVP ledger (per-deployment).
  QA scenarios: happy shared upgrade `tenant_42.canonical.customers` RLS query isolates (pass) + failure missing `tenant_` prefix -> `checks for dataset-q` deletes wrong tenant (must fail) + failure $750 not disclosed -> pricing integrity FAIL; Evidence `.omo/evidence/task-12-saleslens-final-build-plan.md` with model doc + cost calc.
  Commit: Y | docs(tenancy): add pricing model + isolation posture

- [ ] 13. Consistency — window atomic + `kafka_offsets_committed` + append correction + sensor
  What to do / Must NOT do: Implement `V21__kafka_offsets_committed.sql` `kafka_offsets_committed(window_id uuid, partition int, offset bigint, ledger_tx_id uuid fk replay_runs, committed_at timestamptz) pk (partition,offset)` same PG tx as ledger (replay trick: Kafka `AckMode.MANUAL` `commitSync at last_offset+1` contiguous HWM only after `pg_advisory_xact_lock`+`UNIQUE fingerprint`+`replay_leases` fencing + `ON CONFLICT DO NOTHING`); window atomic unit `[window_start,window_end)` — kill mid-window restart proves idempotency (sparse beyond HWM stays uncommitted, `StagedRecord SHA256 + V17 partial unique` dedup `DataIntegrityViolationException` catch per `LiveSalesEventConsumer.java:92`); correction append `supersedes:prior_id+correction_reason` not Dolt `AS OF` branch mutation (Dolt `dolt_history/rebase/gc` 1,570d blowup), sensor `canonical_revenue_sensor -> dbt re-materialize` invalidates downstream mart that consumed pre-correction snapshot; dedup `SHA256(message bytes)` vs windowed batch overlap when consumer lags vs rebalance reprocessing ledger idempotent. Must NOT offset before ledger (double-apply), must NOT history rewrite.
  Parallelization: Wave 2 | Blocked by: 4 | Blocks: 18
  References: `.omo/plans/research/G-BATCH-C-P1.md G-CONSIST-01` + `src/main/java/com/shreyas/saleslens/service/ingestion/LiveSalesEventConsumer.java:80 sha256` + `src/main/java/com/shreyas/saleslens/service/ingestion/StreamPipelineScheduler.java:74 rotateWindow` + `src/main/java/com/shreyas/saleslens/config/StreamKafkaConfig.java:44 DLT` + `research/G-LEDGER-01.md:48` + `dev.to/.../data-version-control-lakefs-nessie-dolt` + `src/main/java/com/shreyas/saleslens/model/StagedRecord.java`
  Acceptance criteria: Kill mid-window restart proves window atomic + idempotent replay (HWM contiguous, beyond uncommitted); 2k-row replay append `supersedes` triggers downstream `canonical_revenue_sensor` re-materialize; forced rollback proves tx atomic `kafka_offsets_committed` + ledger same tx; rebalance reprocessing deduped via `record_hash` unique; window never double-commits beyond HWM.
  QA scenarios: happy commit after ledger (pass HWM) + failure offset before ledger -> replay double-apply (must fail) + failure correction via Dolt branch -> history rewrite (must be rejected); Evidence `.omo/evidence/task-13-saleslens-final-build-plan.md` with `kafka_offsets_committed` SELECT + HWM log + sensor invalidation.
  Commit: Y | feat(consistency): add atomic window + offset table + sensor

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
- Soda auto-gen quoted+coherence+≤1 FP/20+0.18 caught
- Splink audit fails CI >20M, 400k 180M refuses DuckDB+`mypy` pass
- `dg dev` <500MB, backfill ≤8 runs+Airlift factory
- Elementary per-source no 8-alert storm+FPR<10%+GRANTs
- `exception_queue`+`cell_lineage` <100ms+WARN
- Tenancy stated+$750 crossing
- Offset-after-ledger window atomic+idempotent+sensor

---
> Parent: `.omo/plans/saleslens-final-build-plan.md` (355 lines, 20 todos, hardened Appendix 8 patches) — this wave is a slice. Hardened Appendix applies to this wave's todos. Run `$start-work` per wave independently.
