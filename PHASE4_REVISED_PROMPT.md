# Phase 4 (Revised) — LLM Schema Matching & Dynamic Schema Promotion Prompt

## Context & Objectives
You are a **builder agent** implementing the LLM-based schema matching upgrade and dynamic canonical schema management for SalesLens.

This implementation replaces the simple heuristics matching engine with an LLM-based schema mapper. It also handles custom source fields by storing them in a `additional_attributes` JSONB column, and exposes REST endpoints to promote JSONB keys to first-class schema columns (or demote first-class columns back to JSONB keys).

---

### Agentic Pipeline Rules (Mandatory)

1. **Plan Gate First**: Before modifying or creating any files, you **must** output a detailed design plan outlining which files you will create/modify. List your key assumptions tagged as `[ASSUMPTION]`. Stop and wait for approval from the user before writing any code.
2. **Context7 Verification First**: Before invoking LangChain4j APIs, executing raw JDBC alter statements, or modifying JPA mappings, you **must** use the `context7_resolve-library-id` and `context7_query-docs` tools to verify definitions.
3. **Spec-Driven & Non-Speculative**: Stick strictly to the requirements below. Do not add speculative code or dependencies.

---

### Technical Specification & Requirements

#### 1. LLM Schema Matching Engine
Upgrade `SemanticMapperService` to perform LLM-based mapping:
* **Endpoint & Model**: Connect to a local Ollama service at `http://localhost:11434` running the `qwen3.5:9b` model (configurable in `application.yml` via properties).
* **Data Context Retrieval**:
  * Retrieve 10 randomized sample values from existing database tables for each canonical target field to represent actual distributions (e.g. `SELECT field FROM canonical.table ORDER BY random() LIMIT 10`).
  * Retrieve 10 sample values from the newly ingested data source CSV for the incoming column.
* **LLM Prompting**:
  * Feed the schema names, descriptions, 10 canonical samples, and 10 incoming source samples to the LLM.
  * Request a structured JSON mapping decision outlining target `canonicalEntity`, `canonicalField`, and `confidence` score (clamped between 0.00 and 1.00). If no match is found, return empty strings for the target.
* **Execution Frequency**: The LLM matcher runs exactly **once** upon data source registration or schema drift detection. Generated mappings are saved to `field_mappings` table and reused sequentially for subsequent ingestion batch processing.

#### 2. Dynamic JSONB Catch-all (`additional_attributes`)
* In the canonical database schema, verify/create a `additional_attributes` (JSONB) column in each of the primary tables (`customers`, `products`, `orders`, etc.).
* During payload transformation in `TransformationService`, fields that do not map to any first-class canonical column must not be ignored. Instead, serialize them into the `additional_attributes` JSONB payload.

#### 3. Dynamic Attribute Promotion & Demotion APIs
Expose REST endpoints on `MappingController` (or a dedicated schema management controller):
* **`POST /api/v1/schema/promote`**:
  * Parameters: `entityName` (e.g. `"orders"`), `attributeKey` (e.g. `"discount"`), `dataType` (e.g. `"DECIMAL"`).
  * Actions:
    1. Executes dynamic DDL statement: `ALTER TABLE canonical.{entityName} ADD COLUMN {attributeKey} {postgres_type}`.
    2. Migrates historical records: Transfers the value for `attributeKey` from the `additional_attributes` JSONB column into the newly created dedicated column.
    3. Updates registry metadata so future matching maps straight to the first-class column.
* **`POST /api/v1/schema/demote`**:
  * Parameters: `entityName` (e.g. `"orders"`), `columnName` (e.g. `"discount"`).
  * Actions:
    1. Migrates historical records: Transfers the value from the dedicated column back into the `additional_attributes` JSONB payload.
    2. Drops the dedicated column from the database table: `ALTER TABLE canonical.{entityName} DROP COLUMN {columnName}`.
    3. Updates registry metadata to demote it.

---

### Verification and Test Spec
1. **Integration and DDL Tests**:
   * Write unit/integration tests confirming Ollama prompt format serialization.
   * Write tests for database schema modification. Assert that calling promote creates the column and migrates JSONB values. Assert that calling demote drops it and preserves data inside JSONB.
2. **Mocking Ollama**:
   * Mock the Ollama / LangChain4j client response in unit test profiles to ensure local build builds pass without a running Ollama container.
