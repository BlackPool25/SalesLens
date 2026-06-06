# SalesLens — Professional Analysis, Requirements & Build Plan
**Multi-source Sales Data Unification Platform**

---

# PART 1 — MULTI-LEVEL ANALYSIS

---

## Analysis 1: FURPS+ (Rational/Grady Booch)
*Classifies all system concerns before requirements are written. Prevents missing non-functional requirements early.*

### F — Functionality
- Ingest sales data from CSV, Excel, Legacy JDBC databases, and live Kafka streams
- Infer schemas from raw source data without requiring upfront schema definition
- Map heterogeneous source fields to a fixed canonical sales schema
- Detect and persist data quality violations across 6 dimensions
- Detect cross-source conflicts at the entity-field level
- Load unified, quality-scored records into a canonical queryable store
- Expose all pipeline state (jobs, issues, conflicts, schemas, mappings) via REST API
- JWT-based auth with role separation (already partially built)

### U — Usability
- A non-engineer user must be able to register a data source via an API call with minimal config
- Field mappings inferred with low confidence must surface clearly for user confirmation before data flows
- Conflicts must be human-readable: "Source A says customer segment is Consumer, Source B says Corporate"
- Canonical data must be queryable by external tools via standard SQL (direct Postgres access)

### R — Reliability
- A bad record from one source must not block processing of other records in the same batch
- A failed ingestion job must be retryable without re-registering the source
- Staging records persist until canonical loading is confirmed — no silent data loss
- Kafka consumer failures must not result in message loss (offset committed only after successful processing)

### P — Performance
- Batch ingestion (CSV, Excel, JDBC) does not need to be real-time; throughput over latency
- Live stream ingestion (Kafka) should process within seconds of arrival
- Canonical query API must be fast — it reads pre-unified data, not raw staging tables
- Quality scoring runs asynchronously and does not block the ingestion acknowledgement

### S — Supportability
- All pipeline stages must produce observable output (job status, record counts, issue counts)
- Flyway migrations — schema is versioned and reproducible
- Docker Compose — entire stack runs with one command locally
- Every field transformation is logged in the lineage store — debuggable after the fact

### + Constraints
- Free tier infrastructure only (local Docker Compose is primary target)
- Spring Boot 4, Java, Postgres — no switching languages or databases
- Existing User + JWT + BCrypt must be reused, not rewritten
- No paid managed Kafka — runs in Docker locally

### + Interface
- REST API consumed by frontend (React/Vue)
- Canonical store directly queryable via Postgres connection by external tools (Tableau, Grafana, psql)
- File upload via multipart form POST

### + Implementation
- Spring Batch for file and JDBC ingestion (built for exactly this)
- Kafka for live stream ingestion only (not used as internal pipeline bus to keep complexity manageable while learning)
- OpenCSV for CSV parsing, Apache POI for Excel
- Apache Commons Text for fuzzy field name matching
- MapStruct for DTO/entity mapping

---

## Analysis 2: Domain-Driven Design — Bounded Context Mapping
*Identifies which parts of the system are genuinely separate concerns and should not bleed into each other.*

### Bounded Context 1: Source Management
**Responsibility:** Know about data sources, their connection config, trust levels, and schemas
**Owns:** DataSource, SourceSchema, FieldDefinition, DataProfile
**Does not own:** anything canonical, anything about quality rules

### Bounded Context 2: Ingestion & Staging
**Responsibility:** Pull data from sources in their native format, land it in staging without transformation
**Owns:** IngestionJob, StagedRecord
**Does not own:** what happens to staged records, schema inference

### Bounded Context 3: Schema Inference & Mapping
**Responsibility:** Understand what a source's data looks like and map it to the canonical schema
**Owns:** SchemaVersion, FieldMapping, TransformRule, MappingConfidence
**Does not own:** quality rules, canonical data

### Bounded Context 4: Quality
**Responsibility:** Evaluate data against all six quality dimensions, score it, persist issues
**Owns:** QualityRun, QualityScore, QualityIssue, QualityRule, DataProfile (statistical baseline)
**Does not own:** what to do with bad records beyond flagging them

### Bounded Context 5: Conflict Detection & Resolution
**Responsibility:** When loading an entity that already exists from another source, detect disagreements and apply or flag resolution
**Owns:** ConflictRecord, ResolutionStrategy
**Does not own:** quality checks, loading logic

### Bounded Context 6: Canonical Store
**Responsibility:** Own the unified, trusted representation of sales data
**Owns:** Customer, Product, Salesperson, Region, Order, OrderLineItem, DataLineage
**Does not own:** how data got here or where it came from (all source info is in lineage)

### Key insight from DDD:
Quality and conflict are **separate concerns** — a record can be individually valid (passes quality) but still conflict with another source's version of the same entity. These run sequentially: quality first, then conflict check on the survivors.

---

## Analysis 3: Event Storming (Alberto Brandolini)
*Maps the flow as domain events — what happens, in order, as commands are issued.*

```
COMMAND                     → DOMAIN EVENT
─────────────────────────────────────────────────────────────────────
Register Data Source        → DataSourceRegistered
Upload CSV / Excel File     → IngestionJobCreated → RecordStaged (×N)
Schedule JDBC Pull          → IngestionJobCreated → RecordStaged (×N)
Receive Kafka Message       → RecordStaged
                            → SchemaInferred          (first time for source)
                            → SchemaDriftDetected     (if changed)
Confirm Field Mapping       → FieldMappingConfirmed
                            → FieldMappingPending     (low confidence, needs review)
Process Staged Record       → RecordTransformed
                            → QualityIssueDetected    (per violation)
                            → QualityScoreComputed    (per batch)
Load Transformed Record     → ConflictDetected        (if entity exists from another source)
                            → ConflictFlagged
                            → CanonicalRecordCreated  (new entity)
                            → CanonicalRecordUpdated  (existing entity, no conflict)
                            → LineageRecordWritten
Acknowledge Conflict        → ConflictResolved / ConflictSuppressed
Query Canonical Data        → (read side — no event, just a query)
```

