CREATE TABLE conflict_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(50)  NOT NULL,
    entity_id           UUID         NOT NULL,
    field_name          VARCHAR(255) NOT NULL,
    source_a_id         UUID         NOT NULL REFERENCES data_sources(id),
    source_b_id         UUID         NOT NULL REFERENCES data_sources(id),
    value_a             TEXT,
    value_b             TEXT,
    resolution_strategy VARCHAR(50)  NOT NULL DEFAULT 'FLAGGED_FOR_REVIEW',
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    resolved_by         BIGINT       REFERENCES users(id),
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);
