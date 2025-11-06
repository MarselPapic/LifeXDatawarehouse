-- Rename installed software status values and align constraints/defaults
ALTER TABLE InstalledSoftware
    DROP CONSTRAINT IF EXISTS ck_instsw_status;

UPDATE InstalledSoftware
SET Status = CASE
    WHEN Status = 'Active' THEN 'Installed'
    WHEN Status = 'Pending' THEN 'Offered'
    WHEN Status = 'Retired' THEN 'Rejected'
    ELSE Status
END;

ALTER TABLE InstalledSoftware
    ALTER COLUMN Status SET DEFAULT 'Offered';

ALTER TABLE InstalledSoftware
    ADD CONSTRAINT ck_instsw_status CHECK (Status IN ('Offered','Installed','Rejected'));
