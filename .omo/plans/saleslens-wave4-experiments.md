# saleslens-wave4-experiments - Work Plan

## TL;DR (For humans)

**What you'll get:** Ordered P0 experiments that kill=rescope the wedge before full build — n≥15 forced-choice interviews (thin asset vs MDM, `would trial in 60d` ≥30% top-2 → keep thin else opinionated only), dirty-10GB bake-off same hardware wide/string-heavy high-card null GROUP BYs 30s-5min windows vs warm-JVM Spring Batch vs Spark NEE (≥2× vs OOM/fidelity rescope), 20-CSV time-to-canonical counterbalanced n≥5 with Merkle 0 re-verify + <2min audit (<1.2s on 90d), and DQ side-by-side Soda+Elementary vs prototype (≥90% recall confirms wrap).

**Why this approach:** Research falsified H1 general (14h of 58h mapping washed out), H3 ≥5× (1.3× at 20GB, NEE wins at 12.7GB), H4 pluggable (bundle wins), H5 vendor whitespace (GX/Soda at $0) — this wave cheap → expensive kill order validates wedge before code. Runs **week 1 interview parallel to wave 1** not after gates.

**What it will NOT do:** No interview framing bias, no `warm vs cold` conflation, no one-shot 50% headline if <20% vs baseline.

**Effort:** Large (4 todos, 5-8 weeks wall but parallel to waves 1-2, cheapest kill first)
**Risk:** Medium (interview 30% threshold, bake-off ≥2×, H1 phase-specific)
**Order:** 17→18→19→20 but 17 week 1 parallel, 18 week 2-3 parallel

Your next move: approve this wave, then run `$start-work` on this wave's plan — each wave is independently implementable.

---

> TL;DR (machine): Wave experiments — 17-20 — 4 todos — gates-before-code.

> **PIVOT 2026-08-31 FileFeeds:** This wave now ships as part of **B2B SaaS FileFeeds for Revenue** (ICP: GTM ops, customer CSV via SFTP/S3/email → `contacts/companies/opportunities` template). Pricing `$50k+/yr` [wetransform.com] vs self-hostable `$0-500/seat`; build cost `$100k+75k/yr 2×` [oneschema.co]. See parent `saleslens-final-build-plan.md` pivot header.

## Scope
### Must have
- **FileFeeds GTM validation (replaces generic thin vs MDM):** fake-door `Launch customer CSV → warehouse in 15 min, self-hosted FileFeeds` + 15 GTM interviews `Would you pay $50k not to build?` vs `would trial self-hosted free` (SearxNG: FileFeeds $50k+ [wetransform.com], in-house $100k+75k 2× [oneschema.co]) — validates WTP 9/10 vs data-team `would trial 60d ≥30%`.
- Exp1 2wks n≥15 scale-up (5-person, 20+ SaaS+sheets+PG) forced-choice `thin asset vs MDM suite vs all-in-one` + `would trial in 60d` binary + heterogeneity rationale + DQ `would add to CI` top-3 → H4 <15% top-2 + ≥50% bundle → ship opinionated ER without pluggable, H5 <15% top-3 → drop mapping-coupled (already wrap)
- Exp2 2-3wks same hardware 10GB dirty ≥20 heterogeneous CSVs wide/string-heavy high-card null GROUP BYs fresh+drifted + Kafka 30s-5min windows wall-clock+cold-start+peak RSS+cost+fidelity ±2pp + 20GB/100GB crossover vs warm-JVM Spring Batch (local chunking/partitioning/virtual threads 10M+/hr) vs Spark NEE, gate H3 <2× or OOM≤100GB or fidelity≥5pp → bound ≤20GB+hybrid
- Exp4 3-4wks counterbalanced n≥5 analysts 20-100-field canonical explicit m:n+FK=PK 5-run variance wall-clock+decomposed ingest/map/ER/quality+calibration ≥80%/≥70%+0 re-verify Merkle+<2min audit, gate H1 <20% vs baseline or mapping <25% wall-clock → phase-specific 60-70% only, needs 1+2 green
- Exp5 1-2wks Soda+Elementary vs prototype mapping-aware checker same re-ingest precision/recall+alert volume+false-positive vs N+time-to-detect, gate H5 ≥90% recall at ≤ false positives → drop vendor (confirmatory, already wrap)

### Must NOT have (guardrails, anti-slop, scope boundaries)
- No `thin asset` pejorative framing, no `same hardware` undefined (fix laptop vs EC2 c6.8xlarge), no `warm vs cold` win conflated, no 5× claim if <2×, no interviews after 2 months

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- n≥15 transcripts `would trial in 60d` ≥30% thin (H4) AND mapping-aware CI <15% top-3 (H5) else rescope per pivots
- Bake-off ≥2× vs tuned Spring/NEE without OOM, fidelity ±2pp end-to-end, 20GB/100GB crossover documented
- `qwen3:8B+technique vs GPT-4` `p<0.10` + tokens×calls frontier (wave 3) before claiming locality
- Time-to-canonical 0 re-verify + audited mapping-phase saving, calibration ≥80%/≥70%, decomposed timings
- Soda+Elementary ≥90% recall at ≤ false positives → confirm wrap (P2 not blocking launch)
- Evidence: `.omo/evidence/task-<N>-saleslens-wave4-experiments.<ext>` per todo (outside ulw-loop `.omo/evidence/`, inside `omo ulw-loop status --json`)

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. This wave is itself one parallel wave; sub-parallelism per Dependencies below.

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| 17 | 3,4,5 | 19 | 18* (sequential) |
| 18 | 6,13 | 19 | 17 (sequential) |
| 19 | 17,18 | - | 20 |
| 20 | 7,10 | - | 19 |

