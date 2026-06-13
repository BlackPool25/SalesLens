# Phase 3 — Schema Inference Builder Prompt

## Instructions for the builder agent

You are a **builder agent** implementing Phase 3 (Schema Inference) of the SalesLens multi-source sales data unification platform. Follow the methodology below strictly.

---

### Agentic Pipeline Rules

1. **Plan Gate First**: Before touching any file, output a brief plan with your key assumptions tagged as `[ASSUMPTION]`. The plan must list: which files you'll create, which you'll modify, and your type-detection chain order. Get approval before proceeding.

2. **Context7 Before Implementation**: Before making ANY claims about Spring Batch 6, Spring Data JPA, or any library API, use the **context7_query-docs** tool to verify the API exists and works as you expect. Do not guess at method signatures, annotation APIs, or import paths.

3. **Spec-Driven Execution**: Each task follows: Goal → Implementation → Verification. Do NOT add speculative features, defensive code, or "improvements" beyond the spec.

4. **Stop Condition**: You are done when `mvn compile` succeeds AND the test described in Acceptance Criteria passes (manual verification using the instructions given). Do NOT keep editing after this.

5. **One File Per Tool Call**: When writing code, use Write tool for new files and Edit tool for modifications. Read any file before editing it.

6. **Follow Existing Conventions**: This project uses:
   - Lombok `@Getter` `@Setter` `@RequiredArgsConstructor` on all entities/services
   - MapStruct for DTO mapping (componentModel = "spring")
   - `@Entity` with `@Table(name = "snake_case")` for all entities
   - UUID PKs with `@GeneratedValue(strategy = GenerationType.UUID)`
   - Package: `com.shreyas.saleslens`
   - JPA repositories extending `JpaRepository`
   - No comments in code
   - Specific import paths for Spring Batch 6 (e.g., `org.springframework.batch.core.job.JobExecution`, `org.springframework.batch.core.job.Job`)

7. **Verification Loop**: Run `mvn compile` after each significant change. It must pass. If it fails, fix the compile error before moving on.

---

### Project Context

- **Stack**: Spring Boot 4.0.6, Java 25, Spring Batch 6.0.3, Postgres, JPA
- **Package**: `com.shreyas.saleslens`
- **Port**: 8080 (Docker maps 8500 → 8080)
- **Existing Migrations (do NOT modify)**: V3 = `source_schemas` + `source_schema_fields`, V6 = `data_profiles` + `field_profiles`
- **Phase 2 already done**: CSV ingestion creates StagedRecords via Spring Batch. `IngestionController.uploadCsv()` → `IngestionOrchestrator.ingestCsv()` → Spring Batch job with `CsvItemProcessor` + `StagingItemWriter`
- **Auth**: JWT required (existing filter). Use `@Transactional(readOnly = true)` on controllers to handle lazy loading.

---

### Context Files (read these before starting)

Read these files to understand existing patterns:

- `project.md` — Phase 3 specification sections: FR-03, Build Plan Phase 3, package structure, canonical schema
- `database-schema.md` — source_schemas, source_schema_fields, data_profiles, field_profiles table definitions
- `pom.xml` — existing dependencies
- `src/main/resources/db/migration/V3__source_schema.sql` — existing schema DDL
- `src/main/resources/db/migration/V6__data_profiles.sql` — existing profile DDL
- `src/main/java/com/shreyas/saleslens/model/enums/SourceType.java` — enum convention
- `src/main/java/com/shreyas/saleslens/model/enums/JobStatus.java` — enum convention
- `src/main/java/com/shreyas/saleslens/model/DataSource.java` — entity conventions (JdbcTypeCode JSONB, timestamps, etc.)
- `src/main/java/com/shreyas/saleslens/model/StagedRecord.java` — entity conventions
- `src/main/java/com/shreyas/saleslens/repository/StagedRecordRepository.java` — repository convention
- `src/main/java/com/shreyas/saleslens/controller/IngestionController.java` — controller conventions
- `src/main/java/com/shreyas/saleslens/controller/JobController.java` — controller conventions (esp. @Transactional(readOnly = true))
- `src/main/java/com/shreyas/saleslens/batch/csv/CsvIngestionJobListener.java` — where SchemaInferenceService must be called
- `src/main/java/com/shreyas/saleslens/mapper/DataSourceMapper.java` — MapStruct convention
- `src/main/java/com/shreyas/saleslens/batch/csv/CsvBatchConfig.java` — batch config pattern
- `src/main/java/com/shreyas/saleslens/dto/CreateSourceRequest.java` — DTO convention
- `src/main/java/com/shreyas/saleslens/dto/DataSourceResponse.java` — response DTO convention

