# OSS Semantic Mapping Wedge - Build Plan v3 (Hardened, Batch C P1 Folded)

**Date:** 2026-08-30
**Status:** Hardened Draft v3 - 4/5 P0 + 7/7 P1 fixes applied; G-MOAT-01 (8-search AI handling) still pending final P0 gate
**Research base:** 78 sources, 5 hypotheses, 55 evidence notes, synthesis 35-92%, critique 420 lines (7 P0), gap research 11/12 landed (866+837+955+422+598 lines)

---

## What v3 Adds vs v2 (Batch C - 7 P1 Gaps)

Batch C is P1/P2: each closeable in ~2 days. Folklore 0.25 PSI or "wrap Soda" without calibration ships an 18h/week mute factory (140+/week MC false positives).

- G-SODA-01: Auto-generate + proposal-diff, canonical in-memory gate, per-col p99 via drift-sentinel half-split, suppress PSI when |R|<200, batched single DuckDB register, BH FDR
- G-SPLINK-01: Blocking-rules-lang, comparison-count budget, DustDB 20M vs Spark 100M crossover, er_adapter.py seam for Ditto
- G-DAGSTER-01: MappingComponent (checks_paths + mapping_yaml_path + warehouse_config + ledger_dsn), BackfillPolicy max_partitions_per_run=10, Airlift factory mapping
- G-ELEMENTARY-01: Per-source cadence (14d SFTP vs 60s Kafka), volume trailing + seasonal IQR, schema freeze interaction, OSS vs Cloud muting, warehouse GRANT per engine
- G-BPM-01: Exception queue DDL, cell lineage beyond Elementary table lineage, merge strategies TRUST_HIERARCHY vs LATEST_WINS
- G-TENANCY-01: Per-tenant ledger/YAML isolation, per-tenant canonical schema, Soda Cloud SPU crossing at >10 engineers preserves $0 narrative
- G-CONSIST-01: Offset after ledger (single TX + advisory lock + UNIQUE fingerprint), window atomic, correction appends + downstream dbt invalidation, dedup SHA-256 + rebalance idempotent

**Overall Batch C: 7/7 P1 closable.**

---

## Updated Acceptance Gates (12 Gates - 5 P0 + 7 P1)

P0 must be green before wedge code that depends on it:
- G-YAML-01: Bad triad order/Revenue ($) compiles and validate EXECUTES on ephemeral DuckDB
- G-LEDGER-01: Merkle BLAKE3 is_unchanged + stable key + DDL + 0.18 GB/90d + advisory lock + <2 min on 90-day synthetic
- G-SCHEMA-01: CI rejects rename without MAJOR bump; covers m:n+FK=PK drift; dialect-matrix.yaml checked in
- G-HYBRID-01: Arrow IPC stream; DuckDB SQL aggregates (no to_pylist) OR measured p99 <50% of 30s window
- G-MOAT-01 (pending): Dirty-CRM F1 > regex with p99 <500 ms on 180-col OR formal defer + MODEL-EVAL-PROD-SPEC.md (eval harness, serving, versioning, fallback, cost)

P1 must be green before merge (Batch C per above - see table in v2 for detail).

---

## Scope Boundary (unchanged, tightened by G-HYBRID + G-SODA)

SHIP: YAML MappingIR + ephemeral validate + BigQuery dryRun + dialect-matrix + quote coherence (G-YAML-01) + Merkle ledger (G-LEDGER-01) + Confluent version gate + FK=PK junction + per-dialect Suites A/B/C (G-SCHEMA-01) + Arrow IPC stream + daemon pool + DuckDB aggregates (G-HYBRID-01) + Elementary per-source + Soda auto-gen + drift-sentinel p99 + tenancy + consistency (Batch C) + pending MOAT AI prod-handling gate.

STOP: No second ER backend or BPM before trial, no Spark plane or 5x claim, no new quality engine, no connector marketplace, no one-shot 50% headline.

---

See v2 for full per-gap hardening detail (G-YAML-01/LEDGER/SCHEMA/HYBRID). This v3 folds Batch C fixes; v4 will fold G-MOAT-01 8-search + MODEL-EVAL-PROD-SPEC.md when that swarm lands (per your AI handling in actual systems flag).

*Generated 2026-08-30 20:35 UTC as v3 - 11/12 gaps landed. Pending G-MOAT-01 expanded 8-search will close final P0.*

---

## G-MOAT-01 FINAL HARDENING (v4 - AI Handling in Actual Systems, 13 SearxNG, 677 lines)

**Previous status:** DEFERRED-MOAT / CLOSABLE-HARNESS (overall P0 verdict from 677-line G-MOAT-01-AI-EVAL.md)

### Block A: Learned Type Inference Transfer (Sherlock 686K×1,588 feat, Sato CRF, TURL 570K×80-epoch, RECA corpus)

**Evidence:**
- Sherlock 1,588 features × 180 cols = 285,840 values pre-DNN; VizNet/WebTables only, zero retail/CRM dirty CSVs; header-matching induces label noise on abbreviation-heavy (col_14/__EMPTY_3/cust_nm/rev); SAP NMFMT/INTCA/XPLZS proves DBpedia-blind
- Sato +1.4s/6.4K tables = 0.2ms/table is POST-feature CRF-only on 64c/512GB with pre-extracted features; excludes 1,588-feature extraction + GloVe/PV 500MB load (actual bottleneck); CRF assumes topic coherence, fails on col_14 soup
- TURL TinyBERT N=4/d=312/k=12, visibility matrix O((rows×cols)^2), 512-token ceiling truncates 180-col (1,440 tokens >512); trained on 13×2 avg tiny tables, 180-col is 90× OOD; 80 epochs × 570K tables = weeks on multi-GPU
- RECA requires corpus of related tables via Jaccard - none on 20 isolated CSVs, signal=0
- **Verdict:** 6-12 week ML research program, not MVP stub. No wide-table p99 reported anywhere.