### Aggregates (things that own their own consistency):
- **IngestionJob** — owns its staged records and status transitions
- **SourceSchema** — owns its field definitions and version history
- **QualityRun** — owns its issues and scores
- **CanonicalEntity (Customer, Product, etc.)** — owns its external refs and conflict state

---

## Analysis 4: C4 Model — Container Level (Simon Brown)
*Shows what software containers exist and how they communicate.*

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SalesLens System                             │
│                                                                     │
│  ┌──────────────┐     ┌──────────────────────────────────────────┐  │
│  │  React/Vue   │────▶│         Spring Boot 4 Application        │  │
│  │  Frontend    │     │                                          │  │
│  └──────────────┘     │  ┌─────────────┐  ┌──────────────────┐  │  │
│                        │  │  REST API   │  │  Spring Batch    │  │  │
│  ┌──────────────┐     │  │  Controllers│  │  (CSV/Excel/JDBC)│  │  │
│  │ External     │     │  └─────────────┘  └──────────────────┘  │  │
│  │ Tools        │     │  ┌─────────────┐  ┌──────────────────┐  │  │
│  │ (Tableau,    │     │  │  Pipeline   │  │  Kafka Consumer  │  │  │
│  │  Grafana,    │     │  │  Services   │  │  (live streams)  │  │  │
│  │  psql)       │     │  └─────────────┘  └──────────────────┘  │  │
│  └──────┬───────┘     └──────────────────────────────────────────┘  │
│         │                         │           │                      │
│         │              ┌──────────┘           │                      │
│         ▼              ▼                      ▼                      │
│  ┌─────────────┐  ┌──────────┐         ┌──────────┐                │
│  │  Canonical  │  │ Staging  │         │  Kafka   │                │
│  │  Schema in  │  │ Schema   │         │ (Docker) │                │
│  │  Postgres   │  │ Postgres │         └──────────┘                │
│  └─────────────┘  └──────────┘                                     │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Redis — quality score cache, conflict summary cache         │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

External Systems (Sources):
  CSV/Excel Files → multipart upload → Spring Batch
  Legacy Postgres/MySQL → JDBC connection config → Spring Batch scheduled job
  Live Sales Stream → Kafka topic → Kafka Consumer