## Todos

- [ ] 17. Interviews n>=15 — forced-choice thin vs MDM
  What to do / Must NOT do: Run interview protocol per `oss-mapping-wedge.md:§6 Exp1` 2 weeks, n>=15 scale-up data engineers (5-person team, 20+ SaaS+sheets+PG) forced-choice `thin asset vs MDM suite vs all-in-one` + `would trial in 60 days` (binary) + heterogeneity rationale open + DQ ranking `would add to CI` top-3; gate cheapest GTM kill: H4 <15% top-2 + >=50% bundle preference -> ship opinionated ER without pluggable pitch, H5 <15% top-3 -> drop mapping-coupled (already wrap Soda). Must NOT build wedge before interviews if H4 fails (rescope to opinionated only).
  Parallelization: Wave 4 | Blocked by: 3,4,5 | Blocks: 19
  References: `oss-mapping-wedge.md:§6` + `research/G-MOAT-01-AI-EVAL.md` + `src/main/java/com/shreyas/saleslens/controller/DataSourceController.java`
  Acceptance criteria: `n>=15` transcripts + top-2 `would trial` >=30% thin asset (H4) AND mapping-aware CI <15% top-3 (H5) confirms wrap; otherwise rescope per pivots table row 1.
  QA scenarios: happy 15 interviews (gate pass) + failure 5 interviews -> insufficient power (must not gate); Evidence `.omo/evidence/task-17-saleslens-final-build-plan.md` transcripts anonymized.
  Commit: N | chore(research): run interviews

- [ ] 18. Dirty-10GB + event-window bake-off — same hardware
  What to do / Must NOT do: Run bake-off per `oss-mapping-wedge.md:§6 Exp2` 2-3 wks, same hardware, 10GB dirty corpus >=20 heterogeneous CSVs wide/string-heavy high-card null GROUP BYs fresh+drifted + Kafka 30s-5min windows wall-clock+cold-start+peak RSS+cost+fidelity +-2pp + OOM gate; add 20GB+100GB dirties for crossover; compare warm-JVM Spring Batch (local chunking/partitioning/virtual threads 10M+/hr) vs Python subprocess IPC+daemon, vs Spark NEE at 12.7GB; gate H3 strongly falsified (<2x vs tuned Spring/NEE or OOM inside <=100GB or fidelity >=5pp -> bound <=20GB + hybrid already chosen) saves months if plane collapses.
  Parallelization: Wave 4 | Blocked by: 6,13 | Blocks: 19
  References: `research/G-HYBRID-01.md:305-315 fidelity +-2pp` + `research/G-BATCH-C-P1.md` + `docker-compose.yml` + `src/main/java/com/shreyas/saleslens/batch/csv/CsvIngestionJobConfig.java:chunk 50`
  Acceptance criteria: dirty-10GB >=2 x vs tuned Spring/NEE without OOM fidelity +-2pp (H3 rescope), end-to-end window p99 <15s, dagster sub-1s, pgbench; 20GB+100GB crossover documented.
  QA scenarios: happy 10GB passes >=2x (gate green) + failure OOM at 75GiB DuckDB leak / 300GB Polars RSS on 20GB (must bound hybrid); Evidence `.omo/evidence/task-18-saleslens-final-build-plan.md` bake-off table.
  Commit: N | chore(research): run bake-off

- [ ] 19. 20-CSV time-to-canonical with replay — counterbalanced n>=5
  What to do / Must NOT do: Run Exp4 per `oss-mapping-wedge.md:§6` 3-4 wks, counterbalanced n>=5 analysts 20-100-field canonical explicit m:n+FK=PK 5-run variance wall-clock + decomposed ingest/map/ER/quality + calibration >=80%/>=70% + 0 re-verify on re-ingest (Merkle) + <2min audit (<1.2s on 90d synthetic); gate H1 general falsified (<20% vs stronger baseline or mapping <25% wall-clock -> pitch phase-specific 60-70% mapping-phase only) needs 1+2 green.
  Parallelization: Wave 4 | Blocked by: 17,18 | Blocks: -
  References: `oss-mapping-wedge.md:§6` + `research/G-LEDGER-01.md` + `research/G-BATCH-C-P1.md` + `src/main/java/com/shreyas/saleslens/service/canonical/CanonicalLoadService.java:59`
  Acceptance criteria: Recurring replay 0 re-verify + audit <2min on 20-CSV (20-100 fields) + mapping-phase saving 60-70% with decomposed timings, calibration thresholds met.
  QA scenarios: happy re-ingest unchanged Merkle same fingerprint (0 re-verify) + failure byte-hash would re-verify on reordered rows (must use Merkle); Evidence `.omo/evidence/task-19-saleslens-final-build-plan.md` time table.
  Commit: N | chore(research): run time-to-canonical

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
- Interviews ≥30% `would trial` validates thin wedge, else opinionated ER only (no pluggable pitch)
- Bake-off ≥2× validates hybrid IPC (else bound ≤20GB+hybrid already chosen)
- LLM factorial `p<0.10` validates 7-9B locality (else hosted GPT-4)
- 20-CSV <2min audit + 0 re-verify validates recurring replay pitch (phase-specific 60-70% if washed out)

---
> Parent: `.omo/plans/saleslens-final-build-plan.md` (355 lines, 20 todos, hardened Appendix 8 patches) — this wave is a slice. Hardened Appendix applies to this wave's todos. Run `$start-work` per wave independently.
