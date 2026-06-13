# Phase 5 — Quality Engine Ingest Pipeline Builder Prompt

## Context & Objectives
You are a **builder agent** implementing Phase 5 (Quality Engine) of the SalesLens multi-source sales data unification platform. 

This phase takes transformed records, validates them across six data quality dimensions, flags and records any quality issues, computes weighted quality scores and letter grades, and filters out critically failed records before canonical database ingestion.

---

### Agentic Pipeline Rules (Mandatory)

1. **Plan Gate First**: Before modifying or creating any files, you **must** output a detailed design plan outlining which files you will create/modify. List your key assumptions tagged as `[ASSUMPTION]`. Stop and wait for approval from the user before writing any code.
2. **Context7 Verification First**: Before creating custom JPA query methods, writing security filters, or executing Spring Batch/Redis configurations, you **must** use the `context7_resolve-library-id` and `context7_query-docs` tools to verify class definitions and framework APIs. Do not guess signatures.
3. **Spec-Driven & Non-Speculative**: Stick strictly to the requirements below. Do not add speculative code, extra configuration parameters, or placeholder mocks unless defined in the scope.
4. **Test-Driven Rigor**: Ensure that compilation succeeds, and write high-quality MockMvc and Junit integration tests covering positive, negative, and extreme edge cases (such as null fields, division-by-zero, out-of-bound ranges, and invalid datatypes).

---

### Technical Specification & Requirements

#### 1. Database & Domain Entities
Ensure Flyway migrations `V7` and `V8` are executed or mapped to standard configurations. Implement the following JPA entities in the `com.shreyas.saleslens.model` packages:
* **`QualityRule`**:
  * `id` (UUID PK), `ruleCode` (VARCHAR), `dimension` (VARCHAR/Enum), `severity` (Enum: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), `expression` (VARCHAR), `active` (BOOLEAN).
* **`QualityRun`**:
  * `id` (UUID PK), `job` (ManyToOne to `IngestionJob`), `runTimestamp` (TIMESTAMP), `totalRecords` (INT), `failedRecords` (INT).
* **`QualityIssue`**:
  * `id` (UUID PK), `run` (ManyToOne to `QualityRun`), `sourceFieldName` (VARCHAR), `ruleCode` (VARCHAR), `severity` (Enum), `message` (VARCHAR), `dimension` (Enum: `COMPLETENESS`, `VALIDITY`, `UNIQUENESS`, `CONSISTENCY`, `TIMELINESS`, `ACCURACY`), `status` (Enum/String: `OPEN`, `RESOLVED`, `ACKNOWLEDGED`).
* **`QualityScore`**:
  * `id` (UUID PK), `job` (OneToOne to `IngestionJob`), `source` (ManyToOne to `DataSource`), `scoreCompleteness` (BigDecimal), `scoreValidity` (BigDecimal), `scoreUniqueness` (BigDecimal), `scoreConsistency` (BigDecimal), `scoreTimeliness` (BigDecimal), `scoreAccuracy` (BigDecimal), `scoreOverall` (BigDecimal), `letterGrade` (VARCHAR - e.g., 'A', 'B', 'F').

#### 2. The Six Validation Checkers
Implement six modular checker components under `com.shreyas.saleslens.service.quality`:
1. **`CompletenessChecker`**: Validates required canonical fields (e.g. `customer.name`, `order.total_amount`, `product.sku`) are non-null. Flag an issue if null.
2. **`ValidityChecker`**: Validates values conform to types (e.g., checks if dates parse under detected patterns, numeric formats parse properly, emails conform to regex patterns, numbers are non-negative).
3. **`UniquenessChecker`**: Checks staging records against previously loaded items in the canonical store (by looking up natural keys like customer external ref or order UUIDs) to prevent duplicate record processing.
4. **`ConsistencyChecker`**: Evaluates cross-field constraints. For example: `line_total = quantity * unit_price` (within `$0.01` float threshold) and `order_date <= current_date`.
5. **`TimelinessChecker`**: Validates transaction dates are within dynamic thresholds (e.g., `order_date` is not more than 2 years in the past, and not in the future).
6. **`AccuracyChecker`**: Validates fields against strict dictionaries (e.g., ISO-4217 currency list, ISO-3166 country registries).

#### 3. Scoring Engine (`QualityScoreService`)
* Compute per-dimension scores:
  $$\text{Dimension Score} = 1.0 - \frac{(\text{critical} \times 1.0 + \text{high} \times 0.5 + \text{medium} \times 0.2 + \text{low} \times 0.05)}{\text{Total Records}}$$
  *(Clamp between 0.0 and 1.0)*
* Compute the overall score as a weighted average:
  * Completeness: 20%
  * Validity: 25%
  * Uniqueness: 20%
  * Consistency: 20%
  * Timeliness: 10%
  * Accuracy: 5%
* Assign Letter Grades:
  * **A**: $\ge 0.95$
  * **B**: $\ge 0.85$
  * **C**: $\ge 0.70$
  * **D**: $\ge 0.55$
  * **F**: $< 0.55$

#### 4. REST Quality Controller (`QualityController`)
Expose the following endpoints under `/api/v1/quality`:
* `GET /api/v1/quality/issues` (Paginated, filterable by `sourceId`, `severity`, `dimension`, `status`).
* `GET /api/v1/quality/scores` (Retrieve historical score trends per data source).
* `PUT /api/v1/quality/issues/{issueId}/acknowledge` (Change issue status to `ACKNOWLEDGED`).

#### 5. Integration & Error Routing
* Hook `QualityEngineService` into the Spring Batch job execution chain.
* During records processing:
  * Records with **`CRITICAL`** violations must be cataloged in a `rejected_records` log and blocked from progressing to the canonical load phase.
  * Records with minor issues (`HIGH`, `MEDIUM`, `LOW`) can continue to canonical load but must have their associated target entity's metadata fields flagged.

---

### Verification and Test Spec
1. **Repository & Service Unit Tests**:
   * Mock database lookups and verify checker triggers independently.
   * Write tests for edge case data formats (empty string headers, decimal divisions by zero, negative costs, dates far in the past/future).
2. **Controller Tests**:
   * Extend `MockMvc` tests using `@WebMvcTest(QualityController.class)` and `@MockitoBean`.
   * Bypass JWT filters in test executions using appropriate mock security filters. Ensure response payload values are serialized properly.
3. **E2E verification integration**:
   * Verify using standard Python test frameworks to feed bad data parameters (e.g. uploading a row with negative values or missing fields) and check that correct HTTP error responses, validation grades, and issues counts are returned.