```

---

## Analysis 5: MoSCoW Prioritization
*Separates what makes the system work from what makes it impressive.*

### Must Have (system is broken without these)
- Source registration
- CSV ingestion via Spring Batch
- Schema inference (type detection, null rates, format patterns)
- Semantic field mapping with confidence score
- Mapping confirmation flow (PENDING → CONFIRMED)
- Completeness, validity, uniqueness quality checks
- Cross-source conflict detection at field level
- Canonical store load with external_refs JSONB
- Record-level lineage
- Quality score per batch
- All pipeline state exposed via REST API

### Should Have (system is weak without these)
- Excel ingestion
- JDBC connector with scheduling
- Consistency and timeliness quality dimensions
- Statistical profiling (null rate drift, distribution baseline)
- Conflict resolution strategies (TRUST_HIERARCHY, LATEST_WINS)
- Redis caching for quality scores and conflict summaries
- Schema drift detection between ingestions
- Kafka live stream connector
- React/Vue frontend (source mgmt, quality dashboard, conflict review)

### Could Have (good to have, not core)
- Accuracy dimension (reference value set matching)
- Auto-suppression of low-severity conflicts
- Scheduled re-profiling
- Canonical data query API with filters (beyond direct SQL access)
- Export of canonical data as CSV

### Won't Have (explicitly out of scope)
- ML-based anomaly detection
- Predictive analytics on sales data
- CRM integrations (Salesforce, HubSpot) via REST
- Email/webhook alerting
- Multi-tenant architecture
- User-defined canonical schema (fixed to sales domain)

---

## Analysis 6: RAID Analysis
*Professional risk management before a line of code is written.*

### Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Kafka learning curve delays Phase 8 | High | Medium | Keep Kafka only for live streams, not internal pipeline bus. Do it last |
| Schema inference produces wrong type for ambiguous fields | High | Medium | Flag inference confidence below 0.8, require user confirmation |
| Spring Batch complexity for simple CSV reads | Medium | Medium | Use SimpleJob pattern first, not full Chunk-oriented processing |
| Conflict detection creates too many false positives | Medium | High | Define precise conflict rules per entity type before coding |
| Excel files with merged cells / complex formatting | High | Low | Scope: flat tabular Excel only, document limitation clearly |
| JDBC connector requires network access to legacy DB | Medium | High | Use local Postgres as the JDBC target during development |

### Assumptions
- The canonical schema is fixed to the sales domain (Customer, Product, Salesperson, Region, Order, OrderLineItem). It does not need to be user-configurable.
- Sources are trusted enough that their data is worth processing — no source-level rejection.
- A single Spring Boot instance is sufficient (no horizontal scaling needed for prototype).
- The canonical Postgres schema is directly accessible to external tools — no additional query layer needed.
- User already has working JWT auth with ADMIN and ANALYST roles.

### Issues (known before starting)
- Docker Compose not yet set up — must be resolved in Phase 1 before anything else.
- No Kafka experience — must do a standalone Kafka proof-of-concept before integrating with the application.
- Spring Batch not in current dependencies — must be added.

### Dependencies
- Apache POI for Excel (external lib, well-maintained)
- OpenCSV for CSV (external lib, well-maintained)
- Apache Commons Text for Levenshtein distance in semantic mapping (lightweight, stable)
- MapStruct for DTO mapping (annotation processor, must configure with Lombok correctly)
- Spring Kafka, Spring Data Redis, Spring Batch (all Spring-managed, stable)
- Flyway (must order migrations correctly — no going back once applied)

---

# PART 2 — REQUIREMENTS DOCUMENT

---

## Functional Requirements

### FR-01: Source Management
- FR-01.1: User can register a data source with a name, type, and connection config
- FR-01.2: Source types supported: CSV_FILE, EXCEL_FILE, JDBC_POSTGRES, JDBC_MYSQL, KAFKA_STREAM
- FR-01.3: Each source has a trust score (0.0–1.0) set by the user, used in conflict resolution
- FR-01.4: Source can be marked ACTIVE or INACTIVE
- FR-01.5: Source stores its last sync timestamp

### FR-02: Ingestion
- FR-02.1: CSV and Excel files uploaded via multipart POST endpoint, processed by Spring Batch
- FR-02.2: JDBC sources pulled on a schedule configured per source (cron expression stored on source)
- FR-02.3: Kafka sources receive events continuously on a configured topic
- FR-02.4: Every ingestion execution creates an IngestionJob with status tracking (PENDING → RUNNING → COMPLETED / PARTIAL / FAILED)
- FR-02.5: Every raw record is written to StagedRecord before any processing
- FR-02.6: A failed job can be retried without re-registering the source
- FR-02.7: Record counts tracked per job: total read, transformed, quality_passed, quality_failed, loaded, conflicted

### FR-03: Schema Inference
- FR-03.1: On first ingestion from a source, system infers a SourceSchema from the raw data
- FR-03.2: Type detection must support: INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, EMAIL, PHONE, CURRENCY_AMOUNT, CATEGORY (low-cardinality string), FREE_TEXT
- FR-03.3: Date format patterns detected (e.g. "dd/MM/yyyy", "MMM d, yyyy", "yyyy-MM-dd")
- FR-03.4: Per-field statistics computed: null_rate, unique_count, top_10_values, min, max (numeric), sample_values
- FR-03.5: On subsequent ingestions, new schema compared against stored schema. If fields added, removed, or type-changed: SchemaDriftEvent written, new SourceSchema version created
- FR-03.6: Schema and profile written to DB and surfaced via API

### FR-04: Semantic Field Mapping
- FR-04.1: After schema inference, system attempts to auto-map source fields to canonical fields
- FR-04.2: Mapping confidence computed using: exact name match (1.0), fuzzy name match via Levenshtein (0.6–0.9), type + cardinality match (0.5–0.7), value pattern match (0.6–0.8)
- FR-04.3: Mappings with confidence ≥ 0.80 set to AUTO_CONFIRMED and proceed without user action
- FR-04.4: Mappings with confidence < 0.80 set to PENDING — data from those fields does not flow until user confirms or overrides
- FR-04.5: User can confirm, override, or reject any mapping via API
- FR-04.6: Transformation rules stored per mapping: RENAME, TYPE_CAST, FORMAT_NORMALIZE, SPLIT, MERGE, DERIVE, IGNORE
- FR-04.7: Unmapped source fields are stored as IGNORED — not lost, not loaded to canonical

### FR-05: Data Quality Engine
- FR-05.1: Quality evaluation runs per IngestionJob after transformation, before canonical load
- FR-05.2: Six dimensions evaluated:
  - **Completeness**: % of non-null values for each required canonical field
  - **Validity**: value conforms to inferred type, format pattern, and any configured rules
  - **Uniqueness**: no duplicate records by natural key (order_id, customer external_ref, product SKU) within this batch and against canonical store
  - **Consistency**: cross-field rules (line_total = quantity × unit_price, order total = sum of line totals, order_date ≤ today)
  - **Timeliness**: order_date not more than 2 years in the past (configurable), records not arriving more than N days after the period they represent
  - **Accuracy**: field values fall within known reference sets where applicable (currency codes ISO-4217, country codes ISO-3166)
- FR-05.3: Every violation writes a QualityIssue with: field, rule_code, severity (LOW/MEDIUM/HIGH/CRITICAL), message, dimension
- FR-05.4: QualityScore computed per job: score per dimension (0.0–1.0) + overall weighted score + letter grade
- FR-05.5: Records with CRITICAL issues are not loaded to canonical. Records with HIGH/MEDIUM/LOW issues are loaded but flagged.
- FR-05.6: QualityRule is a first-class entity — user can add custom rules via API without redeployment
- FR-05.7: Quality scores tracked over time per source for trend visibility

### FR-06: Conflict Detection & Resolution
- FR-06.1: A conflict occurs when a canonical entity already exists (loaded from a different source) and the incoming record provides a different value for the same field
- FR-06.2: Conflict scope — applies to: Customer (segment, region), Product (unit_price, category), Salesperson (territory, team), Order (total_amount)
- FR-06.3: Every detected conflict writes a ConflictRecord: source_a, source_b, field, value_a, value_b, entity type, entity canonical id
- FR-06.4: Resolution strategies (configured per source pair or entity type):
  - `TRUST_HIERARCHY`: source with higher trust score wins
  - `LATEST_WINS`: source with more recent updated_at wins
  - `FLAGGED_FOR_REVIEW`: no auto-resolution, human decides via API
- FR-06.5: Default strategy is FLAGGED_FOR_REVIEW unless user configures otherwise
- FR-06.6: Conflict does not block loading — canonical record is loaded with the winning value and the conflict is persisted alongside
- FR-06.7: User can acknowledge, resolve, or suppress any conflict via API
- FR-06.8: Canonical record carries has_conflicts: true flag when unresolved conflicts exist

### FR-07: Canonical Store
- FR-07.1: Six canonical tables: customers, products, salespersons, regions, orders, order_line_items
- FR-07.2: Each entity stores external_refs as JSONB: {"crm": "C-123", "erp": "1045"} — one canonical row per real-world entity regardless of source count
- FR-07.3: Each entity carries: primary_source_id, quality_score, has_conflicts, created_at, updated_at
- FR-07.4: Canonical schema is in its own Postgres schema (`canonical`) — directly queryable by external tools
- FR-07.5: No business logic in canonical tables — they are a read target, not a processing target

### FR-08: Data Lineage
- FR-08.1: Every canonical record has a lineage record linking it to: source, ingestion job, staged record, list of transformations applied
- FR-08.2: Transformations stored as ordered JSON array: [{step, from_field, to_field, rule, input_value, output_value}]
- FR-08.3: Lineage queryable per canonical record via API

### FR-09: API
- FR-09.1: All pipeline state accessible via REST API (sources, jobs, schemas, mappings, quality issues, scores, conflicts, lineage)
- FR-09.2: All list endpoints paginated with Pageable
- FR-09.3: Quality issues filterable by: source, dimension, severity, job, resolution status
- FR-09.4: Conflicts filterable by: entity type, field, resolution status, source pair
- FR-09.5: Swagger/OpenAPI documentation auto-generated

### FR-10: Auth (existing + additions)
- FR-10.1: Existing User + JWT + BCrypt reused
- FR-10.2: ADMIN role: all operations
- FR-10.3: ANALYST role: read-only on all endpoints + conflict acknowledgement

---

## Non-Functional Requirements

### NFR-01: Observability
- Every IngestionJob exposes real-time status and record counts
- QualityScore history accessible per source
- ConflictRecord counts surfaced in source detail

### NFR-02: Data Integrity
- StagedRecords persist until canonical load confirmed — no silent drops
- Kafka offsets committed only after staged record written to DB
- Spring Batch jobs use Postgres-backed JobRepository — job state survives restart

### NFR-03: Reproducibility
- Any staged batch can be reprocessed (re-run transformation + quality + load) if mapping rules change
- Flyway ensures schema is identical across environments

### NFR-04: Debuggability
- Every canonical record traceable to exact source record and transformations applied
- QualityIssues reference the specific field and value that caused the violation
- ConflictRecords show both values and both sources

### NFR-05: Local Deployability
- Full stack runs with `docker-compose up`
- No paid services required
- Canonical Postgres schema accessible on localhost:5432 for direct query tool connection

---

## Constraints
- Language: Java, Spring Boot 4
- Database: Postgres (already running)
- Infrastructure: Docker Compose only (no cloud hosting required)
- Auth: existing implementation must not be broken
- Frontend: React or Vue (basic, functional — not a design showcase)
- Kafka: Docker only, not managed cloud

## Explicitly Out of Scope
- User-configurable canonical schema
- CRM/ERP REST API connectors (Salesforce, SAP)
- ML anomaly detection
- Email/webhook alerting
- Multi-tenancy
- Horizontal scaling
- Cloud deployment (unless added later)

---

# PART 3 — BUILD PLAN

---

## New Dependencies to Add

```xml
<!-- Spring Batch -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
</dependency>

