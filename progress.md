# SalesLens Implementation Progress

## Phase 4 — Semantic Field Mapping
**Status: COMPLETED (v1 — LLM-first → v2 — heuristic-primary)**

### v1 (original): LLM-first, heuristic fallback
- Register Kaggle Superstore as source
- See all fields auto-mapped via Ollama LLM
- Confirm/override/ignore mappings via API

### v2 (updated): Heuristic-primary, optional LLM advisory
**Refactored to align with new project.md (commit 2bc7ee8)**
- Heuristic chain now runs **first** (exact → Levenshtein → token overlap → type fallback)
- LLM is optional advisory — runs only if both conditions:
  1. Ollama endpoint is available and configured
  2. LLM response passes registry validation (entity/field exists in canonical registry)
- LLM result used **only** if its confidence > heuristic confidence
- Retry logic: up to 3 attempts (initial + 2 retries) with error feedback
- Structured output via `ResponseFormat` + JSON schema enforcement on `ChatRequest`
- Temperature forced to 0.0 for deterministic output

## Phase 5 — Quality Engine
**Status: COMPLETED (v1 — 6 checkers → v2 + baseline drift detection + LLM explanations)**

### v1 (original): Six quality checkers
- Completeness, Validity, Uniqueness, Consistency, Timeliness, Accuracy
- Weighted scoring with letter grades (A–F)
- REST API for issues, scores, and acknowledgements
- Rejected records for CRITICAL issues

### v2 (updated): Statistical baseline drift detection + LLM quality explanations

**New: ProfilingService** (`service/quality/ProfilingService.java`)
- Compares current batch FieldProfile statistics against historical baselines
- Three drift dimensions detected:
  - **Null rate drift** → COMPLETENESS issue (threshold: >20pp shift from historical avg)
  - **Value distribution skew** → VALIDITY issue (threshold: >50% new values in top-10)
  - **Range expansion** → ACCURACY issue (threshold: beyond 3σ of historical mean)
- Cold-start guard: requires minimum 3 batches of profiling data before activating
- Integration: runs automatically after record-level checkers in `QualityEngineService.runQualityEngine()`

**New: ProfilingService integration in QualityEngineService**
- `ProfilingService` injected as optional dependency
- Drift issues added to `allIssues` list after checker loop, before saving

**New: QualityExplanationService** (`service/advisory/QualityExplanationService.java`)
- Optional async LLM-powered quality explanation generation
- Generates human-readable explanations with remediation suggestions
- Best-effort, `@Async`, never blocks pipeline
- Active only under `!test` profile (matches OllamaConfig pattern)
- Graceful fallback: returns null if LLM unavailable or fails

## Phase 6 — Conflict Detection + Canonical Load
**Status: NOT STARTED**

### Tasks remaining:
1. CanonicalLoadService — upsert records to canonical tables
2. ConflictDetectionService — field-level cross-source conflict detection
3. Batch resolution thresholds — trust gap, importance-based auto-resolve
4. LineageService — record-level lineage tracking
5. ConflictController — GET/PUT endpoints
6. Canonical entities (Customer, Product, Order, etc.)

## Phase 7+ — Future Phases
- Phase 7: Excel + JDBC connectors
- Phase 8: Kafka live stream connector
- Phase 9: Redis caching + API polish
- Phase 10: React frontend
- Phase 11: Docker Compose + README
