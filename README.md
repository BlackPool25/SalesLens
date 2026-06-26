# SalesLens - Multi-Source Sales Data Unification Platform

SalesLens is a multi-source sales data unification platform built with Java 25, Spring Boot 4, Spring Batch 6, Hibernate, Postgres, Kafka, and Redis.

This project enables organizations to stage heterogeneous sales data, profile schemas automatically, track schema drift over time, and resolve entities across diverse data sources.

---

## Technical Stack
* **Language/Runtime**: Java 25 (OpenJDK 25)
* **Framework**: Spring Boot 4.0.6, Spring Batch 6.0.3, Spring Security
* **Database**: PostgreSQL 18.4 (Flyway migrations for schema evolution)
* **Messaging/Cache**: Kafka, Redis
* **Build System**: Maven

---

## Phase 3 - Schema Inference
The Phase 3 implementation introduces automated schema profiling and drift tracking for CSV ingestion jobs.

### Key Features
1. **Dynamic Type Inference**: Iterates through a deterministic type chain (`INTEGER` -> `DECIMAL` -> `BOOLEAN` -> `DATE` -> `DATETIME` -> `EMAIL` -> `PHONE` -> `CURRENCY_AMOUNT` -> `CATEGORY` -> `FREE_TEXT`).
2. **Category vs. Free Text**: Intelligently classifies categories if the sample contains fewer than 20 unique values, falling back to free-text descriptions for high-cardinality fields.
3. **Drift Detection**: When a new batch of data is processed, the system compares the new schema against the current active schema. If column additions, deletions, or type alterations are detected, it version-increments the schema and supersedes previous configurations.
4. **Data Profiling**: Generates column-level profiling metrics including null rates, unique value counts, top frequent values, and statistical minimum/maximum bounds.

---

## Phase 4 - Semantic Field Mapping
Phase 4 introduces intelligent schema mapping that bridges dynamic ingested schemas with a canonical entity model, supporting automated mapping, manual overrides, and structural transformation.

### Key Features
1. **Heuristic-First Similarity Matcher (PRIMARY)**:
   * **Exact Match (1.00)**: Matching column name or predefined synonyms directly.
   * **Levenshtein Distance (0.85)**: Typo tolerance (edit distance <= 2).
   * **Token Overlap (0.70)**: Partial match (Jaccard similarity >= 0.5) for word overlaps.
   * **Type Fallback (0.55)**: Match based on column data type.
2. **Optional LLM Advisory Mapping**: When Ollama is available, an LLM can provide mapping suggestions. The LLM result is used only if its confidence exceeds the heuristic confidence. Output is validated against the canonical registry before acceptance. Up to 2 retries on parse failure.
3. **Dynamic Payload Transformation**: Translates nested raw record maps into flattened, singularized canonical JSON maps (e.g. `{"order.total_amount": "123.45"}`) according to confirmed field mappings.
4. **Safety & Hardening**: Ignores empty/blank headers automatically, stringifies numeric and boolean raw inputs, and handles malformed JSON inputs gracefully without failing ingestion jobs.

---

## Phase 5 - Quality Engine
Phase 5 implements a six-dimension data quality evaluation pipeline with statistical baseline drift detection and optional LLM-powered issue explanations.

### Key Features
1. **Six Quality Dimensions**: Completeness, Validity, Uniqueness, Consistency, Timeliness, Accuracy — each implemented as a separate `QualityChecker` component.
2. **Weighted Scoring**: Per-dimension scores (0.0–1.0) combined with configurable weights to produce an overall score and letter grade (A–F).
3. **Statistical Baseline Drift Detection**: The `ProfilingService` compares current batch statistics against historical profiles:
   - **Null rate drift**: flags when null rates shift >20pp from baseline (COMPLETENESS issue)
   - **Value distribution skew**: flags when >50% of top-10 values are new (VALIDITY issue)
   - **Range expansion**: flags when numeric min/max exceed 3σ of historical mean (ACCURACY issue)
   - Cold-start guard: requires ≥3 batches of profiling data before activating