<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Flyway -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<!-- CSV -->
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.9</version>
</dependency>

<!-- Excel -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>

<!-- Fuzzy string matching (for semantic mapper) -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-text</artifactId>
    <version>1.12.0</version>
</dependency>

<!-- MapStruct (add to compiler plugin too) -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.3</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.6.3</version>
    <scope>provided</scope>
</dependency>
```

**Note on MapStruct + Lombok:** Add both annotation processors to the maven-compiler-plugin in the correct order: mapstruct-processor AFTER lombok-processor. If you don't, generated code breaks at compile time.

---

## Flyway Migration Order

```
V1  — users (already exists, your current schema)
V2  — data_sources
V3  — source_schemas, source_schema_fields
V4  — ingestion_jobs, staged_records
V5  — field_mappings
V6  — data_profiles, field_profiles
V7  — quality_rules
V8  — quality_runs, quality_scores, quality_issues
V9  — conflict_records
V10 — CREATE SCHEMA canonical; canonical.customers, canonical.products,
       canonical.salespersons, canonical.regions, canonical.orders,
       canonical.order_line_items
V11 — data_lineage
```

Each migration is one file. Never edit a migration once applied.

---

## Package Structure

```
com.saleslens.
├── controller/
│   ├── SourceController
│   ├── IngestionController
│   ├── SchemaController
│   ├── MappingController
│   ├── QualityController
│   ├── ConflictController
│   ├── CanonicalController
│   ├── LineageController
│   └── AuthController          ← already exists
│
├── service/
│   ├── source/
│   │   └── DataSourceService
│   ├── ingestion/
│   │   ├── IngestionOrchestrator
│   │   └── StagingService
│   ├── inference/
│   │   ├── SchemaInferenceService
│   │   └── TypeDetectionService
│   ├── mapping/
│   │   ├── SemanticMapperService
│   │   └── TransformationService
│   ├── quality/
│   │   ├── QualityEngineService
│   │   ├── CompletenessChecker
│   │   ├── ValidityChecker
│   │   ├── UniquenessChecker
│   │   ├── ConsistencyChecker
│   │   ├── TimelinessChecker
│   │   ├── AccuracyChecker
│   │   ├── ProfilingService
│   │   └── QualityScoreService
│   ├── conflict/
│   │   ├── ConflictDetectionService
│   │   └── ConflictResolutionService
│   ├── canonical/
│   │   ├── CanonicalLoadService
│   │   └── LineageService
│   └── cache/
│       └── QualityCacheService
│
├── batch/
│   ├── csv/
│   │   ├── CsvIngestionJobConfig
│   │   ├── CsvLineMapper
│   │   └── StagingItemWriter
│   ├── excel/
│   │   ├── ExcelIngestionJobConfig
│   │   └── ExcelRowReader
│   └── jdbc/
│       ├── JdbcIngestionJobConfig
│       └── JdbcIngestionScheduler
│
├── kafka/
│   ├── consumer/
│   │   └── LiveSalesEventConsumer
│   └── producer/
│       └── (internal events if needed)
│
├── domain/
│   ├── source/
│   │   ├── DataSource.java
│   │   ├── SourceType.java (enum)
│   │   ├── SourceSchema.java
│   │   └── SourceSchemaField.java
│   ├── ingestion/
│   │   ├── IngestionJob.java
│   │   ├── JobStatus.java (enum)
│   │   └── StagedRecord.java
│   ├── mapping/
│   │   ├── FieldMapping.java
│   │   ├── MappingStatus.java (enum)
│   │   └── TransformRule.java
│   ├── quality/
│   │   ├── QualityRule.java
│   │   ├── QualityRun.java
│   │   ├── QualityScore.java
│   │   ├── QualityIssue.java
│   │   ├── QualityDimension.java (enum)
│   │   └── DataProfile.java
│   ├── conflict/
│   │   ├── ConflictRecord.java
│   │   ├── ResolutionStrategy.java (enum)
│   │   └── ConflictStatus.java (enum)
│   ├── canonical/
│   │   ├── CanonicalCustomer.java
│   │   ├── CanonicalProduct.java
│   │   ├── CanonicalSalesperson.java
│   │   ├── CanonicalRegion.java
│   │   ├── CanonicalOrder.java
│   │   ├── CanonicalOrderLineItem.java
│   │   └── DataLineage.java
│   └── user/                    ← already exists
│
├── repository/          (one per entity, all Spring Data JPA)
├── dto/
│   ├── request/
│   └── response/
├── mapper/              (MapStruct mappers)
├── config/
│   ├── KafkaConfig
│   ├── RedisConfig
│   ├── BatchConfig
│   └── SecurityConfig   ← already exists
└── security/            ← already exists
```

---

## Canonical Schema (Fixed — Sales Domain)

These are the tables external tools will query directly.

```sql
-- canonical.customers
id              UUID PK
external_refs   JSONB    -- {"crm":"C-123","erp":"1045"}
name            VARCHAR
email           VARCHAR
phone           VARCHAR
segment         VARCHAR  -- Consumer, Corporate, Home Office
region          VARCHAR
country         VARCHAR
city            VARCHAR
primary_source  UUID FK → data_sources
quality_score   FLOAT
has_conflicts   BOOLEAN
created_at      TIMESTAMP
updated_at      TIMESTAMP

