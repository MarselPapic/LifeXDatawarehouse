ALTER TABLE InstalledSoftware
    ADD COLUMN Status VARCHAR(12) DEFAULT 'Active';

UPDATE InstalledSoftware
SET Status = 'Active'
WHERE Status IS NULL;

ALTER TABLE InstalledSoftware
    ALTER COLUMN Status SET NOT NULL;

ALTER TABLE InstalledSoftware
    ADD CONSTRAINT ck_instsw_status CHECK (Status IN ('Active','Pending','Retired'));
