CREATE TABLE data_lineage (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_id    UUID        NOT NULL,
    canonical_type  VARCHAR(50) NOT NULL,
    source_id       UUID        NOT NULL REFERENCES data_sources(id),
    job_id          UUID        NOT NULL REFERENCES ingestion_jobs(id),
    staged_id       UUID        REFERENCES staged_records(id),
    transformations JSONB,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);
