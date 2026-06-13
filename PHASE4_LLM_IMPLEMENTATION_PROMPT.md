# Prompt for Builder Agent: LLM Schema Matching & Dynamic Schema Promotion

You are a **Senior Backend Engineer / Agent** tasked with upgrading Phase 4 of the SalesLens platform to use LLM-based schema matching, catch-all JSONB attributes, and dynamic schema promotion/demotion REST APIs. 

You must strictly adhere to the following agentic guidelines, technical specifications, and testing criteria.

---

## Agentic & Tooling Rules

1. **Plan Gate First (No Assumptions)**:
   * Before writing, editing, or deleting any code, you **must** output a detailed implementation plan.
   * List all design choices and assumptions explicitly, tagged as `[ASSUMPTION]`.
   * **Do not assume design choices.** Ask the user questions for clarification to ensure high-quality output. Wait for the user's explicit approval on your plan before writing any code.
2. **Context7 & Web Search Mandatory Check**:
   * Before designing or implementing, you **must** call the `context7_resolve-library-id` and `context7_query-docs` tools (or perform `search_web` queries) to look up up-to-date documentation on:
     * LangChain4j Ollama integration and client configuration in Spring Boot.
     * Dynamic schema manipulation and execution of raw DDL statements (`ALTER TABLE`) via Spring JDBC (`JdbcTemplate`).
     * PostgreSQL syntax for migrating fields to and from JSONB (`jsonb_set`, `#-` operators, extraction).
3. **Spec-Driven Execution**:
   * Build only what is specified in this document. Avoid speculative dependencies or architectural bloat.

---

## Technical Specifications

### 1. Ollama-Based Schema Matching
* **Ollama Client**:
  * Integrate LangChain4j's Ollama module (model name `qwen3.5:9b`, base URL `http://localhost:11434` - both configurable via `application.yml` properties).
* **Sample Data Context Engine**:
  * For each target canonical field, retrieve **10 randomized sample values** from the database (e.g. `SELECT field FROM canonical.table ORDER BY random() LIMIT 10`).
  * For each incoming data source CSV column, retrieve **10 sample values** from the staged records.
* **LLM Prompts & Mapping Execution**:
  * Format a system/user prompt detailing the canonical schema fields (name, expected type, synonym aliases, descriptions, 10 DB examples) and the incoming CSV column name + its 10 raw examples.
  * Require the LLM to output a clean, confident JSON block identifying `canonicalEntity`, `canonicalField`, and a confidence score (`0.00` to `1.00`). If no match is found, return blank target strings.
  * Run the LLM match **exactly once** during schema initialization or when drift is detected. Save the results to the `field_mappings` table for future ingestion reuse.

### 2. Catch-All JSONB Storage (`additional_attributes`)
* In the canonical database schema tables, verify or add an `additional_attributes` (JSONB) column.
* Update `TransformationService` so that any dynamic or unmapped incoming source fields are not ignored, but instead serialized and stored within the `additional_attributes` column on the target canonical entity during ingestion.

### 3. Dynamic Attribute Promotion & Demotion APIs
Create a schema management controller containing two REST endpoints:
* **Promotion (`POST /api/v1/schema/promote`)**:
  * Request Body / Params: `entityName` (e.g. `"orders"`), `attributeKey` (e.g. `"discount"`), `dataType` (e.g. `"DECIMAL"`).
  * logic:
    1. Run a dynamic database DDL statement to add the new column to the table: `ALTER TABLE canonical.{entityName} ADD COLUMN {attributeKey} {POSTGRES_TYPE}`.
    2. Run a migration query to extract values from `additional_attributes` and write them to the new column: e.g. `UPDATE canonical.{entityName} SET {attributeKey} = ({additional_attributes}->>'{attributeKey}')::{POSTGRES_TYPE}`.
    3. Update the mapping registry metadata so future ingestion mappings bind directly to the new dedicated column.
* **Demotion (`POST /api/v1/schema/demote`)**:
  * Request Body / Params: `entityName`, `columnName`.
  * Logic:
    1. Run a database query to serialize values from the dedicated column back into the `additional_attributes` JSONB payload (e.g. using `jsonb_build_object` or `jsonb_set`).
    2. Drop the column: `ALTER TABLE canonical.{entityName} DROP COLUMN {columnName}`.
    3. Update the mapping metadata back to JSONB.

---

## Testing & Quality Assurance (Crucial)

You must write comprehensive unit and integration tests in the Spring Boot test suites.
* **Feature-Driven Testing**: Tests must validate the correctness, reliability, and edge cases of the feature itself, rather than mock verification constructed to bypass check failures.
* **Ollama Mocking**: Provide a mock bean/profile for the Ollama LangChain4j client so local builds compile and pass test phases when Ollama is offline.
* **Database DDL & Migration Assertions**:
  * Write integration tests that invoke the Promotion API, insert records with dynamic keys, and assert that the Postgres schema changes and the dynamic values migrate accurately.
  * Write corresponding tests for the Demotion API, asserting column drops and JSONB payload preservation.
  * Test transaction safety: ensure database changes roll back cleanly if a migration query fails.
