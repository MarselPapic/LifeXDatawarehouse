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

    public InstalledSoftware() {}
    public InstalledSoftware(UUID installedSoftwareID, UUID siteID, UUID softwareID) {
        this(installedSoftwareID, siteID, softwareID, null);
    }

    public InstalledSoftware(UUID installedSoftwareID, UUID siteID, UUID softwareID, String status) {
        this.installedSoftwareID = installedSoftwareID;
        this.siteID = siteID;
        this.softwareID = softwareID;
        this.status = status;
    }

    public UUID getInstalledSoftwareID() { return installedSoftwareID; }
    public void setInstalledSoftwareID(UUID installedSoftwareID) { this.installedSoftwareID = installedSoftwareID; }

    public UUID getSiteID() { return siteID; }
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    public UUID getSoftwareID() { return softwareID; }
    public void setSoftwareID(UUID softwareID) { this.softwareID = softwareID; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
