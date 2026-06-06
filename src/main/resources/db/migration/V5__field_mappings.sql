CREATE TABLE field_mappings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id           UUID         NOT NULL REFERENCES data_sources(id),
    source_field_name   VARCHAR(255) NOT NULL,
    canonical_entity    VARCHAR(100) NOT NULL,
    canonical_field     VARCHAR(100) NOT NULL,
    confidence          NUMERIC(3,2) NOT NULL DEFAULT 0.0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    transform_rule      VARCHAR(50),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);
