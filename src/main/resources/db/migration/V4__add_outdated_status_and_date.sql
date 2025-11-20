-- Extend InstalledSoftware with Outdated status and timestamp
ALTER TABLE InstalledSoftware
    ADD COLUMN IF NOT EXISTS OutdatedDate DATE;

ALTER TABLE InstalledSoftware
    DROP CONSTRAINT IF EXISTS ck_instsw_status;

ALTER TABLE InstalledSoftware
    ALTER COLUMN Status SET DEFAULT 'Offered';

ALTER TABLE InstalledSoftware
    ADD CONSTRAINT ck_instsw_status CHECK (Status IN ('Offered','Installed','Rejected','Outdated'));

-- Existing rows keep their dates; ensure future inserts default to NULL
UPDATE InstalledSoftware
SET OutdatedDate = NULL
WHERE OutdatedDate IS NULL;
