-- Prevent duplicate staged records from Kafka consumer retries/rebalances
-- Records that differ in content will have different hashes — this only catches true duplicates
CREATE UNIQUE INDEX IF NOT EXISTS idx_staged_records_source_hash
ON staged_records(source_id, record_hash)
WHERE record_hash IS NOT NULL;
