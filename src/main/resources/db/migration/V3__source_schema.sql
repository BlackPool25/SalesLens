CREATE TABLE source_schemas (
        id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        source_id    UUID        NOT NULL REFERENCES data_sources(id),
        version      INTEGER     NOT NULL DEFAULT 1,
        status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        created_at   TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE TABLE source_schema_fields (
        id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        schema_id      UUID         NOT NULL REFERENCES source_schemas(id),
        field_name     VARCHAR(255) NOT NULL,
        inferred_type  VARCHAR(50),
        detected_format VARCHAR(50),
        nullable       BOOLEAN      NOT NULL DEFAULT true,
        sample_values  JSONB,
        created_at     TIMESTAMP    NOT NULL DEFAULT now()
);