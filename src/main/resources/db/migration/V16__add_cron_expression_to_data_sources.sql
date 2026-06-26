ALTER TABLE data_sources
    ADD COLUMN IF NOT EXISTS cron_expression VARCHAR(100);
