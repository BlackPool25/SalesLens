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