4. **Optional LLM Quality Explanations**: The `QualityExplanationService` (in `service/advisory/`) generates human-readable explanations with remediation suggestions. Async, best-effort, never blocks the pipeline.
5. **REST API**: Query issues (filterable by source, dimension, severity), view score trends, acknowledge issues.

---

## Phase 6 - Conflict Detection & Canonical Load
Phase 6 resolves multi-source conflicts and loads quality-verified records into canonical entity tables with full data lineage.

### Key Features
1. **Canonical Load**: Quality-passing records are upserted into `canonical.*` tables using multi-pass ordering — entities with higher trust scores and completeness are loaded first, establishing a base that lower-pass records merge into.
2. **Entity Resolution**: Matching combines `external_refs` JSONB exact-match lookups with business-key fallback strategies (email for contacts, SKU for products, composite keys for orders). Entities from different sources that resolve to the same identity are grouped for conflict analysis.
3. **Conflict Detection**: Field-by-field comparisons across grouped entity versions. Each conflict records the field name, diverging values, source provenance, and a computed importance score. Batch resolution thresholds prevent premature propagation when unresolved conflict density exceeds the configured limit.
4. **Resolution Strategies**: Three configurable strategies triggered automatically during resolution:
   - `TRUST_HIERARCHY` — selects the value from the source with the highest trust score when the trust gap between sources is ≥0.3
   - `LATEST_WINS` — picks the most recently ingested value for low-importance fields (formatting, descriptions)
   - `FLAGGED_FOR_REVIEW` — escalates high-importance fields (pricing, quantities, customer identifiers) for manual intervention
5. **REST API**: Query, inspect, resolve, and suppress conflicts.
6. **Data Lineage**: Every canonical write traces back through the source record ID, ingestion batch, and source system, producing an auditable chain from raw input to master record.

### REST API
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/conflicts` | List all conflicts with pagination and filterable by source, entity type, severity, and status |
| GET | `/api/v1/conflicts/{id}` | Retrieve a single conflict with full field-level detail and source provenance |
| PUT | `/api/v1/conflicts/{id}/resolve` | Resolve conflict by providing the chosen canonical value |
| PUT | `/api/v1/conflicts/{id}/suppress` | Suppress a conflict permanently without resolution |

---

## Phase 7 - Excel and JDBC Connectors
Phase 7 introduces support for Excel spreadsheets and relational databases as ingestion sources, expanding the platform beyond CSV files.

### Excel Connector
The Excel connector processes spreadsheet data using Spring Batch and Apache POI.
* **How to Upload**: Send a `POST` request to `/api/v1/ingest/excel` with `multipart/form-data` containing the `file` and the `sourceId` (UUID).
* **Supported Features**:
  * Flat tabular data structures.
  * Reads the first sheet only (index 0).
  * Evaluates cell formulas automatically.
  * Merged cells resolve to null values for all cells except the top-left cell.
* **Limitations**:
  * Only supports `.xlsx` files. Legacy `.xls` files are not supported.
  * Loads the entire workbook into memory, which can cause issues with very large files.
  * Doesn't support password-protected files or multiple sheets.

### JDBC Connector
The JDBC connector enables scheduled or manual ingestion from relational databases.
* **How to Register**: Create a data source via `POST /datasources/create-source` with `sourceType` set to `JDBC_POSTGRES` or `JDBC_MYSQL`.
* **Connection Configuration**: Provide a JSONB string in the `connectionConfig` field. The format is:
  ```json
  {
    "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
    "user": "myuser",
    "password": "encrypted_password_here",
    "driverClassName": "org.postgresql.Driver",
    "query": "SELECT * FROM sales_data"
  }
  ```
* **Credential Encryption**: Database credentials in the connection configuration are encrypted at rest using AES-256. The encryption key is sourced from the `ENCRYPTION_KEY` environment variable.
* **Cron Scheduling**: Set the `cronExpression` field (e.g., `0 0 * * * *` for hourly) when registering the data source.
* **Scheduler**: A background scheduler polls active JDBC sources every 60 seconds by default. You can configure this interval using the `saleslens.batch.jdbc.poll-interval-ms` property.

### REST API
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/ingest/excel` | Upload an Excel file (`.xlsx`) via multipart form data |
| POST | `/api/v1/ingest/jdbc/{sourceId}` | Manually trigger ingestion for a registered JDBC source |

