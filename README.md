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
mvn test
```

### Running End-to-End Verification
An automated Python integration script validates user registration, authentication, data source registration, CSV ingestion, schema inference, and schema drift handling:
```bash
python3 /home/lightdesk/.gemini/antigravity/brain/8d2eb9c0-4148-47e1-8c3f-9b8ddb13f5c6/scratch/verify_e2e.py
```
*(Alternatively, copy/run the script from the scratch directory.)*

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
