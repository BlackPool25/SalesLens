CREATE TABLE quality_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID         REFERENCES data_sources(id),
    dimension       VARCHAR(30)  NOT NULL,
    rule_code       VARCHAR(50)  NOT NULL,
    description     TEXT,
    severity        VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    config          JSONB,
    active          BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);
