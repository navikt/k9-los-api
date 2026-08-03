-- Rename saksbehandlerid to navident
ALTER TABLE saksbehandler RENAME COLUMN saksbehandlerid TO navident;

-- Add enhet column
ALTER TABLE saksbehandler ADD COLUMN enhet varchar(200) NULL;

-- Copy enhet from data jsonb column to the new enhet column
UPDATE saksbehandler SET enhet = data->>'enhet' WHERE data IS NOT NULL AND data->>'enhet' IS NOT NULL;

-- Drop the data column
ALTER TABLE saksbehandler DROP COLUMN data;

