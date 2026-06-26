-- V13: Quality Engine schema reshape
-- 1. Add 'expression' column to quality_rules (missing from V7)
ALTER TABLE quality_rules ADD COLUMN IF NOT EXISTS expression VARCHAR(500);

-- 2. Add 'run_timestamp' to quality_runs
ALTER TABLE quality_runs ADD COLUMN IF NOT EXISTS run_timestamp TIMESTAMP NOT NULL DEFAULT now();
-- Rename total_issues -> failed_records for spec alignment (if old name exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='quality_runs' AND column_name='total_issues'
    ) THEN
        ALTER TABLE quality_runs RENAME COLUMN total_issues TO failed_records;
    END IF;
END $$;

-- 3. Drop the per-dimension quality_scores table and replace with flat per-job entity
DROP TABLE IF EXISTS quality_scores CASCADE;

CREATE TABLE quality_scores (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID         NOT NULL UNIQUE REFERENCES ingestion_jobs(id),
    source_id           UUID         NOT NULL REFERENCES data_sources(id),
    score_completeness  NUMERIC(5,4) NOT NULL DEFAULT 0,
    score_validity      NUMERIC(5,4) NOT NULL DEFAULT 0,
    score_uniqueness    NUMERIC(5,4) NOT NULL DEFAULT 0,
    score_consistency   NUMERIC(5,4) NOT NULL DEFAULT 0,
    score_timeliness    NUMERIC(5,4) NOT NULL DEFAULT 0,
    score_accuracy      NUMERIC(5,4) NOT NULL DEFAULT 0,
    score_overall       NUMERIC(5,4) NOT NULL DEFAULT 0,
    letter_grade        CHAR(1)      NOT NULL DEFAULT 'F',
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

-- 4. Rejected records log table
CREATE TABLE IF NOT EXISTS rejected_records (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id      UUID         NOT NULL REFERENCES quality_runs(id),
    record_id   UUID         REFERENCES staged_records(id),
    reason      TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
