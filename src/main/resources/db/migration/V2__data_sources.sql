CREATE TABLE data_sources (
      id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
      name              VARCHAR(255) NOT NULL,
      source_type       VARCHAR(50)  NOT NULL,
      trust_score       NUMERIC(3,2) NOT NULL DEFAULT 0.5,
      active            BOOLEAN      NOT NULL DEFAULT true,
      last_sync_at      TIMESTAMP,
      connection_config JSONB,
      created_by        BIGINT       NOT NULL REFERENCES users(id),
      created_at        TIMESTAMP    NOT NULL DEFAULT now(),
      updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);