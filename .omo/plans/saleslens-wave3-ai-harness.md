# saleslens-wave3-ai-harness - Work Plan

## TL;DR (For humans)

**What you'll get:** AI harness wave (parallel track, closable before any learned wiring) — a gated `research/learned_moat/` harness for Sherlock/Sato/TURL/RECA that stays **not wired** in MVP (regex chain `INTEGER→FREE_TEXT` remains default), a 120-run LLM factorial `qwen3:8B vs GPT-4 × bidir+agg+prefilter × retrieval J/K held-out` on MIMIC+Synthea+dirty-CRM with contamination defense, and production guardrails (standing Pod, CHOKE `k≥3` probe, hedged `qwen3→GPT-4→heuristic` + `PipesSubprocessClient` forkserver).

**Why this approach:** Research proved Sato 0.2ms is POST-feature only (real 45s-45min on 180-col), TURL 512-token ceiling, RECA zero-signal on 20 isolated CSVs, and CHOKE 9-43% raw `LLM>heuristic` unsafe — this wave makes the gates executable without leaking private hold-outs.

**What it will NOT do:** No learned inference wired as default, no 7-9B locality claim without `p<0.10`, no per-window spawn, no Python 3.12.

**Effort:** Medium (3 todos, 1-2 weeks, parallel to waves 1-2)
**Risk:** Low (harness+bench, no prod cutover)
**Gates:** L1 F1 >regex+2pp AND L2 p99<500ms incl. features CPU-warm AND OOM-free@8GB; kill 7-9B if Δ≤0; standing Pod <300ms p95

Your next move: approve this wave, then run `$start-work` on this wave's plan — each wave is independently implementable.

---

> TL;DR (machine): Wave harness — 14-16 — 3 todos — gates-before-code.

> **PIVOT 2026-08-31 FileFeeds:** This wave now ships as part of **B2B SaaS FileFeeds for Revenue** (ICP: GTM ops, customer CSV via SFTP/S3/email → `contacts/companies/opportunities` template). Pricing `$50k+/yr` [wetransform.com] vs self-hostable `$0-500/seat`; build cost `$100k+75k/yr 2×` [oneschema.co]. See parent `saleslens-final-build-plan.md` pivot header.

## Scope
### Must have
- Keep MVP `TypeDetectionService.java:24` `INTEGER→DECIMAL→BOOLEAN→DATE→DATETIME→EMAIL→PHONE→CURRENCY_AMOUNT→CATEGORY[<20]→FREE_TEXT` + `SchemaInferenceService.java:34` 500-record `nullRate/uniqueCount/top10/minMax` — scaffold-only; `research/learned_moat/harness.py` with Sherlock 1,588×180=285k pre-DNN (686k VizNet 78 DBpedia F1 0.89) + Sato CRF 0.925 +1.4s/6.4K=0.2ms post-feature (excludes extraction+500MB GloVe/PV) + TURL TinyBERT N=4 d=312 512-token ceiling 1,440>512 + RECA Jaccard/topic zero-signal + gates **L1 dirty-CRM hold-out (private ≥20 tables ≥5×100col 1×180col abbrev/locale 5 runs) F1>regex+2pp AND L2 p99<500ms incl. features CPU-warm OOM-free@8GB ≤500MB AND L3 ablation identical prefilter** + warm daemon pre-warmed GloVe/PV + fallback regex
- `research/llm_factorial/` harness `qwen3:8B vs GPT-4 × vanilla vs bidir+aggregation+prefilter (Gale-Shapley Def 3.1 stable matching + sampling/aggregation + type prefilter N→k >50%) × retrieval {off vs vector+lexical J/K held-out}` on `MIMIC-OMOP+Synthea-OMOP+≥1 retail/CRM dirty (20-100 m:n+FK=PK)` metrics `accuracy@1+HitRate@3+F1+tokens×calls+$/mapping+p50/95/99` 5 runs stratified `≤15/16-49/≥50` `p<0.10`, composite `F1+HitRate@1/@3+decisiveness`, TS-Guessing 52%/57% →100% contamination → private never in prompt, kill 7-9B if `Δ≤0 at matched harness or p>0.10`, reporting `F1/HitRate1/3+tokens_in×out+calls+$/mapping+p50/95/99`
- Standing Pod `s3fs+fuse+readiness 85% <300ms p95 200 concurrent` vs per-window 1.2-9s 96min/day, Ollama `Q4_K_M 5.03GB cold 1.2s vs vLLM 4.8s` A10G, `CHOKE 9-43% + 13/15 scorers <0.5 AUROC` → calibrated hidden-state probe + bidirectional + `unknown→pending` + `k≥3 or abstain` + KV-prefix/embedding/full-response caches + hedged `qwen3→GPT-4→heuristic 0.70` + `PipesSubprocessClient` forkserver `blocking=True AssetCheck` + `ConfigurableResource` + `AutomationCondition.on_cron` + versioned `ledger.model_version`

