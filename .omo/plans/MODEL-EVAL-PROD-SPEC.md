# MODEL-EVAL-PROD-SPEC.md - P0 Gate: AI Model Handling in Actual Systems

**Date:** 2026-08-30 | **P0 GATE** | No learned inference wired as default until green
**Source:** G-MOAT-01-AI-EVAL.md (677/95175, 13 SearxNG, 80+ hits expanded per founder flag)
**Trigger:** Founder: "a lot more research on handling in actual systems and eval"
**Verdict:** DEFERRED-MOAT / CLOSABLE-HARNESS - heuristic-first + LLM-conditional stays until gates green

---

## A. LEARNED INFERENCE - DEFERRED (180-col dirty retail/CRM wide tables)

**Research (Sherlock 686K×1,588 feat, Sato CRF 0.2ms POST-feature on 64c/512GB, TURL TinyBERT 570K×80-epoch 512-token ceiling, RECA corpus needs related tables):**
Together = 6-12 week ML research program, not MVP stub. 1,588×180=285K features, no wide-table p99 anywhere, abbreviation NMFMT/col_14/__EMPTY_3 blind.

**MVP ships:** regex INTEGER→...→CATEGORY[unique<20]→FREE_TEXT + statistical profiling (scaffold only).
**Promote IFF (private dirty-CRM 180-col, 5 runs, J/K held-out, CPU-warm):**
F1_dirty-CRM(learned) > F1_dirty-CRM(regex+stat) +2pp AND p99 <500ms incl. features AND OOM-free@8GB
Fallback: regex on timeout/OOM (not hard fail). Serving if promoted: warm DAEMON pre-warmed GloVe/PV, not per-window spawn.

## B. LLM EVAL HARNESS - CLOSABLE ONLY VIA FACTORIAL

Contamination TS-Guessing 52% → ~100%; KG-RAG4SM 35.89/69.20% small-Synthea contradicts H2 ≤15 hurts / ≥50 helps; CSP NP-hard 1-1/e + Gale-Shapley + N→k >50% = missing tokens×calls frontier.

**Required factorial (Exp.3, P0, 2-3w, parallel to bake-off):**
models {qwen3:8B, GPT-4} × technique {vanilla vs bidir+agg+prefilter} × retrieval {off vs on, J/K held-out} on MIMIC/Synthea + ≥1 retail/CRM dirty tabular (20-100 fields, m:n+FK≡PK) metrics {accuracy@1 + HitRate@3 + F1 + tokens×calls + p99} 5 runs stratified ≤15/15-50/≥50 p<0.10
HOLD-OUT private corpus never in prompt history. Kill 7-9B locality if Δ≤0 or p>0.10.

## C. PRODUCTION AI HANDLING

**Hosting:** Q4_K_M 5.03GB cold 1.2s vs vLLM 4.8s on A10G -> 96 min/day if per-window -> standing Pod s3fs+fuse+readiness 85% util 300ms p95 only topology
**Hallucination:** CHOKE 9-43% + 13/15 scorers <0.5 AUROC -> raw LLM>heuristic formally unsafe -> hidden-state probe + bidirectional consistency + k>=3 or abstain
**Lifecycle:** PipesSubprocessClient 12s→5s forkserver (3.11 ok/3.12 broken), blocking AssetCheck, ConfigurableResource + AutomationCondition.on_cron, versioned artifact + ledger model_version + rollback

**Overall:** Ship learned only if F1+2pp & p99<500ms & OOM-free; LLM 7-9B killed if Δ≤0; standing Pod + probe are CLOSABLE patterns.

*Source: G-MOAT-01-AI-EVAL.md (677/95175). Levels L1-L3.*
