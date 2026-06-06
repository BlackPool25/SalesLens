CREATE TABLE data_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID      NOT NULL REFERENCES data_sources(id),
    schema_id       UUID      NOT NULL REFERENCES source_schemas(id),
    job_id          UUID      NOT NULL REFERENCES ingestion_jobs(id),
    total_records   INTEGER   NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE field_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID         NOT NULL REFERENCES data_profiles(id),
    field_name      VARCHAR(255) NOT NULL,
    null_rate       NUMERIC(5,4) NOT NULL DEFAULT 0.0,
    unique_count    INTEGER      NOT NULL DEFAULT 0,
    top_values      JSONB,
    min_value       VARCHAR(255),
    max_value       VARCHAR(255),
    sample_values   JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
