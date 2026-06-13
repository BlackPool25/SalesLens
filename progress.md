# SalesLens Phase 4 Progress Report: Semantic Field Mapping

## Summary of Accomplishments
We have successfully completed **Phase 4 (Semantic Field Mapping)** of the SalesLens multi-source sales data unification platform. This phase implements automated schema matching of raw ingested CSV columns to canonical business entities, exposes REST APIs for manual confirmation and override actions, and transforms raw payloads into flat canonical structures ready for unified storage.

### 1. Domain Entities & Database Mapping
* **FieldMapping JPA Entity**: Created the `FieldMapping` model to persist mapping records between raw source columns and canonical model fields.
* **Fields persisted**: `id`, `source` (DataSource reference), `sourceFieldName`, `canonicalEntity`, `canonicalField`, `confidence`, `status` (`PENDING`, `AUTO_CONFIRMED`, `IGNORED`), and standard audit fields.
* **Flyway Schema Evolution**: Integrated database migration scripts to provision the `field_mappings` table.

### 2. Semantic Mapper Service Heuristics
* Implemented a multi-tiered similarity matcher in `SemanticMapperService`:
  * **Exact Match (1.00)**: Normalized match on exact column name or predefined synonyms.
  * **Levenshtein Distance (0.85)**: Tolerates typos and spelling variations (edit distance <= 2).
  * **Token Overlap (0.70)**: Matches partial token sets (Jaccard similarity >= 0.5) to capture word overlaps.
  * **Type Match Fallback (0.55)**: Maps columns sharing matching data types (e.g. `DATE` matching `order_date`) when spelling is unrecognizable.
* **Drift Safety**: Integrated mapping generation into the Batch Ingestion pipeline. Upon schema drift detection, previous mappings are deleted and new ones automatically generated.

### 3. REST Controller API Layer
* Designed `MappingController` endpoints with Spring Security authorization for manual override capability:
  * `GET /api/v1/sources/{sourceId}/mappings`: Retrieve all field mappings.
  * `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/confirm`: Confirm mapping as `AUTO_CONFIRMED`.
  * `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/override`: Manually bind a field to a custom entity/column.
  * `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/ignore`: Discard a column mapping.

### 4. Payload Transformation Service
* Created `TransformationService` to consume raw ingested records and translate them into a singularized canonical representation (e.g., converting a raw payload's `Sales` cell value into `{"order.total_amount": "123.45"}` using confirmed mappings).

### 5. Edge Case Hardening & Real-World Safety
* **Nameless Headers**: Prevented mapping of nameless/blank columns by immediately ignoring headers that are empty or null.
* **Exception Safety**: transformation processes recover gracefully from invalid JSON payloads, missing properties, and null entries, returning clean empty structures instead of crashing downstream processes.
* **Auto-Conversion**: Numbers and booleans inside raw JSON are automatically stringified during normalization and translation.

---

## Verification Results

### Unit & Integration Tests
Created a comprehensive Spring Boot MockMvc test suite and expanded service-level unit tests:
* `MappingControllerTest`: Runs MockMvc tests mocking `CustomUserDetailsService` and `JwtFilter` to verify endpoint authentication, parameter constraints, and payload serialization.
* `SemanticMapperServiceTest`: Tests exact, Levenshtein, token overlap, and fallback matches, alongside null/empty column handling.
* `TransformationServiceTest`: Tests parser safety, malformed JSON recovery, singularization mappings, and numeric type handling.
* **Status**: **ALL PASSED (41/41 tests green)**.

### End-to-End Integration Test
We validated the entire pipeline utilizing the updated `verify_e2e.py` script against the application stack:
1. Registered new user and generated JWT.
2. Ingested `superstore_v1.csv` and verified mappings auto-generated (e.g. `Sales` mapping to `orders.total_amount`).
3. Executed manual confirm, override, and ignore actions via REST endpoints.
4. Uploaded `superstore_v2.csv` containing drift and verified automatic regeneration.
* **Status**: **ALL PASSED**.
