ALTER TABLE Software
    ADD COLUMN Version VARCHAR(40);

UPDATE Software
SET Version = CONCAT(Release, '.', Revision)
WHERE Version IS NULL;

ALTER TABLE Software
    ALTER COLUMN Version SET NOT NULL;
