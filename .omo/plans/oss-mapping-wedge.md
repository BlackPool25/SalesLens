# OSS Semantic Mapping Wedge — Build Plan (Rescaled, Evidence-Backed)

**Date:** 2026-08-30
**Status:** Draft — pending brutal critique + gap research
**Research base:** `~/.omnilearn/research/oss-semantic-mapping-wedge-scaleup-data-unification` — 78 sources (36 academic + 42 industry), 12 contradictions, 5 falsifiable hypotheses, adversarial evidence (27 supporting / 28 contradicting), synthesis 35-92% confidence
**Locked wedge:** semantic mapping, any tabular+event batch OK, connector framework, pluggable ER (opinionated first), Python+Dagster asset, OSS, 10× cheaper + interview-validated
**Overall confidence in rescaled wedge:** 55-68%

---

## 1. Problem & Why SalesLens Fails

**Problem:** Scale-up data engineers (5-person team, 20+ SaaS + sheets + Postgres sources, warehouse already exists) unify heterogeneous tabular+event data into canonical entities. Current path: warehouse + manual mapping (cheap but breaks every schema change) vs enterprise MDM (Informatica/Reltio $1.5M-4M year 1, 9-18 months, 20% annual maintenance). The middle — lightweight OSS mapping layer between connectors and warehouse — is unserved.

**Why SalesLens is toy-grade (user diagnosis: all stages):**
- Type inference: deterministic chain INTEGER→DECIMAL→BOOLEAN→DATE→DATETIME→EMAIL→PHONE→CURRENCY_AMOUNT→CATEGORY[unique<20]→FREE_TEXT using `allMatch` regex on full sample — no statistical features, no embeddings, no table context, formally ill-posed without context (Sato Fig.1)
- Mapping: heuristic 1.0/0.85/0.70/0.55 (exact/synonym Levenshtein≤2/token-overlap Jaccard≥0.5/type fallback) — weakest family in Rahm/Bernstein taxonomy alone, static mutable `CopyOnWriteArrayList REGISTRY` vs PAYGO feedback ordering, heuristic ceiling
- LLM advisory: heuristic-first then LLM only if LLM>heuristic via Ollama qwen3.5:9b — unevaluated, inherits heuristic overconfidence, CHOKE high-certainty hallucinations break triage
- Quality: 6 checkers + fixed 20pp/50%/3σ drift (cold-start ≥3 batches) — threshold-arbitrary and sample-size naive vs PSI vs KS tradeoff
- Ingestion: Spring Batch CSV + Kafka 30s windows with SHA-256 dedup — no connector framework, no CDC, no schema evolution handling
- Canonical: trust hierarchy / latest-wins / flagged-review with static registry, no PAYGO utility ordering, no provenance/lineage
- Stack: Java/Spring Batch plane at 8-25× disadvantage vs embedded Python at ≤100GB microbenchmarks (but strongly falsified at geometric ≥5× on dirty data — see H3)

**What research falsified vs survived:**
- H1 ≥50% time-to-canonical (mapping wedge): Provisionally falsified general — mapping is 2-4h of 58h (ER labelling 47.7h dominates), CHOKE hallucinations. Survives only as **recurring SFTP replay** (OneSchema cell→rule, Tamr 60-90% mapping-phase) + audit.
- H2 technique>scale at 7-9B: Contested 45-60%. 70B+technique beats GPT-4 +15.2pp true but 7-9B unproven off-health, retrieval win on small Synthea contradicts, leakage 52% confounds.
- H3 Python ≥5× at ≤100GB: Strongly falsified 78-88% — 20GB 1.3×, Spark NEE wins at 12.7GB, OOMs reproduced (DuckDB 75GiB leak, Polars 300GB RSS on 20GB).
- H4 thin pluggable ER wins trial: Provisionally falsified — buyer guides converge on bundled/MDM + 11-stage stewardship, PAYGO 2008 small-N blocked by parallel batching.
- H5 mapping-coupled quality wedge: Strongly falsified — GX weekly cadence exceeds threshold, Soda column contracts at $0 with 99.8% parity already fill whitespace.