---

## Phase 8 — Kafka Live Stream Connector

Phase 8 introduces live sales event streaming via Kafka, enabling real-time ingestion of sales data from POS systems, e-commerce platforms, and other event sources. The stream consumer writes each event to the staging database immediately, then a scheduled processor groups events into time windows and runs the full pipeline (schema inference → quality engine → canonical load).

### Architecture Overview

```
Kafka (sales.live) → LiveSalesEventConsumer → StagedRecord → StreamPipelineScheduler → PipelineCompletionHandler
       │                      │                          │                            │
       │               ┌──────┴──────┐                    │                     ┌──────┴──────┐
       │               │ Source      │                    │                     │  Schema     │
       │               │ Registry    │                    │                     │  Inference  │
       │               │ (Cache)     │                    │                     ├─────────────┤
       │               └─────────────┘                    │                     │  Quality    │
       │                                                  │                     │  Engine     │
       │               ┌───────────────┐                  │                     ├─────────────┤
       │               │ Window Job    │                  │                     │  Canonical  │
       │               │ Manager       │                  │                     │  Load       │
       │               └───────────────┘                  │                     └─────────────┘
       │                         │                        │
  sales.live.DLT         30-second windows           Dedup via SHA-256
  (Dead Letter Topic)                                 (source_id + hash)
```

### Message Schema

Events must be valid JSON with the following required fields:

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "source_system": "pos_terminal_01",
  "event_time": "2026-06-26T12:00:00Z",
  "customer_ref": "C-9912",
  "product_ref": "SKU-ALPHA-001",
  "salesperson_ref": "SP-07",
  "quantity": 5,
  "unit_price": 49.99,
  "total_amount": 249.95,
  "currency": "USD",
  "region": "East"
}
```

Required fields: `event_id` (unique string), `source_system` (mapped to a DataSource), `event_time` (ISO-8601 timestamp). All other fields are optional and stored as-is in the raw payload.

### Key Components

| Component | Responsibility |
|-----------|----------------|
| `StreamKafkaConfig` | Custom `ConcurrentKafkaListenerContainerFactory` with `AckMode.RECORD`, per-message commits, DLT error handler (3 retries, 1s backoff) |
| `KafkaTopicConfig` | Defines `sales.live` (3 partitions) and `sales.live.DLT` (1 partition) topics |
| `KafkaSourceRegistryService` | Resolves `source_system` from Kafka messages to `DataSource` entities with a 5-minute volatile cache |
| `StreamIngestionJobManager` | Manages per-source windowed `IngestionJob` lifecycle with `ReentrantReadWriteLock` for thread-safe rotation |
| `LiveSalesEventConsumer` | `@KafkaListener` that deserializes JSON, validates required fields, resolves source, writes `StagedRecord` with SHA-256 dedup hash |
| `StreamPipelineScheduler` | `@Scheduled` polling (default 30s) that rotates ready windows and triggers the full pipeline |
| `V17__add_dedup_constraint_to_staged_records.sql` | Partial unique index on `(source_id, record_hash)` for idempotent deduplication |

### Configuration

```yaml
saleslens:
  batch:
    streaming:
      poll-interval-ms: 30000        # Scheduler polling interval
      window-seconds: 30              # Time window for batching events
      source-cache-ttl-ms: 300000    # Source system cache TTL (5 min)
  kafka:
    stream-topic: sales.live          # Kafka topic for live events
    stream-group-id: saleslens-live   # Consumer group ID
```

### Offset Management

- **At-least-once delivery**: Offsets committed only after successful DB write (`AckMode.RECORD` + `@Transactional`)
- **Deduplication**: SHA-256 hash of raw message JSON, enforced by DB unique constraint — identical messages produce exactly one `StagedRecord`
- **Consumer rebalance**: On rebalance, uncommitted offsets cause reprocessing; duplicates are discarded by the dedup constraint

### Error Handling

| Error | Handling |
|-------|----------|
| Invalid JSON / missing fields | `IllegalArgumentException` → 3 retries (1s backoff) → **Dead Letter Topic** (`sales.live.DLT`) |
| Unknown `source_system` | `UnknownSourceException` → DLT |
| Duplicate message | `DataIntegrityViolationException` caught → logged at WARN → message skipped |
| Pipeline failure | Job marked `FAILED` with error message; records preserved for manual retry |

### Testing

```bash
# 1. Install requirements
pip install -r scripts/requirements-kafka.txt

