ALTER TABLE canonical.salespersons
    ADD COLUMN IF NOT EXISTS quality_score NUMERIC(5,4);

ALTER TABLE canonical.salespersons
    ADD COLUMN IF NOT EXISTS has_conflicts BOOLEAN NOT NULL DEFAULT false;
