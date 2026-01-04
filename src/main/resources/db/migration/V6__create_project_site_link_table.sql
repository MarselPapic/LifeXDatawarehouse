CREATE TABLE ProjectSite (
    ProjectSiteID UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    ProjectID     UUID NOT NULL,
    SiteID        UUID NOT NULL,
    CONSTRAINT fk_projectsite_project FOREIGN KEY (ProjectID) REFERENCES Project(ProjectID),
    CONSTRAINT fk_projectsite_site FOREIGN KEY (SiteID) REFERENCES Site(SiteID),
    CONSTRAINT uq_projectsite UNIQUE (ProjectID, SiteID)
);

CREATE INDEX idx_projectsite_project ON ProjectSite(ProjectID);
CREATE INDEX idx_projectsite_site ON ProjectSite(SiteID);

INSERT INTO ProjectSite (ProjectID, SiteID)
SELECT DISTINCT ProjectID, SiteID
FROM Site;
