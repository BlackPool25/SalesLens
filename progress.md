# SalesLens Phase 3 Progress Report

## Summary of Accomplishments
We have successfully implemented **Phase 3 (Schema Inference)** of the SalesLens platform, automating column type detection, profiling statistics calculation, and schema drift detection for ingested CSV datasets.

### 1. Domain Entities & Database Mapping
* **Entities Created**: `SourceSchema`, `SourceSchemaField`, `DataProfile`, `FieldProfile`.
* **Repositories Created**: `SourceSchemaRepository`, `SourceSchemaFieldRepository`, `DataProfileRepository`, `FieldProfileRepository`.
* **Schema Evolution**: Automated standard schema persistence and relational mapping under PostgreSQL.

### 2. Type Detection Chain (`TypeDetectionService`)
* Implemented a hierarchical detection chain:
  `INTEGER` → `DECIMAL` → `BOOLEAN` → `DATE` → `DATETIME` → `EMAIL` → `PHONE` → `CURRENCY_AMOUNT` → `CATEGORY` → `FREE_TEXT`
* Padded identifiers (e.g., zip codes) are prevented from being parsed as decimals by rejecting leading zeros (except for numbers like `0.x`).
* Date format detection checks for common patterns (`yyyy-MM-dd`, `MM/dd/yyyy`, `dd-MM-yyyy`, ISO-8601).
* Categories are determined dynamically if the count of unique non-null values in the sampled record set is `< 20`; otherwise, they fall back to `FREE_TEXT`.

### 3. Schema Inference & Drift Detection (`SchemaInferenceService`)
* **Sampling**: Samples the first 500 records paginated from the staging tables.
* **Drift Check**: Compares the newly inferred columns (names and types) with the currently `ACTIVE` version.
* **Versioning**: If drift is detected:
  1. A new version of `SourceSchema` is created (auto-incremented).
  2. The previous active version is set to `SUPERSEDED`.
  3. The new schema version is marked `ACTIVE`.
* **Data Profiling**: Calculates `null_rate`, `unique_count`, `top_values`, and `min`/`max` values for each column and stores them in `FieldProfile`.

### 4. Integration
* Tied schema inference to the batch processing lifecycle by registering `SchemaInferenceService` inside `CsvIngestionJobListener`. Schema inference runs asynchronously immediately upon `JobStatus.COMPLETED`.
* Created standard API endpoints in `SchemaController` for:
  * Fetching the active schema (`GET /api/v1/sources/{sourceId}/schema`).
  * Fetching the full schema drift history (`GET /api/v1/sources/{sourceId}/schema/drift`).

---

## Verification Results

### Unit Tests
We created and ran a comprehensive set of unit tests:
* `TypeDetectionServiceTest`: Validates every rule of the detection chain, including leading zeros edge cases, category boundaries, emails, phone numbers, and currencies.
* `SchemaInferenceServiceTest`: Mock-tests schema creation, profiling stats, version incrementation, and drift detection.
* **Status**: **ALL PASSED (17/17 tests green)**.

### End-to-End Integration Test
We wrote a Python E2E integration script (`verify_e2e.py`) to test the flow against a live Docker environment:
1. Registers a new user.
2. Obtains a Bearer JWT Token.
3. Creates a new CSV data source.
4. Generates and uploads a 22-row CSV (`superstore_v1.csv`).
5. Verifies schema is successfully inferred (`Row ID` -> `INTEGER`, `Customer Name` -> `FREE_TEXT`, `Ship Mode` -> `CATEGORY`, `Order Date` -> `DATE`, `Sales` -> `DECIMAL`).
6. Generates and uploads a modified CSV (`superstore_v2.csv`) with the `Sales` column removed.
7. Polls job to completion and verifies that a new `ACTIVE` version (version 2) is created, and version 1 is marked `SUPERSEDED`.
* **Status**: **ALL PASSED**.
