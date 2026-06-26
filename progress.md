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
**Status: COMPLETED**

### Completed:
1. Canonical entities (Customer, Product, Order, OrderLineItem, Salesperson, Region) — 6 tables via Flyway
2. ConflictDetectionService — field-level cross-source conflict detection with trust-gap/latest-wins/flagged-for-review strategies
3. CanonicalLoadService — multi-pass upsert ordered by trust score + completeness
4. LineageService — record-level lineage tracking via `CanonicalLineage` entity
5. ConflictController — GET (list + by-id), PUT (resolve + suppress) endpoints
6. CanonicalController — 6 paginated read-only endpoints for all canonical entities

## Phase 7 — Excel + JDBC Connectors
**Status: COMPLETED**
- Excel (`.xlsx`) ingestion via Spring Batch + Apache POI
- JDBC (Postgres/MySQL) ingestion with encrypted config, cron scheduling, background poller
- REST endpoints: `/api/v1/ingest/excel`, `/api/v1/ingest/jdbc/{sourceId}`

## Phase 8 — Kafka Live Stream Connector
**Status: COMPLETED**
- `sales.live` topic consumer with per-message commits and DLT error handling
- Source system cache with 5-minute TTL
- SHA-256 dedup constraint on staged records
- Window-based batch processor (30s default) with `StreamPipelineScheduler`
- `/scripts/kafka_test_producer.py` for load testing

## Phase 9 — Redis Caching + API Polish
**Status: IN PROGRESS**

### Completed:
1. **Springdoc/Swagger**: OpenAPI 3.0 docs at `/swagger-ui.html`, `/api-docs` (springdoc-openapi 3.0.3)
2. **Redis caching**: `@EnableCaching`, `CacheConfig` with 15/30-min TTL regions, `QualityCacheService` for quality/conflict caching
3. **`@PreAuthorize` on all controllers**: Role-based access (ADMIN/ANALYST for read, ADMIN for ingestion/schema mutations)
4. **Pagination on all list endpoints**: `@PageableDefault` with 20-item pages, sorted by `createdAt`
5. **DB-level conflict filtering**: Replaced in-memory stream filter with `ConflictRecordRepository.findFiltered()` JPQL query
6. **CanonicalController**: 6 paginated read-only endpoints (`/api/v1/canonical/customers`, `/products`, `/orders`, `/order-line-items`, `/salespersons`, `/regions`)

### Remaining:
- Test infrastructure fix: CacheManager bean for `@WebMvcTest` slices
- QualityCacheService unit tests
- Full scenario QA

## Phase 10+ — Future Phases
- Phase 10: React frontend
- Phase 11: Docker Compose + README polish
