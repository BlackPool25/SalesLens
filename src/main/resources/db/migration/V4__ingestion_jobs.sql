CREATE TABLE ingestion_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id           UUID        NOT NULL REFERENCES data_sources(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_read          INTEGER     NOT NULL DEFAULT 0,
    total_transformed   INTEGER     NOT NULL DEFAULT 0,
    total_quality_pass  INTEGER     NOT NULL DEFAULT 0,
    total_quality_fail  INTEGER     NOT NULL DEFAULT 0,
    total_loaded        INTEGER     NOT NULL DEFAULT 0,
    total_conflicted    INTEGER     NOT NULL DEFAULT 0,
    error_message       TEXT,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE staged_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID        NOT NULL REFERENCES ingestion_jobs(id),
    source_id       UUID        NOT NULL REFERENCES data_sources(id),
    raw_payload     JSONB       NOT NULL,
    record_hash     VARCHAR(64),
    row_number      INTEGER,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);
