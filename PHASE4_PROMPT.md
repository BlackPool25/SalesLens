# Phase 4 — Semantic Field Mapping Builder Prompt

## Instructions for the Builder Agent

You are a **builder agent** implementing Phase 4 (Semantic Field Mapping) of the SalesLens multi-source sales data unification platform. Follow the methodologies and specifications below strictly.

---

### Agentic Pipeline Rules

1. **Plan Gate First**: Before touching or creating any files, output a brief design plan outlining which files you will create/modify. List your key assumptions tagged as `[ASSUMPTION]`. Get approval from the user before writing any code.
2. **Context7 Before Implementation**: Before calling Spring Data JPA methods, defining entity classes, adding dependencies, or mapping controllers, you **must** use the `context7_query-docs` tool to verify the APIs exist and work as you expect. Do not guess method signatures or annotation structures.
3. **Spec-Driven Execution**: Implement only the features requested in this specification. Do not add speculative configurations, custom authentication checks (re-use existing JWT filters), or non-essential dependencies.
4. **Stop Condition**: Once compilation passes and unit/E2E test validation is completed, summarize your changes and stop.

---

### Phase 4 Specification: Semantic Field Mapping

The goal of Phase 4 is to map dynamically inferred source fields (staged CSV fields) to canonical entity fields. High-confidence mappings (≥ 0.80) are auto-confirmed, while lower-confidence matches are flagged as `PENDING` for manual confirmation or override.

#### 1. Database Schema
The database table `field_mappings` is already defined in Flyway migrations (see `V5__field_mappings.sql`):
* `id` (UUID, Primary Key)
* `source_id` (UUID, Foreign Key referencing `data_sources`)
* `source_field_name` (VARCHAR)
* `canonical_entity` (VARCHAR)
* `canonical_field` (VARCHAR)
* `confidence` (NUMERIC)
* `status` (VARCHAR, e.g. `'AUTO_CONFIRMED'`, `'PENDING'`, `'IGNORED'`)
* `transform_rule` (VARCHAR)

You must create the `FieldMapping` JPA entity matching this schema. Re-use existing annotations like `@Entity`, `@Table`, and appropriate relational mappings.

#### 2. Canonical Field Registry
Define a static Java configuration registry representing all fields across the canonical tables (from `V10__canonical_schema.sql`):
* **`customers`**:
  * `name` (FREE_TEXT, synonyms: `[customer_name, cust_name, client_name, buyer_name, customer]`)
  * `email` (EMAIL, synonyms: `[mail, email_address, contact_email]`)
  * `phone` (PHONE, synonyms: `[phone_number, contact_no, tel, telephone]`)
  * `segment` (CATEGORY, synonyms: `[cust_segment, group, division]`)
* **`products`**:
  * `sku` (FREE_TEXT, synonyms: `[sku, product_code, item_code, prod_id]`)
  * `name` (FREE_TEXT, synonyms: `[product_name, item, description, prod_name]`)
  * `category` (CATEGORY, synonyms: `[prod_category, category, dept, department]`)
  * `sub_category` (CATEGORY, synonyms: `[sub_category, subcategory, group]`)
  * `unit_price` (DECIMAL, synonyms: `[price, unit_price, cost]`)
  * `currency` (CATEGORY, synonyms: `[curr, currency_code, valuta]`)
* **`orders`**:
  * `order_date` (DATE, synonyms: `[order_date, date, transaction_date]`)
  * `ship_date` (DATE, synonyms: `[ship_date, shipping_date, delivery_date]`)
  * `ship_mode` (CATEGORY, synonyms: `[ship_mode, shipping_method, carrier]`)
  * `shipping_cost` (DECIMAL, synonyms: `[shipping_cost, freight, shipping_fee]`)
  * `total_amount` (DECIMAL, synonyms: `[sales, revenue, total, amount, order_total]`)
  * `currency` (CATEGORY, synonyms: `[curr, currency_code]`)

#### 3. Matching Pipeline (`SemanticMapperService`)
Implement `SemanticMapperService.generateMappings(UUID sourceId, SourceSchema schema)`:
For each `SourceSchemaField` in the schema, find the best canonical target field by scoring similarity:
1. **Normalize Names**: Convert both strings to lowercase and strip all spaces, underscores, and special characters (e.g. `"Order_Date"` and `"orderDate"` both normalize to `"orderdate"`).
2. **Confidence Logic**:
   * **Confidence = 1.0**: Exact match of the normalized source field name against either a normalized canonical field name or any of its normalized synonyms.
   * **Confidence = 0.85**: Levenshtein distance between normalized source field name and normalized canonical name/synonym is $\le 2$ (use standard Levenshtein calculation; do not add external libraries unless verified with Maven/Pom checks).
   * **Confidence = 0.70**: Token overlap. Split source name and canonical name/synonym into tokens (by space or underscore). If the intersection of tokens is $\ge 1$, calculate overlap score (intersection size / union size). If score $\ge 0.5$, assign 0.70.
   * **Confidence = 0.55**: Same inferred type. If the source field's `inferredType` matches the target canonical field's expected type (e.g. `DATE` matches `order_date`), assign 0.55.
   * **Confidence < 0.55**: Mark as `UNMAPPED` (status `IGNORED`, empty target).
3. **Status Confirmation**:
   * If highest confidence $\ge 0.80$, save `FieldMapping` with status `AUTO_CONFIRMED`.
   * If $0.55 \le$ highest confidence $< 0.80$, save `FieldMapping` with status `PENDING`.
   * Otherwise, save as `IGNORED`.

#### 4. REST Endpoints (`MappingController`)
Implement `MappingController` mapping `/api/v1/sources/{sourceId}/mappings`:
* `GET /api/v1/sources/{sourceId}/mappings`: Return all field mappings for the given datasource.
* `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/confirm`: Transition a mapping from `PENDING` to `AUTO_CONFIRMED`.
* `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/override`: Override the canonical entity, canonical field, and change status to `AUTO_CONFIRMED` via request parameters.
* `PUT /api/v1/sources/{sourceId}/mappings/{mappingId}/ignore`: Set status to `IGNORED`.

#### 5. Transformation Service (`TransformationService`)
Implement `TransformationService.transform(StagedRecord record, List<FieldMapping> mappings)`:
Given a staged raw payload JSON record and a list of active `AUTO_CONFIRMED` mappings, map the source columns to a unified, flat Map representation conforming to canonical naming:
* Input: Staged Record payload (e.g. `{"Row ID": "1", "Sales": "123.45"}`)
* Output: Mapped key-value pairs (e.g. `{"order.total_amount": "123.45"}`)
* Unmapped or `IGNORED` columns must be omitted.

---

## Verification Requirements

### 1. Java Unit Tests
Write test cases in `SemanticMapperServiceTest` and `TransformationServiceTest`:
* Validate that `Order Date` maps to `orders.order_date` with confidence 1.0.
* Validate that a slight typo like `Ordr Date` resolves with Levenshtein confidence 0.85.
* Validate that a generic name with correct type defaults to type-based mapping with 0.55 confidence.
* Validate transformation logic mapping raw payloads into canonical entity maps.

### 2. E2E Verification
Update the Python verification pipeline to:
1. Create a data source and ingest a sample file.
2. Query `/api/v1/sources/{id}/mappings` and assert that fields match expected confidence values.
3. Call the confirm/override endpoints to adjust mappings.
4. Verify mappings are successfully updated in the database.