### Must NOT have (guardrails, anti-slop, scope boundaries)
- No Sato/TURL/RECA wired as default before dirty-CRM gate
- No leakage-inflated health bench without dirty retail + J/K held-out
- No raw `LLM>heuristic` confidence gate without probe, no per-window spawn, no Python 3.12 forkserver

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- MVP still regex chain (not learned), `research/learned_moat/harness.py` thresholds `+2pp & p99<500ms & OOM-free`, private hold-out never crawled, warm daemon vs per-window, regex fallback on timeout/OOM
- 120-run factorial `24×5` on hold-out private reports `accuracy@1+HitRate@3+F1+tokens×calls+$/mapping` per cell, `p<0.10`, kill clause executed if Δ≤0
- Standing Pod readiness <300ms 200 concurrent (not per-window 1.2-9s), CHOKE probe AUROC>0.5, `PipesSubprocessClient` not per-invocation, `blocking=True` gates downstream, `model_version` pinned + rollback
- Evidence: `.omo/evidence/task-<N>-saleslens-wave3-ai-harness.<ext>` per todo (outside ulw-loop `.omo/evidence/`, inside `omo ulw-loop status --json`)

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. This wave is itself one parallel wave; sub-parallelism per Dependencies below.

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| 14 | 1 | - | 15,16 |
| 15 | - | - | 14,16 |
| 16 | 6 | - | 14,15 |

## Todos

- [ ] 14. Learned inference scaffold — regex+stat fallback + promotion gate harness
  What to do / Must NOT do: Keep MVP `src/main/java/com/shreyas/saleslens/service/inference/TypeDetectionService.java:24` chain `INTEGER->DECIMAL->BOOLEAN->DATE (detectDateFormat 8 patterns)->DATETIME->EMAIL->PHONE->CURRENCY_AMOUNT->CATEGORY[<20]->FREE_TEXT` + `SchemaInferenceService.java:34` statistical profiling (500 records, nullRate, uniqueCount, top10, min/max) — scaffold only per G-MOAT-01; build `research/learned_moat/` harness for future promote but NOT wired as default: Sherlock 1,588 feats x 180 cols =285k pre-DNN (MIT Sherlock 686k cols VizNet, 78 DBpedia, F1 0.89) + Sato CRF hybrid Sherlock+topic(LDA)+linear-chain CRF F1 0.925 +1.4s/6.4K tables=0.2ms/post-feature on 64c/512GB (excludes 1,588 extraction+500MB GloVe/PV, real 45s-45min on 180-col dirty) + TURL TinyBERT N=4 d=312 k=12 visibility matrix O((rows x cols)^2) 512-token ceiling truncates 180-col (1,440 tokens >512) avg 13 x 2 tiny tables (570k tables 80 epochs weeks/multi-GPU) + RECA needs related-table corpus Jaccard/topic (20 isolated CSVs signal zero) + Doduo 8 tokens/col but 512 ceiling; gates `research/G-MOAT-01-AI-EVAL.md:496-527` **L1 dirty-CRM hold-out (private not web-crawled >=20 tables >=5 x 100col 1 x 180col abbrev/locale) F1 > regex+stat +2pp (5 runs CI non-overlap) AND L2 p99<500ms per table incl. features CPU-warm OOM-free@8GB closure<=500MB AND L3 ablation with identical prefilter**; serving if promoted warm daemon pre-warmed GloVe/PV forkserver not per-window spawn, fallback regex on timeout/OOM (not hard fail). Must NOT ship TURL/Sato stub as default, must NOT 180-col without p99 gate.
  Parallelization: Wave 3 | Blocked by: 1 | Blocks: -
  References: `.omo/plans/research/G-MOAT-01-AI-EVAL.md:30-133 deffer + 468-527 gates` + `research/G-YAML-01.md moat stub` + `src/main/java/com/shreyas/saleslens/service/inference/TypeDetectionService.java:24` + `src/main/java/com/shreyas/saleslens/service/inference/SchemaInferenceService.java:34 getSourceSamples hook`
  Acceptance criteria: MVP still regex chain (not learned), `research/learned_moat/harness.py` exists with gate thresholds `+2pp & p99<500ms & OOM-free` + `learned_vs_regex_180col.md` benchmark template with private hold-out (never in prompt history), `RECA corpus` requirement documented as zero-signal on isolated CSVs, warm daemon pre-warmed closure vs per-window spawn decision.
  QA scenarios: happy regex fallback 180-col completes 12s (pass) + failure Sato stub without gate -> 45min feature extraction OOM 8GB (must be DEFERRED) + failure `col_14/__EMPTY_3` abbreviation blind F1 < regex (must not promote); Evidence `.omo/evidence/task-14-saleslens-final-build-plan.md` with chain code + harness + Sato 0.2ms post-feature note.
  Commit: N | chore(moat): scaffold learned gate harness (no default)