# 2. Start the full stack
docker compose up -d --build

# 3. Publish 100 test events
python scripts/kafka_test_producer.py

# Options:
python scripts/kafka_test_producer.py --count 50           # Custom count
python scripts/kafka_test_producer.py --count 5 --dry-run  # Dry run (no Kafka needed)
```

Run the embedded Kafka integration test (requires Postgres):
```bash
docker compose up -d postgres
./mvnw test -Dgroups='kafka'
```

Exclude Kafka tests from fast suite:
```bash
./mvnw test -Dtest='!SaleslensApplicationTests' -Dgroups='!kafka'
```

### Current Limitations

- Single topic (`sales.live`) — multi-source routing is via `source_system` message field
- Window-based batching (default 30s) — not truly real-time; events wait for window closure
- No dynamic consumer group or topic management
- Failed pipeline windows require manual retry (no automatic reprocessing)

---

## Getting Started

### Prerequisites
* Docker and Docker Compose
* Python 3 with `requests` library (for E2E tests)
* Maven (optional, if running tests locally)

### Running the Services
Start the complete infrastructure (Postgres, Kafka, Redis, and the backend service):
```bash
docker compose up -d --build
```

### Running Unit Tests
To run Java unit tests locally:
```bash
mvn test -Dtest=!SaleslensApplicationTests
```

### Running End-to-End Verification
An automated Python integration script validates user registration, authentication, data source registration, CSV ingestion, schema inference, schema drift, semantic mapping generation, and manual overrides:
```bash
python3 verify_e2e.py
```

---

## API Documentation

### 1. Register User
* **Endpoint**: `POST /auth/register`
* **Request Body**:
```json
{
  "username": "johndoe",
  "password": "SecurePassword123!",
  "email": "johndoe@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

### 2. Login User
* **Endpoint**: `POST /auth/login`
* **Request Body**:
```json
{
  "identifier": "johndoe",
  "password": "SecurePassword123!"
}
```
* **Response Body**:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG..."
}
```

### 3. Create Data Source
* **Endpoint**: `POST /datasources/create-source`
* **Request Body**:
```json
{
  "name": "Superstore Sales",
  "sourceType": "CSV_FILE",
  "trustScore": 0.9,
  "active": true,
  "connectionConfig": "{\"filePath\": \"/tmp/file.csv\"}"
}
```

### 4. Upload CSV
* **Endpoint**: `POST /api/v1/ingest/csv`
* **Request Headers**: `Authorization: Bearer <accessToken>`
* **Content-Type**: `multipart/form-data`
* **Parameters**:
  * `file`: (CSV File binary)
  * `sourceId`: (UUID of the created data source)

### 5. Get Current Active Schema
* **Endpoint**: `GET /api/v1/sources/{sourceId}/schema`
* **Request Headers**: `Authorization: Bearer <accessToken>`

### 6. Get Schema Drift History
* **Endpoint**: `GET /api/v1/sources/{sourceId}/schema/drift`
* **Request Headers**: `Authorization: Bearer <accessToken>`

### 7. Get Field Mappings
* **Endpoint**: `GET /api/v1/sources/{sourceId}/mappings`
* **Request Headers**: `Authorization: Bearer <accessToken>`

### 8. Confirm Mapping
* **Endpoint**: `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/confirm`
* **Request Headers**: `Authorization: Bearer <accessToken>`

### 9. Override Mapping
* **Endpoint**: `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/override`
* **Request Headers**: `Authorization: Bearer <accessToken>`
* **Parameters**:
  * `canonicalEntity`: (String, e.g. `customers`)
  * `canonicalField`: (String, e.g. `name`)

### 10. Ignore Mapping
* **Endpoint**: `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/ignore`
* **Request Headers**: `Authorization: Bearer <accessToken>`
