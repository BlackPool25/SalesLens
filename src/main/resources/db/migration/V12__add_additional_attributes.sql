ALTER TABLE canonical.customers ADD COLUMN IF NOT EXISTS additional_attributes JSONB;
ALTER TABLE canonical.products ADD COLUMN IF NOT EXISTS additional_attributes JSONB;
ALTER TABLE canonical.salespersons ADD COLUMN IF NOT EXISTS additional_attributes JSONB;
ALTER TABLE canonical.orders ADD COLUMN IF NOT EXISTS additional_attributes JSONB;
ALTER TABLE canonical.order_line_items ADD COLUMN IF NOT EXISTS additional_attributes JSONB;