---

## Goal

Implement Phase 3 — Schema Inference. After a CSV ingestion job completes, the system must automatically infer the schema (column types, null rates, statistics) of the staged data and persist it.

---

## Scope

### DO create:
1. `com.shreyas.saleslens.model.enums.InferredType` enum
2. `com.shreyas.saleslens.model.SourceSchema` entity (maps to `source_schemas` table)
3. `com.shreyas.saleslens.model.SourceSchemaField` entity (maps to `source_schema_fields` table)
4. `com.shreyas.saleslens.model.DataProfile` entity (maps to `data_profiles` table)
5. `com.shreyas.saleslens.model.FieldProfile` entity (maps to `field_profiles` table)
6. `com.shreyas.saleslens.repository.SourceSchemaRepository`
7. `com.shreyas.saleslens.repository.SourceSchemaFieldRepository`
8. `com.shreyas.saleslens.repository.DataProfileRepository`
9. `com.shreyas.saleslens.repository.FieldProfileRepository`
10. `com.shreyas.saleslens.service.inference.TypeDetectionService`
11. `com.shreyas.saleslens.service.inference.SchemaInferenceService`
12. `com.shreyas.saleslens.controller.SchemaController`
13. `com.shreyas.saleslens.dto.SchemaResponse` — response DTO

### DO modify:
- `CsvIngestionJobListener.java` — call `SchemaInferenceService.runInference(jobId)` at end of `afterJob` when status is COMPLETED

### DO NOT touch:
- Any migration SQL files (V1–V11 are final)
- `pom.xml`
- `application.yaml`
- Existing model entities (DataSource, IngestionJob, StagedRecord) — unless you need to add a JPA relationship to SourceSchema
- Auth/Security files
- Docker files

---

## Acceptance Criteria

1. `mvn compile` succeeds with zero errors
2. After uploading a CSV (e.g., Kaggle Superstore), the system infers schema automatically:
   - Each column gets a type (e.g., "Sales" → DECIMAL, "Order Date" → DATE, "Row ID" → INTEGER, "Ship Mode" → CATEGORY, "Customer Name" → FREE_TEXT)
   - SourceSchema + SourceSchemaField records exist in DB
   - DataProfile + FieldProfile records exist in DB with null_rate, unique_count, top_values, min/max
3. `GET /api/v1/sources/{sourceId}/schema` returns the inferred schema with types and field stats
4. Upload the CSV again after removing one column: a new SourceSchema version is created (version=2, previous marked SUPERSEDED)

---

## Detailed Implementation Spec

### 1. `InferredType` enum

Location: `com.shreyas.saleslens.model.enums.InferredType`

Values: `INTEGER`, `DECIMAL`, `BOOLEAN`, `DATE`, `DATETIME`, `EMAIL`, `PHONE`, `CURRENCY_AMOUNT`, `CATEGORY`, `FREE_TEXT`

### 2. `SourceSchema` entity

Table: `source_schemas` (from V3 migration)

| Column | Mapping |
|--------|---------|
| id | UUID PK, auto-generated |
| source_id | `@ManyToOne(fetch = LAZY) → DataSource` |
| version | int, default 1 |
| status | String, default "ACTIVE" |
| created_at | Instant, @PrePersist |

Create a static constant for status values: `public static final String STATUS_ACTIVE = "ACTIVE"`, `public static final String STATUS_SUPERSEDED = "SUPERSEDED"`.

### 3. `SourceSchemaField` entity

Table: `source_schema_fields` (from V3 migration)