-- canonical.products
id              UUID PK
external_refs   JSONB
sku             VARCHAR
name            VARCHAR
category        VARCHAR
sub_category    VARCHAR
unit_price      DECIMAL(12,4)
currency        CHAR(3)
active          BOOLEAN
primary_source  UUID FK → data_sources
quality_score   FLOAT
has_conflicts   BOOLEAN

-- canonical.salespersons
id              UUID PK
external_refs   JSONB
name            VARCHAR
email           VARCHAR
team            VARCHAR
territory       VARCHAR
region          VARCHAR
active          BOOLEAN
primary_source  UUID FK → data_sources

-- canonical.regions
id              UUID PK
name            VARCHAR
country         VARCHAR
zone            VARCHAR

-- canonical.orders
id              UUID PK
external_refs   JSONB
order_date      DATE
ship_date       DATE
customer_id     UUID FK → canonical.customers
salesperson_id  UUID FK → canonical.salespersons
region_id       UUID FK → canonical.regions
ship_mode       VARCHAR
shipping_cost   DECIMAL(12,4)
total_amount    DECIMAL(12,4)
currency        CHAR(3)
source_id       UUID FK → data_sources
job_id          UUID FK → ingestion_jobs
quality_score   FLOAT
has_conflicts   BOOLEAN
created_at      TIMESTAMP

