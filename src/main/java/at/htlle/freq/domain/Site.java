package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Represents a physical deployment location that belongs to a
 * {@link Project}. Each site points to an {@link Address} record, may host
 * multiple {@link Clients} client workstations, and provides metadata such as
 * fire zone and tenant counts for operational planning.
 */
public class Site {
    private UUID siteID;
    private String siteName;
    private UUID projectID;
    private UUID addressID;
    private String fireZone;
    private Integer tenantCount;

    public Site() {}
    public Site(UUID siteID, String siteName, UUID projectID, UUID addressID, String fireZone, Integer tenantCount) {
        this.siteID = siteID;
        this.siteName = siteName;
        this.projectID = projectID;
        this.addressID = addressID;
        this.fireZone = fireZone;
        this.tenantCount = tenantCount;
    }

    public UUID getSiteID() { return siteID; }
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public UUID getProjectID() { return projectID; }
    public void setProjectID(UUID projectID) { this.projectID = projectID; }

    public UUID getAddressID() { return addressID; }
    public void setAddressID(UUID addressID) { this.addressID = addressID; }

    public String getFireZone() { return fireZone; }
    public void setFireZone(String fireZone) { this.fireZone = fireZone; }

    public Integer getTenantCount() { return tenantCount; }
    public void setTenantCount(Integer tenantCount) { this.tenantCount = tenantCount; }
}