---

## 2. Rescaled Wedge — What We Actually Ship

**Core value prop (H1 rescaled):** Recurring replay + ledger. Zero re-verification on re-ingest of unchanged sources + <2min audit trace. Pitch: "SFTP drops that map themselves after first approval." Not one-shot migration.

**Why it wins vs alternatives:**
- OneSchema FileFeeds: every cell fix → deterministic rule, replay on future uploads, before/after diff (existence proof)
- Forrester/Tamr: mapping 3-6wks → 1-2wks (60-70%), SocGen 90% less prep — mapping-phase saving is real even if washed out end-to-end
- dbt MetricFlow: YAML→SQL compilation, `validate`/`test`, PR governance — architectural precedent for mapping rules compiling to SQL/Python
- PAYGO + cross-dataset ER: blocking mismatch (not matcher choice) is the 40% F1 collapse — wedge must expose heterogeneity-aware blocking

**OSS motion (Pick: dbt, not Airbyte):**
- Airbyte: 350+ connectors, 60k stars, but Docker-per-connector K8s burden, long-tail brittleness, "control becomes responsibility" — connectors commoditised, "we only move bytes"
- dbt: version-controlled transforms + MetricFlow engine OSS (Apache 2.0) + cloud semantic layer is the only semantic abstraction that won (30k customers). The wedge lives *below* dbt — 20 CSV dialects that mean revenue differently.
- Engine Apache 2.0 (YAML + tests + ledger + replay) + cloud governance/audit/caching per-seat $0-500/mo (not per-record, not $50-200k/yr Monte Carlo band). Explicit engine vs product split (single-sponsor GX lesson).

**Components (solo dev, 2-3 months):**

```
INGEST (ride, not rebuild)     MAPPING (wedge + harder moat)       QUALITY (wrapper at $0)             CANONICAL+ER (opinionated)
┌────────────────────┐        ┌──────────────────────────┐        ┌─────────────────────┐            ┌────────────────────┐
│ Airbyte Builder /  │        │ YAML rules in Git        │        │ Volume anomaly +    │            │ Canonical load +   │
│ Meltano SDK / dlt  │───────▶│ compile → SQL/Python     │───────▶│ freshness + schema  │───────────▶│ single matcher     │
│ Singer taps        │        │ confidence(calibrated)   │        │ freeze + Elementary │            │ Splink OR Ditto    │
│ Kafka 30s windows  │        │ + deterministic replay   │        │ lineage + Soda      │            │ seam for 2nd       │
│ (hybrid plane:     │        │ + audit ledger <2min     │        │ column contracts    │            │ BPM seam (not     │
│  Java window +     │        │ + harder moat:          │        │ wrapping mapped     │            │ built pre-trial)   │
│  Python subprocess)│        │   Sato/TURL table context│        │ output, per-col p99 │            │                    │
└────────────────────┘        └──────────────────────────┘        └─────────────────────┘            └────────────────────┘
         │                              │                                    │                                 │
         └───────── OSS kernel: YAML + tests + ledger + replay (Apache 2.0) ─┴─ Cloud: governance/audit/cache ─────────┘
```

**Distribution:** Dagster asset/component + Python library (Soda/GX/MetricFlow pattern), not standalone service. Airflow 1700 providers + Dagster Components asset-factory + `dagster-airlift` incremental migration — adoption via embeddability.

**Harder moat (your demand — replay alone cloneable in 1 quarter):**
- Learned type inference with table context (Sato CRF topic model + TURL Transformer + RECA related-table) on dirty retail/CRM wide tables — dataset incumbents haven't productised
- Cross-dataset ER blocking learner that holds across heterogeneity types (the 30-40% collapse is blocking mismatch)

---

## 3. Scope Boundary (What We Ship vs STOP)