-- canonical.order_line_items
id              UUID PK
order_id        UUID FK → canonical.orders
product_id      UUID FK → canonical.products
quantity        INTEGER
unit_price      DECIMAL(12,4)
discount        DECIMAL(5,4)
line_total      DECIMAL(12,4)
```

---

## What "Conflict" Means — Precisely

A conflict is a field-level disagreement between two sources on the same real-world entity, where both values are plausible.

**Customer conflicts:**
- `segment`: Source A = "Consumer", Source B = "Corporate" → CONFLICT
- `region`: Source A = "West", Source B = "Northwest" → CONFLICT
- `email`: differs between sources → CONFLICT

**Product conflicts:**
- `unit_price`: differs by more than 1% between sources → CONFLICT
- `category`: differs → CONFLICT
- `sub_category`: differs → CONFLICT

**Order conflicts:**
- `total_amount`: differs by more than $0.01 → CONFLICT
- `ship_mode`: differs → CONFLICT

**Not a conflict:**
- A null value in one source where the other has a value → COMPLETENESS issue, not conflict (the non-null wins by default)
- A field that only exists in one source → merge silently
- Rounding differences under threshold → not a conflict

---

## Phase-by-Phase Build Plan

---

### Phase 1 — Infrastructure Foundation
**Goal:** Full stack runs. App starts. Tables exist. You can produce and consume a Kafka message.
**Prerequisite:** Nothing. This is Day 1.

**Tasks:**
1. Write `docker-compose.yml`:
  - Postgres (map to 5432 — you already use this, so map carefully or use 5433 for Docker)
  - Redis (6379)
  - Kafka with KRaft (no Zookeeper): use `confluentinc/cp-kafka:7.6.0` with KRaft env vars
  - Kafka UI (optional but extremely helpful: `provectuslabs/kafka-ui`)
2. Add all new dependencies to `pom.xml`
3. Fix MapStruct + Lombok compiler plugin ordering
4. Write `application.yml` with datasource, redis, kafka, batch config
5. Write V1 migration (users — your existing schema)
6. Write V2 migration (data_sources table)
7. Confirm app starts: `mvn spring-boot:run`
8. Kafka proof-of-concept (before integrating with app):
  - Use Kafka CLI inside Docker: create topic → produce → consume
  - Write one `@KafkaListener` bean that logs whatever it receives
  - Write one `KafkaTemplate.send()` call from a `/test/kafka` endpoint
  - See message arrive in listener log
9. Write V3–V11 migrations (all tables)
10. Write all @Entity classes

**Done when:** App starts, all tables exist in Postgres, you send a test Kafka message and see it logged.

**What you learn:** KRaft Kafka setup (no Zookeeper), Spring Batch JobRepository autoconfiguration, Flyway migration ordering, MapStruct + Lombok compiler interaction

---

### Phase 2 — Source Registry + CSV Ingestion
**Goal:** Register a data source. Upload a CSV. See raw rows in staged_records.

**Tasks:**
1. `DataSource` entity + repository + `DataSourceService`
2. `SourceController`: `POST /api/v1/sources`, `GET /api/v1/sources`, `GET /api/v1/sources/{id}`
3. `IngestionJob` entity + repository
4. `StagedRecord` entity + repository
5. `CsvIngestionJobConfig` using Spring Batch:
  - `FlatFileItemReader` with `DefaultLineMapper` and `DelimitedLineTokenizer`
  - Header detection (first row = field names)
  - `StagingItemWriter`: write each row as a `StagedRecord` with raw payload as JSONB
  - `IngestionJob` created at batch start, updated on completion
6. `IngestionController`: `POST /api/v1/ingest/csv` (multipart file upload + sourceId)
7. `GET /api/v1/jobs`, `GET /api/v1/jobs/{id}`

**Done when:** Upload a CSV with 1000 rows, see 1000 StagedRecords in DB, see IngestionJob show COMPLETED.

**What you learn:** Spring Batch SimpleJob vs ChunkJob, FlatFileItemReader config, multipart file handling in Spring Boot, Spring Batch JobRepository (uses its own tables — they appear automatically)

---

### Phase 3 — Schema Inference
**Goal:** After CSV staged, system infers what each column contains and stores it.

**Tasks:**
1. `TypeDetectionService`:
  - `detectType(List<String> sampleValues)` → returns `InferredType` enum
  - Logic: try parse as Integer → Decimal → Boolean → Date (multiple format attempts) → DateTime → Email regex → Phone regex → Currency → then cardinality check (< 20 unique values in sample = CATEGORY) → else FREE_TEXT
  - `detectDateFormat(List<String> sampleValues)` → returns pattern string
2. `SchemaInferenceService`:
  - Takes a completed IngestionJob
  - Samples up to 500 StagedRecords from the job
  - Runs `TypeDetectionService` per field
  - Computes: null_rate, unique_count, top_10_values (as JSONB array), min/max for numeric
  - Writes `SourceSchema` + `SourceSchemaField` records
  - Writes `DataProfile` + `FieldProfile` records
  - On subsequent jobs: compares to previous schema version → writes new version if drift detected
3. `SchemaController`: `GET /api/v1/sources/{id}/schema`, `GET /api/v1/sources/{id}/schema/drift`
4. Wire `SchemaInferenceService` to run automatically after CSV batch completes

**Done when:** Upload Kaggle Superstore CSV. Query schema endpoint. See every column with its inferred type and null rate. Upload again with one column removed. See drift detected.

**What you learn:** Statistical sampling strategy, how to build a type inference chain, schema versioning design

---

### Phase 4 — Semantic Field Mapping
**Goal:** Source fields auto-mapped to canonical fields. Low-confidence ones flagged for review.

**Tasks:**
1. Define canonical field registry — a static map of all canonical fields across all six entities, with their expected type and synonyms:
   ```
   customer.name → type: FREE_TEXT, synonyms: [customer_name, cust_name, client_name, buyer_name]
   order.total_amount → type: DECIMAL, synonyms: [total, sales, revenue, order_total, amount]
   product.sku → type: FREE_TEXT, synonyms: [sku, product_code, item_code, prod_id]
   ...
   ```
2. `SemanticMapperService.generateMappings(SourceSchema)`:
  - For each source field:
    a. Normalize name (lowercase, strip underscores/spaces)
    b. Exact match against canonical field names and synonyms → confidence 1.0
    c. Levenshtein distance ≤ 2 → confidence 0.85
    d. Token overlap (split on underscore/space, intersect token sets) → confidence 0.70
    e. Same inferred type + cardinality profile matches a canonical field → confidence 0.55
    f. Below 0.55 → UNMAPPED, stored as IGNORED
  - Write `FieldMapping` per source field with confidence + status (AUTO_CONFIRMED if ≥ 0.80, PENDING if < 0.80)
3. `MappingController`:
  - `GET /api/v1/sources/{id}/mappings` — list all with confidence and status
  - `PUT /api/v1/sources/{id}/mappings/{fieldId}/confirm` — confirm pending mapping
  - `PUT /api/v1/sources/{id}/mappings/{fieldId}/override` — change target canonical field
  - `PUT /api/v1/sources/{id}/mappings/{fieldId}/ignore` — mark as IGNORED
4. `TransformationService`: given a `StagedRecord` + confirmed `FieldMappings`, apply transforms and return a normalized map ready for quality checks

**Done when:** Register Kaggle Superstore as source. See all fields mapped. Verify "Customer Name" → customer.name (exact), "Postal Code" → PENDING (ambiguous). Confirm it. See status change.

**What you learn:** Fuzzy matching algorithms, confidence thresholds, how to model a mapping engine as data rather than code

---

### Phase 5 — Quality Engine
**Goal:** Every staged record evaluated across quality dimensions. Issues persisted. Score computed.

**Tasks:**
1. `QualityEngineService.runQualityCheck(IngestionJob)`:
  - Iterates transformed records
  - Runs all checkers in sequence
  - Aggregates issues into a `QualityRun`
  - Computes `QualityScore` (per dimension and overall)
2. Implement each checker as a separate `@Component`:
  - `CompletenessChecker`: for each required canonical field, check null rate. Issue if field null rate > 10% above profile baseline.
  - `ValidityChecker`: value conforms to inferred type. Date values parseable by detected format. Email matches regex. Amount is non-negative. Issue per failing record+field.
  - `UniquenessChecker`: query canonical store for existing record with same natural key. If found from same source → duplicate. Issue written, record skipped.
  - `ConsistencyChecker`: line_total = quantity × unit_price (within $0.01). order total = sum of line item totals. order_date ≤ today.
  - `TimelinessChecker`: order_date not more than 730 days in the past (configurable). order_date not in future.
  - `AccuracyChecker`: currency code in ISO-4217 set. Country name/code in reference set.
3. `QualityScoreService.compute(List<QualityIssue>, int totalRecords)`:
  - Score per dimension = 1.0 − (critical_issues × 1.0 + high × 0.5 + medium × 0.2 + low × 0.05) / totalRecords, clamped to [0, 1]
  - Overall = weighted average (completeness 20%, validity 25%, uniqueness 20%, consistency 20%, timeliness 10%, accuracy 5%)
  - Grade: A ≥ 0.95, B ≥ 0.85, C ≥ 0.70, D ≥ 0.55, F < 0.55
4. `QualityController`:
  - `GET /api/v1/quality/issues` (paginated, filterable)
  - `GET /api/v1/quality/scores` (per source, over time)
  - `GET /api/v1/quality/summary`
  - `PUT /api/v1/quality/issues/{id}/acknowledge`
5. Records with CRITICAL issues written to a `rejected_records` table with reason. All others proceed.

**Done when:** Run Superstore CSV through quality. See completeness score. Manually insert a row with negative amount — see ValidityChecker catch it as HIGH severity issue. See overall score as letter grade.

**What you learn:** How to model quality as data, weighted scoring, why checkers should be separate components (different failure modes), reference data validation

---

### Phase 6 — Conflict Detection + Canonical Load
**Goal:** Records that pass quality are loaded to canonical tables. Cross-source conflicts detected and stored.

**Tasks:**
1. `CanonicalLoadService.load(IngestionJob)`:
  - For each quality-passing transformed record:
    a. Determine entity type (customer, product, order, etc.)
    b. Look up canonical store by external_ref from source
    c. If not found → INSERT new canonical record, write lineage
    d. If found, same source → UPDATE (same source refreshing its data)
    e. If found, different source → run conflict detection
2. `ConflictDetectionService.detect(CanonicalEntity existing, TransformedRecord incoming)`:
  - Compare each conflicting field (defined in FR-06.2)
  - For each differing field: write `ConflictRecord`
  - Apply configured resolution strategy
  - Update canonical record with resolved value
  - Set `has_conflicts = true`
3. `LineageService.write(...)`: after each canonical write, persist lineage record
4. `ConflictController`:
  - `GET /api/v1/conflicts` (filterable by entity type, field, status, source)
  - `GET /api/v1/conflicts/{id}`
  - `PUT /api/v1/conflicts/{id}/resolve` (body: chosen_value)
  - `PUT /api/v1/conflicts/{id}/suppress`

**Done when:** Load Superstore CSV. Load a second CSV of the same customers with different segment values. See ConflictRecords in DB. See `has_conflicts = true` on those canonical customers. Resolve one conflict via API. See it update.

**What you learn:** Upsert semantics with conflict tracking, the difference between quality (record-level) and conflict (cross-source entity-level), lineage modeling

---

### Phase 7 — Excel + JDBC Connectors
**Goal:** Two more source types working through the same pipeline.

**Tasks:**
1. **Excel connector:**
  - `ExcelIngestionJobConfig` using Apache POI `XSSFWorkbook`
  - Read first row as headers, iterate subsequent rows
  - Convert each row to a flat map (field → string value)
  - Write to `StagedRecord` identically to CSV connector
  - Scope: flat tabular sheets only. Multi-sheet: use first sheet. Merged cells: treat as null.
2. **JDBC connector:**
  - `JdbcIngestionJobConfig` using Spring Batch `JdbcCursorItemReader`
  - Connection URL, credentials, table/query stored on `DataSource` entity (encrypted with AES in connectionConfig JSONB)
  - `JdbcIngestionScheduler`: reads all ACTIVE JDBC sources, checks if next scheduled pull is due (cron-based), triggers batch job
  - During dev: use your existing local Postgres as the "legacy database" target — create a separate schema with sample data

**Done when:** Upload an Excel file with the same Superstore data. See it staged and processed. Create a JDBC source pointing at a local Postgres table. See records pulled on schedule.

**What you learn:** Apache POI workbook iteration, JdbcCursorItemReader, Spring @Scheduled, storing and decrypting connection credentials

---

### Phase 8 — Kafka Live Stream Connector
**Goal:** A live Kafka topic streams sales events. They flow through the same pipeline as batch data.

**Tasks:**
1. Define Kafka message schema for live sales events (JSON). This is what would come from a POS system, e-commerce platform, etc.:
   ```json
   {
     "event_id": "uuid",
     "source_system": "pos_terminal_01",
     "event_time": "2024-03-15T14:22:00Z",
     "customer_ref": "C-9912",
     "product_ref": "SKU-441",
     "salesperson_ref": "SP-07",
     "quantity": 3,
     "unit_price": 49.99,
     "total_amount": 149.97,
     "currency": "USD",
     "region": "West"
   }
   ```
2. `LiveSalesEventConsumer`:
  - `@KafkaListener` on `sales.live` topic
  - Deserialize JSON → `StagedRecord`
  - Create or reuse the `IngestionJob` for the source (one long-running job per Kafka source, or one job per hour — your design decision)
  - Write `StagedRecord` to DB
  - Commit offset ONLY after staged record written
3. Wire the same inference/mapping/quality/conflict/canonical pipeline to process staged records from Kafka source
4. Write a test producer script (Python or Java main) that publishes 100 fake sales events to `sales.live` topic
5. Watch them flow through end-to-end

**This is the hardest phase.** The complexity is: Kafka consumers run in their own thread. DB transactions, Spring Batch jobs, and Kafka offsets must be carefully coordinated. Do not rush this.

**Done when:** Run test producer. See 100 events land in staged_records. See them quality-checked and loaded to canonical tables. See quality score for the Kafka source.

**What you learn:** Kafka consumer offset management, consumer group semantics, transaction boundaries between Kafka and database, idempotent processing

---

### Phase 9 — Redis Caching + API Polish
**Goal:** Quality scores and conflict summaries cached. All list endpoints paginated and filterable.

**Tasks:**
1. `QualityCacheService`:
  - Key: `quality:score:{sourceId}:{jobId}` TTL 30 minutes
  - Key: `quality:summary:{sourceId}` TTL 15 minutes
  - Key: `conflicts:summary:{sourceId}` TTL 15 minutes
  - Invalidate relevant keys when new job completes for source
2. Add `Pageable` to all list endpoints
3. Add filter params: QualityIssues by (sourceId, dimension, severity, jobId, status), Conflicts by (entityType, field, status, source1Id, source2Id)
4. Add `@PreAuthorize` to all endpoints (was likely already there for auth but confirm)
5. Springdoc OpenAPI: add to pom, verify Swagger UI works at `/swagger-ui.html`
6. `CanonicalController`: query endpoints for each canonical entity with pagination (this is secondary — external tools connect directly to Postgres, but having an API is useful for the frontend)

**Done when:** Hit a quality score endpoint twice in quick succession, verify second response is cached (log Redis hit). Open Swagger UI, see all endpoints documented.

---

### Phase 10 — Frontend (React)
**Goal:** A working dashboard a potential customer could look at and understand the value.

**Five pages, nothing more:**

1. **Login** — standard, uses your existing auth endpoint

2. **Sources** — list registered sources, register new source, see last sync time, quality score badge per source

3. **Ingestion** — upload CSV/Excel, see active jobs with real-time status (polling), see record counts (staged, quality passed, loaded, rejected)

4. **Quality Dashboard** — select a source → see quality score per dimension as a bar chart, quality trend over time as a line chart (Chart.js or Recharts), issues table with severity filter

5. **Conflicts** — table of open conflicts, each row shows: entity type, field, Source A value, Source B value, resolution strategy, resolve/suppress buttons

**Approach:** Vite + React, Axios for API calls, Recharts for charts, TailwindCSS for styling (utility classes, no custom CSS files). JWT stored in memory (not localStorage — use a React context).

---

### Phase 11 — Docker Compose + README
**Goal:** Someone can clone the repo and run the entire system in one command.

**docker-compose.yml services:**
- `db`: postgres:16
- `redis`: redis:7-alpine
- `kafka`: confluentinc/cp-kafka:7.6.0 (KRaft mode)
- `kafka-ui`: provectuslabs/kafka-ui (for local debugging)
- `app`: your Spring Boot app (build from Dockerfile)
- `frontend`: your React app (nginx container serving built static files)

**README must include:**
- What this system does (2 paragraphs, clear)
- Architecture diagram (ASCII is fine)
- Prerequisites: Docker, Docker Compose
- How to run: `docker-compose up --build`
- How to connect external tools: Postgres connection string for canonical schema
- Known scope boundaries (Excel: flat sheets only, Kafka: local only, etc.)
- API docs link (Swagger)

---

## What You Will Be Able To Say

**"What problem does this solve?"**
Sales data arrives from multiple systems — a CRM, a legacy ERP, point-of-sale streams, exported spreadsheets. Each uses different field names, formats, and conventions for the same real-world entities. Most companies have no unified view of their own sales data. This system ingests from all those sources, infers their schemas, maps fields to a canonical sales model, evaluates every record across six quality dimensions, detects cross-source conflicts, and produces a single clean database that analysis tools can trust.

**"What's genuinely hard in this system?"**
Three things: the semantic mapper, which infers field equivalence statistically rather than requiring manual configuration; the conflict model, which persists cross-source disagreements as first-class queryable entities rather than silently overwriting; and the quality engine, which evaluates data against a statistical baseline so it catches drift that hardcoded rules miss.

**"How does a customer actually use this without migrating their data?"**
They don't migrate anything. They register their existing Postgres or MySQL database as a JDBC source. The system pulls from it on a schedule. Their data stays where it is. The canonical store is additive — a clean unified copy, not a replacement.

**"What does a conflict actually mean in your system?"**
It means two sources provided different values for the same real-world entity. I defined conflicts precisely by entity type — customer segment disagreements, product price differences over 1%, order total differences over a cent. Each conflict is a queryable record showing both values, both sources, the resolution strategy applied, and the current status. Nothing is silently overwritten.