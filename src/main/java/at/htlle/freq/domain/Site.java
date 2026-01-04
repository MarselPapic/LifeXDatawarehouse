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
    private Integer redundantServers;
    private Boolean highAvailability;

    /**
     * Creates a new Site instance.
     */
    public Site() {}
    /**
     * Creates a site with all attributes set.
     *
     * @param siteID site identifier.
     * @param siteName site name.
     * @param projectID project identifier.
     * @param addressID address identifier.
     * @param fireZone fire zone.
     * @param tenantCount tenant count.
     * @param redundantServers redundant servers.
     * @param highAvailability high availability.
     */
    public Site(UUID siteID, String siteName, UUID projectID, UUID addressID, String fireZone,
                Integer tenantCount, Integer redundantServers, Boolean highAvailability) {
        this.siteID = siteID;
        this.siteName = siteName;
        this.projectID = projectID;
        this.addressID = addressID;
        this.fireZone = fireZone;
        this.tenantCount = tenantCount;
        this.redundantServers = redundantServers;
        this.highAvailability = highAvailability;
    }

    /**
     * Returns the Site ID value held by this instance.
     * @return the Site ID value.
     */
    public UUID getSiteID() { return siteID; }
    /**
     * Sets the Site ID value and updates the current state.
     * @param siteID site identifier.
     */
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    /**
     * Returns the Site Name value held by this instance.
     * @return the Site Name value.
     */
    public String getSiteName() { return siteName; }
    /**
     * Sets the Site Name value and updates the current state.
     * @param siteName site name.
     */
    public void setSiteName(String siteName) { this.siteName = siteName; }

    /**
     * Returns the Project ID value held by this instance.
     * @return the Project ID value.
     */
    public UUID getProjectID() { return projectID; }
    /**
     * Sets the Project ID value and updates the current state.
     * @param projectID project identifier.
     */
    public void setProjectID(UUID projectID) { this.projectID = projectID; }

    /**
     * Returns the Address ID value held by this instance.
     * @return the Address ID value.
     */
    public UUID getAddressID() { return addressID; }
    /**
     * Sets the Address ID value and updates the current state.
     * @param addressID address identifier.
     */
    public void setAddressID(UUID addressID) { this.addressID = addressID; }

    /**
     * Returns the Fire Zone value held by this instance.
     * @return the Fire Zone value.
     */
    public String getFireZone() { return fireZone; }
    /**
     * Sets the Fire Zone value and updates the current state.
     * @param fireZone fire zone.
     */
    public void setFireZone(String fireZone) { this.fireZone = fireZone; }

    /**
     * Returns the Tenant Count value held by this instance.
     * @return the Tenant Count value.
     */
    public Integer getTenantCount() { return tenantCount; }
    /**
     * Sets the Tenant Count value and updates the current state.
     * @param tenantCount tenant count.
     */
    public void setTenantCount(Integer tenantCount) { this.tenantCount = tenantCount; }

    /**
     * Returns the Redundant Servers value held by this instance.
     * @return the Redundant Servers value.
     */
    public Integer getRedundantServers() { return redundantServers; }
    /**
     * Sets the Redundant Servers value and updates the current state.
     * @param redundantServers redundant servers.
     */
    public void setRedundantServers(Integer redundantServers) { this.redundantServers = redundantServers; }

    /**
     * Returns the High Availability value held by this instance.
     * @return true when the condition is met; otherwise false.
     */
    public Boolean getHighAvailability() { return highAvailability; }
    /**
     * Returns whether the site is configured for high availability.
     *
     * @return true when high availability is enabled.
     */
    public boolean isHighAvailability() { return Boolean.TRUE.equals(highAvailability); }
    /**
     * Sets the High Availability value and updates the current state.
     * @param highAvailability high availability.
     */
    public void setHighAvailability(Boolean highAvailability) { this.highAvailability = highAvailability; }
    /**
     * Sets the High Availability value and updates the current state.
     * @param highAvailability high availability.
     */
    public void setHighAvailability(boolean highAvailability) { this.highAvailability = highAvailability; }
}