**SHIP (MVP, 2-3 months, solo dev):**
- YAML mapping spec: source column → canonical field, confidence, human verification state (pending/confirmed/ignored), deterministic condition, audit fields (who/when/commit)
- Compiler: YAML → SQL/Python for any warehouse (DuckDB, Postgres, BigQuery), with `validate` + `test` + PR diff CI
- Deterministic replay engine: cell-level fix → versioned rule, replay on re-ingest of unchanged sources, before/after diff, `replay --check` ledger
- Harder moat stub: statistical/embedding features + table context (Sherlock 1,588 features → Sato CRF upgrade path), not regex chain
- Lineage + volume/freshness at $0: Elementary OSS lineage, freshness table 2 intervals, trailing row-count, schema freeze
- Soda wrapping: Soda column contracts (missing/invalid/range/regex, filter/variables, proposal diffs, exit-code gate) validating mapped output, per-column PSI/K S p99 calibration (not global 20pp/50%/3σ)
- Single ER adapter: Splink (probabilistic, unsupervised EM, DuckDB or Spark) — adapter seam for Ditto, not a second backend yet
- Dagster asset: `@dg.asset` / Component YAML + `build_etl_job` → `Definitions` pattern, duck-typed for `dagster-airlift` incremental adoption

**STOP (not in MVP):**
- No second ER backend or pluggable marketplace (H4 hedge — don't staff before trial data)
- No BPM exception queue / 11-stage stewardship (H4 governance is real need but is MDM scope — second-order)
- No Spark plane or claim of ≥5× speed (H3 falsified — bounded ≤20GB + hybrid)
- No new quality engine (wrap Soda at $0; don't compete with Elementary/Soda)
- No connector marketplace or Singer fork (ride Airbyte/dlt, don't rebuild)
- No one-shot 50% headline (H1 falsified general — recurring replay is the pitch)

---

## 4. Data Plane Decision (H3 hedge)

- Ingestion/windowing: measure, don't assume. Warm-JVM Spring Batch (10M+/hr with local chunking/partitioning/virtual threads) vs Python subprocess — isolate profiling to Python subprocess so DuckDB/Polars swappable without GIL Arrow→to_pylist breakage.
- Profiling: whichever plane passes dirty-10GB fidelity gate (type-F1, null-rate, distinct, top-K within ±2pp on same corpus). Defensive `VARCHAR+TRY_CAST` if DuckDB type coercion bug (PR #24167) bites — account speed loss.
- Quality UDFs: DuckDB inside Soda scan path is acceptable, but UDF-heavy 6 checkers materialise Python dicts → `to_pylist()` bottleneck — benchmark separately from scan/JOIN microbenchmarks; expect >10-table joins to favour Spark Catalyst.
- Memory: 50-60% instance limits, fail-fast to spill not OOM; test `union_by_name` on many gzipped dirties; Polars morsel-split 250B/col pathology → shard.
- Cold-start 560× is ad-hoc re-run win, not streaming win — warm windows erase it.

---

## 5. LLM Strategy (H2 hedge)

- Ship model-agnostic harness: bidirectional stable matching (Def 3.1), sampling/aggregation over phrasing/layout perturbation, type prefilter Numeric/Text/Date/Boolean (N→k >50% token cut), as model-agnostic switches
- Retrieval gated by target cardinality: ≤15 fields retrieval off, ≥50 on (crossover ~15-30), tunable; report tokens×calls per mapping for 10× cheaper measurement
- Kill 7-9B locality if factorial shows GPT-4 single-pass ≥ 7-9B+technique (Δ≤0) at matched harness
- Don't claim vs leakage-inflated health benchmarks without retail/CRM dirty tabular replication + J/K held-out

---

## 6. Next Experiments — Ordered, Kill = Rescope Slice

| # | Experiment | Effort | Gate | Kills | Pivot |
|---|-----------|--------|------|-------|-------|
| 1 | **n≥15 scale-up interviews** — forced-choice thin asset vs MDM suite vs all-in-one, "would trial in 60 days" + heterogeneity rationale, plus DQ ranking top-3 "would add to CI" | 2 wks | Interviews validate wedge before code | H4 <15% top-2 + ≥50% bundle preference → ship opinionated ER without pluggable pitch; H5 <15% top-3 → drop mapping-coupled, wrap Soda | cheapest GTM kill |
| 2 | **Dirty-10GB + event-window bake-off** — same hardware, 10GB dirty corpus (≥20 heterogeneous CSVs, wide/string-heavy, high-card null GROUP BYs, fresh + drifted), Kafka 30s-5min windows, wall-clock + cold-start + peak RAM + cost, fidelity ±2pp, bounded OOM gate; 20GB + 100GB dirties for crossover | 2-3 wks | H3 strongly falsified — bound before mapping code | <2× vs tuned Spring Batch/NEE or OOM inside ≤100GB or fidelity ≥5pp drop → bound to ≤20GB + hybrid (already chosen) | saves months if plane collapses |
| 3 | **LLM factorial** — qwen3:8b vs GPT-4 × vanilla vs bidirectional+aggregation+prefilter × retrieval off/on (J/K held-out), MIMIC/Synthea + ≥1 retail/CRM dirty tabular, accuracy@1/HitRate@3/F1, 5 runs, tokens×calls, 3 cardinality strata | 2-3 wks | Decides $/mapping, locality, always-on retrieval | 7-9B+technique Δ≤0 vs GPT-4 → hosted; uniform retrieval → gate removal | cost model |
| 4 | **20-CSV time-to-canonical with replay** — counterbalanced n≥5 analysts, 20-100-field canonical, explicit m:n + FK≡PK, 5-run variance, wall-clock + decomposed (ingest/map/ER/quality), calibration ≥80%/≥70%, 0 re-verify on re-ingest, <2min audit | 3-4 wks | H1 general falsified — need recurring replay slice | <20% vs stronger baseline or mapping <25% of wall-clock → pitch phase-specific 60-70% mapping-phase only | needs 1+2 green |
| 5 | **DQ side-by-side audit** — Soda+Elementary vs prototype mapping-aware canonical-contract checker on same re-ingest, precision/recall on violations, alert volume, false-positive vs N, time-to-detect | 1-2 wks | H5 strongly falsified — confirmatory | Soda+Elementary ≥90% recall at ≤ false positives → drop mapping-coupled vendor, wrap Soda | already dropped as vendor |

---

## 7. Open-Source Sustainability

- Engine Apache 2.0 (YAML + tests + ledger + replay). Cloud: governance/audit/lineage/caching/monitoring, per-seat not per-record.
- Honesty about engine vs product boundary (self-hostable rule compilation + tests OSS; managed is convenience not hostage) — GX single-sponsor lesson.
- Don't repeat MIT→ELv2 trap for engine; ELv2/BSL only defensible for cloud hosting, not for mapping spec.

---

## 8. Risks & Falsification Summary

| Risk | Verdict | Hedge |
|------|---------|-------|
| Replay cloneable in 1 quarter | Real — flagged as moat gap | Harder moat: learned type inference + blocking learner |
| 58h bottleneck washout | H1 provisional, strong on bottleneck | Narrow to recurring SFTP replay, not one-shot |
| 7-9B locality fails | H2 contested | Model-agnostic hedge + factorial K.O. |
| Python plane collapses on dirty | H3 strongly falsified at ≥5× | Bounded ≤20GB + hybrid, bake-off first |
| Pluggable marketplace fatigue | H4 provisionally falsified | Opinionated first + seam |
| Quality vendor whitespace closed | H5 strongly falsified | Wrap Soda, don't compete |

---

## 9. Success Criteria (kill thresholds in Next Experiments)

- n≥15 interviews: thin asset ≥30% top-2 "would trial in 60 days" (H4) AND mapping-aware CI quality not needed as distinct vendor (H5 <15% top-3 confirms wrap decision)
- Bake-off: dirty-10GB ≥2× vs tuned Spring Batch/NEE without OOM, fidelity ±2pp (H3 rescope gate)
- LLM factorial: qwen3:8b+technique vs GPT-4 at matched harness, retrieval stratification interaction p<0.10
- Time-to-canonical: recurring replay shows 0 re-verify + audited mapping-phase saving (H1 slice)

---

*Generated 2026-08-30 from research synthesis. Pending brutal critique + gap research for plan hardening.*