- [ ] 15. LLM factorial harness — `qwen3:8B vs GPT-4 x technique x retrieval` on hold-out
  What to do / Must NOT do: Implement `research/llm_factorial/` harness per `MODEL-EVAL-PROD-SPEC.md` Block B: models `{qwen3:8B, GPT-4}` x technique `{vanilla vs bidir+agg+prefilter (Def 3.1 bidirectional stable matching Gale-Shapley + sampling/aggregation over phrasing/layout perturbation + type prefilter Numeric/Text/Date/Boolean N->k >50% token cut)}` x retrieval `{off vs on vector+lexical, J/K held-out}` on `MIMIC-OMOP, Synthea-OMOP + >=1 retail/CRM dirty tabular (20-100 fields m:n+FK=PK)` metrics `accuracy@1+HitRate@3+F1+tokens x calls+$/mapping+p50/95/99` 5 runs stratified `<=15/16-49/>=50` interaction `p<0.10`; composite `F1+HitRate@1/@3+decisiveness` not 1:1-biased; contamination defense `TS-Guessing 52% ChatGPT/57% GPT-4 EM MMLU -> ~100% when contaminated` + `ConTAM 13 x 7 x 2` + `GSM8K -22.9% / MMLU -19% / HumanEval 8-18% overlap` + `OLMo2 detection fails` => hold-out private dirty-CRM corpus never in prompt history (Scalable 70B +15.2pp 77.44 vs 62.20 only at 70B not 7-9B, KG-RAG4SM 35.89%/69.20% small-Synthea contradicts H2 <=15 hurts/>=50 helps crossover ~15-30); reporting template `F1/HitRate1/3 + tokens_in x out + calls + $/mapping + p50/95/99` per `arxiv`; kill 7-9B locality if `GPT-4 single-pass >= 7-9B+technique Delta<=0 at matched harness or p>0.10`. Must NOT leakage-inflated health bench without dirty retail replication + J/K held-out, must NOT CSP NP-hard 1-1/e without Gale-Shapley, must NOT single `accuracy@K`.
  Parallelization: Wave 3 | Blocked by: - | Blocks: -
  References: `.omo/plans/research/G-MOAT-01-AI-EVAL.md:200-299 harness + 242-268 contamination` + `.omo/plans/MODEL-EVAL-PROD-SPEC.md:Block B` + `src/main/java/com/shreyas/saleslens/config/OllamaConfig.java:28 qwen3.5:9b` + `SaleslensApplication.java:10 @EnableScheduling` for cron not per-window
  Acceptance criteria: Harness exists with `qwen3:8B vs GPT-4` x 2 techniques x 2 retrieval x 3 strata =24 cells x 5 runs =120 runs on hold-out dirty-CRM private (never crawled), reports `accuracy@1+HitRate@3+F1+tokens x calls+$/mapping` per cell, `p<0.10` interaction test; kill clause documented and executed: if 7-9B+technique Delta<=0 vs GPT-4 single-pass -> locality killed -> hosted; private corpus provenance documented.
  QA scenarios: happy harness on 50-field stratum (pass compiles) + failure health-only MIMIC without dirty retail -> inflated 88% F1 (must be rejected, needs dirty) + failure single vanilla vs harness-matched (must kill 7-9B if Delta<=0); Evidence `.omo/evidence/task-15-saleslens-final-build-plan.md` with factorial matrix + token frontier + contamination memo.
  Commit: N | chore(eval): add LLM factorial harness

