CREATE TABLE quality_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID      NOT NULL REFERENCES ingestion_jobs(id),
    source_id       UUID      NOT NULL REFERENCES data_sources(id),
    total_records   INTEGER   NOT NULL,
    total_issues    INTEGER   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE quality_scores (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID         NOT NULL REFERENCES quality_runs(id),
    source_id       UUID         NOT NULL REFERENCES data_sources(id),
    dimension       VARCHAR(30)  NOT NULL,
    score           NUMERIC(5,4) NOT NULL,
    overall_score   NUMERIC(5,4),
    grade           CHAR(1),
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE quality_issues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID         NOT NULL REFERENCES quality_runs(id),
    source_id       UUID         NOT NULL REFERENCES data_sources(id),
    record_id       UUID         REFERENCES staged_records(id),
    dimension       VARCHAR(30)  NOT NULL,
    rule_code       VARCHAR(50)  NOT NULL,
    severity        VARCHAR(20)  NOT NULL,
    field_name      VARCHAR(255),
    field_value     TEXT,
    message         TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