| Column | Mapping |
|--------|---------|
| id | UUID PK, auto-generated |
| schema_id | `@ManyToOne(fetch = LAZY) → SourceSchema` |
| field_name | String |
| inferred_type | `@Enumerated(STRING) → InferredType` |
| detected_format | String, nullable |
| nullable | boolean, default true |
| sample_values | String (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`), nullable |
| created_at | Instant, @PrePersist |

### 4. `DataProfile` entity

Table: `data_profiles` (from V6 migration)

| Column | Mapping |
|--------|---------|
| id | UUID PK, auto-generated |
| source_id | `@ManyToOne(fetch = LAZY) → DataSource` |
| schema_id | `@ManyToOne(fetch = LAZY) → SourceSchema` |
| job_id | `@ManyToOne(fetch = LAZY) → IngestionJob` |
| total_records | int |
| created_at | Instant, @PrePersist |

### 5. `FieldProfile` entity

Table: `field_profiles` (from V6 migration)

| Column | Mapping |
|--------|---------|
| id | UUID PK, auto-generated |
| profile_id | `@ManyToOne(fetch = LAZY) → DataProfile` |
| field_name | String |
| null_rate | BigDecimal (precision=5, scale=4) |
| unique_count | int |
| top_values | String (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`), nullable |
| min_value | String, nullable |
| max_value | String, nullable |
| sample_values | String (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`), nullable |
| created_at | Instant, @PrePersist |

### 6. `TypeDetectionService`

```java
@Component
public class TypeDetectionService {

    /**
     * Detect the inferred type from a list of sample string values.
     * Detection chain (first match wins):
     * 1. If all non-null values parse as Integer → INTEGER
     * 2. If all parse as Decimal → DECIMAL
     * 3. If all are "true"/"false" (case-insensitive) → BOOLEAN
     * 4. If all parse as Date with a common format → DATE (call detectDateFormat)
     * 5. If all parse as DateTime → DATETIME
     * 6. If all match email regex → EMAIL
     * 7. If all match phone regex → PHONE
     * 8. If all match currency pattern → CURRENCY_AMOUNT
     * 9. If unique non-null values < 20 → CATEGORY
     * 10. Else → FREE_TEXT
     */
    public InferredType detectType(List<String> sampleValues) { ... }

    /**
     * Detect date format pattern from sample date string values.
     * Try parsing against common patterns in order:
     * - "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd'T'HH:mm:ss",
     *   "MMM d, yyyy", "dd-MMM-yyyy", "yyyy/MM/dd", "dd.MM.yyyy"
     * Return the first pattern where ALL non-null samples parse successfully.
     * Return null if no pattern matches all.
     */
    public String detectDateFormat(List<String> sampleValues) { ... }
}
```

Key implementation details:
- Null/empty values in sampleValues should be SKIPPED (not counted as failures)
- Date detection must return the java.time.format pattern string
- Use `DateTimeFormatter.ofPattern(pattern).parse(value)` for date checking, not `DateUtils`
- Parse methods should be `private static` utility methods
- Integer: try `Integer.parseInt(value)` (no decimals, no leading zeros beyond 1)
- Decimal: try `new BigDecimal(value)`
- Boolean: `value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")`
- Email: `Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")`
- Phone: `Pattern.compile("^\\+?[0-9\\-\\s().]{7,20}$")`
- Currency: `Pattern.compile("^[\\$€£¥]\\s*\\d+(\\.\\d{1,2})?$|^\\d+(\\.\\d{1,2})?\\s*[\\$€£¥]$")`
- CATEGORY: count distinct non-null values, if < 20 → CATEGORY

### 7. `SchemaInferenceService`

```java
@Service
@RequiredArgsConstructor
public class SchemaInferenceService {

    private final StagedRecordRepository stagedRecordRepository;
    private final SourceSchemaRepository sourceSchemaRepository;
    private final SourceSchemaFieldRepository sourceSchemaFieldRepository;
    private final DataProfileRepository dataProfileRepository;
    private final FieldProfileRepository fieldProfileRepository;
    private final TypeDetectionService typeDetectionService;
    private final IngestionJobRepository ingestionJobRepository;

    /**
     * Run schema inference for a completed ingestion job.
     * 1. Load the job and source
     * 2. Check if a previous ACTIVE SourceSchema exists for this source
     * 3. Sample up to 500 StagedRecords from this job
     * 4. Collect all field names from the JSONB raw_payload
     * 5. For each field: collect sample values, run TypeDetectionService
     * 6. Compute stats: null_rate, unique_count, top_10_values, min/max (numeric only)
     * 7. Create SourceSchema (version = previous max version + 1)
     * 8. Create SourceSchemaField per field
     * 9. Mark previous ACTIVE schema as SUPERSEDED
     * 10. Create DataProfile + FieldProfile records
     * 11. If no previous schema exists or drift detected, create new version
     */
    @Transactional
    public void runInference(UUID jobId) { ... }

    /**
     * Detect drift: compare current schema to previous ACTIVE schema.
     * Drift = fields added, fields removed, or field type changed.
     */
    private boolean hasDrifted(SourceSchema current, SourceSchema previous) { ... }

    /**
     * Parse raw_payload JSON string into a Map<String, String>.
     * Use Jackson ObjectMapper.
     */
    private Map<String, String> parsePayload(String json) { ... }
}
```

Sampling logic:
- Use a custom `@Query` on StagedRecordRepository to select up to 500 records for a given jobId
- If you prefer not to add a custom query, use `stagedRecordRepository.findAll()` and filter in-memory (but this is inefficient — prefer a query method)

Repository query to add:
```java
// In StagedRecordRepository:
List<StagedRecord> findByJobId(UUID jobId, Pageable pageable);
```

Statistics computation:
- `null_rate`: count of null/empty values in field / total records sampled
- `unique_count`: count of distinct non-null values
- `top_values`: collect top 10 most frequent values as JSON array string
- `min_value`: min string value (for numeric fields, compare as BigDecimal)
- `max_value`: max string value
- `sample_values`: up to 5 representative non-null values as JSON array string

### 8. Repositories

All follow standard `JpaRepository<Entity, UUID>` pattern:

- `SourceSchemaRepository` — add query methods:
  - `Optional<SourceSchema> findBySourceIdAndStatus(UUID sourceId, String status)` — to get current ACTIVE schema
  - `List<SourceSchema> findBySourceIdOrderByVersionDesc(UUID sourceId)` — for drift history
  - `Optional<SourceSchema> findTopBySourceIdOrderByVersionDesc(UUID sourceId)` — to get latest version
- `SourceSchemaFieldRepository`
  - `List<SourceSchemaField> findBySchemaId(UUID schemaId)`
- `DataProfileRepository`
  - `Optional<DataProfile> findTopBySourceIdOrderByCreatedAtDesc(UUID sourceId)`
- `FieldProfileRepository`
  - `List<FieldProfile> findByProfileId(UUID profileId)`

### 9. `SchemaController`

```java
@RestController
@RequestMapping("/api/v1/sources/{sourceId}/schema")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchemaController {

    @GetMapping
    public ResponseEntity<SchemaResponse> getCurrentSchema(@PathVariable UUID sourceId) { ... }

    @GetMapping("/drift")
    public ResponseEntity<List<SchemaResponse>> getSchemaHistory(@PathVariable UUID sourceId) { ... }
}
```

- `getCurrentSchema`: find ACTIVE schema for source → return as SchemaResponse with fields
- `getSchemaHistory`: return all schema versions ordered by version desc
- Use `@Transactional(readOnly = true)` at class level for lazy loading
- Return 404 if no schema found

### 10. `SchemaResponse` DTO

```java
@Getter
@Setter
public class SchemaResponse {
    private UUID id;
    private int version;
    private String status;
    private List<FieldResponse> fields;
    private String inferredAt;

    @Getter
    @Setter
    public static class FieldResponse {
        private String fieldName;
        private String inferredType;
        private String detectedFormat;
        private boolean nullable;
        private List<String> sampleValues;
    }
}
```

Use MapStruct if you want, but a manual mapper in the controller or service is also fine since this is a nested structure.

### 11. Wiring into the pipeline

In `CsvIngestionJobListener.afterJob()`, after setting status to COMPLETED, add:

```java
if (job.getStatus() == JobStatus.COMPLETED) {
    try {
        schemaInferenceService.runInference(ingestionJobId);
    } catch (Exception e) {
        log.warn("Schema inference failed for job {}: {}", ingestionJobId, e.getMessage());
    }
}
```

Inject `SchemaInferenceService` into `CsvIngestionJobListener` via constructor injection (Lombok `@RequiredArgsConstructor`).

---

## Verification Steps

1. Run `mvn compile` — must pass
2. Verify all new files exist with correct package structure
3. Verify all entities map to correct table names
4. Verify the controller uses correct endpoint paths
5. Review that the type detection chain order matches the spec exactly
6. Review that null/empty values are properly handled in sampling and type detection
7. Confirm CsvIngestionJobListener calls SchemaInferenceService only on COMPLETED status
8. Confirm SourceSchema version increments correctly on drift
