package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Captures the relationship between a {@link Site} and the {@link Software}
 * packages that are installed there. The entity optionally stores the current
 * operational {@code status} using {@link InstalledSoftwareStatus} values to
 * support upgrade planning.
 */
public class InstalledSoftware {
    private UUID installedSoftwareID;
    private UUID siteID;
    private UUID softwareID;
    private String status;
    private String offeredDate;
    private String installedDate;
    private String rejectedDate;
    private String outdatedDate;

    /**
     * Creates a new InstalledSoftware instance.
     */
    public InstalledSoftware() {}
    /**
     * Creates a new InstalledSoftware instance and initializes it with the provided values.
     * @param installedSoftwareID installed software identifier.
     * @param siteID site identifier.
     * @param softwareID software identifier.
     */
    public InstalledSoftware(UUID installedSoftwareID, UUID siteID, UUID softwareID) {
        this(installedSoftwareID, siteID, softwareID, null, null, null, null, null);
    }

    /**
     * Creates a new InstalledSoftware instance and initializes it with the provided values.
     * @param installedSoftwareID installed software identifier.
     * @param siteID site identifier.
     * @param softwareID software identifier.
     * @param status status.
     */
    public InstalledSoftware(UUID installedSoftwareID, UUID siteID, UUID softwareID, String status) {
        this(installedSoftwareID, siteID, softwareID, status, null, null, null, null);
    }

    /**
     * Creates a new InstalledSoftware instance and initializes it with the provided values.
     * @param installedSoftwareID installed software identifier.
     * @param siteID site identifier.
     * @param softwareID software identifier.
     * @param status status.
     * @param offeredDate offered date.
     * @param installedDate installed date.
     * @param rejectedDate rejected date.
     * @param outdatedDate outdated date.
     */
    public InstalledSoftware(UUID installedSoftwareID, UUID siteID, UUID softwareID, String status,
                             String offeredDate, String installedDate, String rejectedDate, String outdatedDate) {
        this.installedSoftwareID = installedSoftwareID;
        this.siteID = siteID;
        this.softwareID = softwareID;
        this.status = status;
        this.offeredDate = offeredDate;
        this.installedDate = installedDate;
        this.rejectedDate = rejectedDate;
        this.outdatedDate = outdatedDate;
    }

    /**
     * Returns the Installed Software ID value held by this instance.
     * @return the Installed Software ID value.
     */
    public UUID getInstalledSoftwareID() { return installedSoftwareID; }
    /**
     * Sets the Installed Software ID value and updates the current state.
     * @param installedSoftwareID installed software identifier.
     */
    public void setInstalledSoftwareID(UUID installedSoftwareID) { this.installedSoftwareID = installedSoftwareID; }

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
     * Returns the Software ID value held by this instance.
     * @return the Software ID value.
     */
    public UUID getSoftwareID() { return softwareID; }
    /**
     * Sets the Software ID value and updates the current state.
     * @param softwareID software identifier.
     */
    public void setSoftwareID(UUID softwareID) { this.softwareID = softwareID; }

    /**
     * Returns the Status value held by this instance.
     * @return the Status value.
     */
    public String getStatus() { return status; }
    /**
     * Sets the Status value and updates the current state.
     * @param status status.
     */
    public void setStatus(String status) { this.status = status; }

    /**
     * Returns the Offered Date value held by this instance.
     * @return the Offered Date value.
     */
    public String getOfferedDate() { return offeredDate; }
    /**
     * Sets the Offered Date value and updates the current state.
     * @param offeredDate offered date.
     */
    public void setOfferedDate(String offeredDate) { this.offeredDate = offeredDate; }

    /**
     * Returns the Installed Date value held by this instance.
     * @return the Installed Date value.
     */
    public String getInstalledDate() { return installedDate; }
    /**
     * Sets the Installed Date value and updates the current state.
     * @param installedDate installed date.
     */
    public void setInstalledDate(String installedDate) { this.installedDate = installedDate; }

    /**
     * Returns the Rejected Date value held by this instance.
     * @return the Rejected Date value.
     */
    public String getRejectedDate() { return rejectedDate; }
    /**
     * Sets the Rejected Date value and updates the current state.
     * @param rejectedDate rejected date.
     */
    public void setRejectedDate(String rejectedDate) { this.rejectedDate = rejectedDate; }

    /**
     * Returns the Outdated Date value held by this instance.
     * @return the Outdated Date value.
     */
    public String getOutdatedDate() { return outdatedDate; }
    /**
     * Sets the Outdated Date value and updates the current state.
     * @param outdatedDate outdated date.
     */
    public void setOutdatedDate(String outdatedDate) { this.outdatedDate = outdatedDate; }
}