- [ ] 16. Production AI handling — standing Pod + probe + blocking AssetCheck + caches + fallback
  What to do / Must NOT do: Implement hosting `Ollama Q4_K_M 5.03GB (BF16 16.4GB) cold 1.2s vs vLLM FP16 4.8s on A10G -> per-window spawn 96min/day at 30s -> standing Pod s3fs+fuse+readiness probe 85% util <300ms p95 200 concurrent` only topology that passes (`MODEL-EVAL-PROD-SPEC.md Block C`); hallucination `CHOKE 9-43% Certainty Misalignment + 13/15 scorers <0.5 AUROC on knowledge gaps + k=1 retrieval amplifies` -> raw `LLM>heuristic` formally unsafe -> `hedged fallback chain qwen3:8B -> GPT-4 (error/timeout/low-calibrated) -> heuristic 0.70` with guard `hidden-state probe + bidirectional consistency + admit unknown->pending, k>=3 or abstain` + KV-prefix (stable template before breakpoint) + embedding (text,model-ver) + full-response exact-match + semantic lossy with tenant boundaries + fault injection p95/p99 -18% per CASCON; lifecycle `PipesSubprocessClient 12s->5s forkserver (3.11 ok, 3.12 broken #30893), blocking=True AssetCheck gates downstream` (`yield_for_execution` warm handle), `ConfigurableResource` + `AutomationCondition.on_cron` not per-window, versioned `model_version` artifact + `ledger.model_version` + rollback; heuristic-first `CopyOnWriteArrayList` stays `heuristic 1.0/0.85/0.70/0.55` then LLM only if `LLM>heuristic` via Ollama `qwen3.5:9b` until gates green (already in `SemanticMapperService.java:400 runLlmWithRetry 3 tries JSON mode`). Must NOT per-window spawn, must NOT raw confidence gate, must NOT Python 3.12 forkserver.
  Parallelization: Wave 3 | Blocked by: 6 | Blocks: -
  References: `.omo/plans/research/G-MOAT-01-AI-EVAL.md:321-454 prod + 535-548 gates` + `.omo/plans/MODEL-EVAL-PROD-SPEC.md:Block C` + `src/main/java/com/shreyas/saleslens/config/OllamaConfig.java:47` + `src/main/java/com/shreyas/saleslens/service/advisory/QualityExplanationService.java:94 @Async` + `src/main/java/com/shreyas/saleslens/service/SemanticMapperService.java:192 runLlmWithRetry`
  Acceptance criteria: `standing Pod` readiness probe p95 <300ms 200 concurrent (not per-window 1.2-9s), `CHOKE` mitigation probe+`bidirectional+ k>=3` tests show `AUROC >0.5` + `isValidMapping` still gates, `PipesSubprocessClient` not per-invocation spawn, `blocking=True` asset check gates downstream, `model_version` pinned in `replay_runs.model_version` + `environment_pins`; fallback chain latency logged `qwen3 -> GPT-4 -> heuristic` with hedge.
  QA scenarios: happy standing Pod 200 concurrent (pass <300ms) + failure per-window spawn 12s->5s vs 96min/day -> must be rejected + failure raw `LLM>heuristic` without probe -> CHOKE high-certainty hallucination (must fail `AssetCheck`); Evidence `.omo/evidence/task-16-saleslens-final-build-plan.md` with Pod manifest + CHOKE mitigation + fallback chain log.
  Commit: Y | feat(ai-prod): add standing Pod + probe + fallback

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
- Learned NEVER promoted without `F1+2pp & p99<500ms & OOM-free@8GB` on 180-col private hold-out (5 runs)
- 7-9B locality killed if `GPT-4 single-pass ≥ 7-9B+technique Δ≤0 or p>0.10`
- Standing Pod+probe+hedged fallback+KV caches are CLOSABLE patterns (not research) — prod LLM cutover gated

---
> Parent: `.omo/plans/saleslens-final-build-plan.md` (355 lines, 20 todos, hardened Appendix 8 patches) — this wave is a slice. Hardened Appendix applies to this wave's todos. Run `$start-work` per wave independently.
