package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Stores catalog information for software packages that can be deployed to
 * {@link Site} installations. Release metadata and lifecycle milestones are
 * tracked to support compliance and maintenance scheduling.
 */
public class Software {
    private UUID softwareID;
    private String name;
    private String release;
    private String revision;
    private String supportPhase;   // Preview / Production / EoL
    private String licenseModel;
    private Boolean thirdParty;
    private String endOfSalesDate;
    private String supportStartDate;
    private String supportEndDate;

    public Software() {}
    public Software(UUID softwareID, String name, String release, String revision, String supportPhase,
                    String licenseModel, Boolean thirdParty, String endOfSalesDate, String supportStartDate, String supportEndDate) {
        this.softwareID = softwareID;
        this.name = name;
        this.release = release;
        this.revision = revision;
        this.supportPhase = supportPhase;
        this.licenseModel = licenseModel;
        this.thirdParty = thirdParty;
        this.endOfSalesDate = endOfSalesDate;
        this.supportStartDate = supportStartDate;
        this.supportEndDate = supportEndDate;
    }

    public UUID getSoftwareID() { return softwareID; }
    public void setSoftwareID(UUID softwareID) { this.softwareID = softwareID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRelease() { return release; }
    public void setRelease(String release) { this.release = release; }

    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }

    public String getSupportPhase() { return supportPhase; }
    public void setSupportPhase(String supportPhase) { this.supportPhase = supportPhase; }

    public String getLicenseModel() { return licenseModel; }
    public void setLicenseModel(String licenseModel) { this.licenseModel = licenseModel; }

    public Boolean getThirdParty() { return thirdParty; }
    public boolean isThirdParty() { return Boolean.TRUE.equals(thirdParty); }
    public void setThirdParty(Boolean thirdParty) { this.thirdParty = thirdParty; }
    public void setThirdParty(boolean thirdParty) { this.thirdParty = thirdParty; }

    public String getEndOfSalesDate() { return endOfSalesDate; }
    public void setEndOfSalesDate(String endOfSalesDate) { this.endOfSalesDate = endOfSalesDate; }

    public String getSupportStartDate() { return supportStartDate; }
    public void setSupportStartDate(String supportStartDate) { this.supportStartDate = supportStartDate; }

    public String getSupportEndDate() { return supportEndDate; }
    public void setSupportEndDate(String supportEndDate) { this.supportEndDate = supportEndDate; }
}