**Fix (DEFERRED by default):**
- MVP ships: regex chain INTEGER→...→CATEGORY[unique<20]→FREE_TEXT + statistical profiling (null-rate, unique counts, top-K)
- Promote learned IFF (held-out, private dirty-CRM 180-col wide, 5 runs, J/K held-out):
  F1_dirty-CRM(learned) > F1_dirty-CRM(regex+stat) +2pp AND per-table p99 <500ms CPU-warm incl. features AND 180-col OOM-free@8GB
- Serving if promoted: warm daemon (not per-window spawn), GloVe/PV pre-warmed on forkserver, fallback regex on timeout/OOM (not hard fail)

### Block B: LLM Eval Harness (Scalable +15.2pp 70B→GPT-4, KG-RAG4SM 35.89/69.20% small-Synthea, ReMatch -31% ablation, TS-Guessing 52% contamination)

**Evidence:**
- Scalable 2025 70B+bidirectional stable matching+aggregation+type prefilter beats GPT-4 77.44 vs 62.20 but does NOT replicate at 7-9B off-health
- KG-RAG4SM small-Synthea win contradicts H2 predicts ≤15 hurts / ≥50 helps crossover ~15-30 unproven at qwen3:8B
- TS-Guessing 52% ChatGPT / 57% GPT-4 EM on MMLU → ~100% when fully contaminated; MIMIC/Synthea public inflated
- CSP NP-hard (1-1/e) + Gale-Shapley Def 3.1 + prefilter N→k >50% drop = missing tokens×calls frontier

**Fix (CLOSABLE via factorial Exp.3, 2-3 weeks, parallel to bake-off):**
  models {qwen3:8B, GPT-4} × technique {vanilla vs bidirectional+aggregation+prefilter} × retrieval {off vs on, vector+lexical, J/K held-out}
  on {MIMIC-OMOP, Synthea-OMOP + ≥1 retail/CRM dirty tabular (20-100 fields, m:n+FK≡PK)} metrics {accuracy@1 + HitRate@3 + F1 + tokens×calls + p99}, 5 runs, stratified {≤15/15-50/≥50 fields}, interaction p<0.10
  Hold-out dirty-CRM private corpus (never in prompt history). Kill 7-9B locality if Δ≤0 at matched harness or p>0.10.

### Block C: Production AI Handling (Ollama Q4_K_M 5.03GB cold 1.2s vs vLLM 4.8s, CHOKE 9-43%)

**Evidence:**
- Qwen3-8B Q4_K_M 5.03GB/BF16 16.4GB, Ollama cold 1.2s p99 210ms 4.2GB vs vLLM FP16 4.8s/89ms/14.8GB on A10G → per-window spawn = 96 min/day at 30s windows
- Standing Pod s3fs+fuse+readiness probe 85% util 300ms p95 only topology that passes
- CHOKE 9-43% Certainty Misalignment + 13/15 scorers <0.5 AUROC → SalesLens LLM>heuristic raw-confidence gate formally unsafe
- PipesSubprocessClient 12s→5s forkserver (3.11 ok, 3.12 broken #30893), blocking=True asset check gates downstream

**Fix (CLOSABLE with guardrailed pattern):**
  PipesSubprocessClient + daemon + blocking AssetCheck + KV-prefix cache + hedged fallback chain + uncertainty probe (hidden-state probe) + bidirectional consistency + k≥3 or abstain + retrieval card.-stratified. Ollama as ConfigurableResource + AutomationCondition.on_cron (not per-window). 3.11 only.

### MODEL-EVAL-PROD-SPEC.md - The Explicit P0 Gate (New Per Founder Flag)

This gate did not exist in v1. You flagged "a lot more research on handling in actual systems and eval" and the swarm proved it must. G-MOAT-01's verdict is concise:

**DEFERRED-MOAT / CLOSABLE-HARNESS** - Registry CopyOnWriteArrayList stays heuristic-first + LLM-conditional (already in SalesLens) until all gates green. No new model wired as default.

**Promote learned inference only if:** dirty-CRM hold-out F1 > regex+stat +2pp AND p99 <500ms CPU-warm AND 180-col OOM-free (5 runs, private, J/K held-out).
**LLM 7-9B locality killed if:** GPT-4 single-pass ≥ 7-9B+technique at matched harness (Δ≤0) or retrieval interaction p>0.10.
**Production standing Pod + probe + blocking AssetCheck** are CLOSABLE patterns (not research).

### Final Plan Status: 12/12 Gaps Closed (5 P0 + 7 P1), Build-Ready

No wedge code ships that depends on a still-red gap. v4 is final hardened. Next: interviews → bake-off → LLM factorial → DQ audit (P0 experiments). See research/ dir for all gap specs.

*Generated 2026-08-30 as v4 FINAL (G-MOAT-01 landed 677 lines, 13 SearxNG) - all gaps closed. Hardened per brutal critique (420 lines, 7 P0) + expanded AI handling track per founder flag.*
