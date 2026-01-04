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

    /**
     * Creates a new Software instance.
     */
    public Software() {}
    /**
     * Creates a software record with all attributes set.
     *
     * @param softwareID software identifier.
     * @param name name.
     * @param release release.
     * @param revision revision.
     * @param supportPhase support phase.
     * @param licenseModel license model.
     * @param thirdParty whether the software is third-party.
     * @param endOfSalesDate end of sales date.
     * @param supportStartDate support start date.
     * @param supportEndDate support end date.
     */
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
     * Returns the Name value held by this instance.
     * @return the Name value.
     */
    public String getName() { return name; }
    /**
     * Sets the Name value and updates the current state.
     * @param name name.
     */
    public void setName(String name) { this.name = name; }

    /**
     * Returns the Release value held by this instance.
     * @return the Release value.
     */
    public String getRelease() { return release; }
    /**
     * Sets the Release value and updates the current state.
     * @param release release.
     */
    public void setRelease(String release) { this.release = release; }

    /**
     * Returns the Revision value held by this instance.
     * @return the Revision value.
     */
    public String getRevision() { return revision; }
    /**
     * Sets the Revision value and updates the current state.
     * @param revision revision.
     */
    public void setRevision(String revision) { this.revision = revision; }

    /**
     * Returns the Support Phase value held by this instance.
     * @return the Support Phase value.
     */
    public String getSupportPhase() { return supportPhase; }
    /**
     * Sets the Support Phase value and updates the current state.
     * @param supportPhase support phase.
     */
    public void setSupportPhase(String supportPhase) { this.supportPhase = supportPhase; }

    /**
     * Returns the License Model value held by this instance.
     * @return the License Model value.
     */
    public String getLicenseModel() { return licenseModel; }
    /**
     * Sets the License Model value and updates the current state.
     * @param licenseModel license model.
     */
    public void setLicenseModel(String licenseModel) { this.licenseModel = licenseModel; }

    /**
     * Returns the Third Party value held by this instance.
     * @return true when the condition is met; otherwise false.
     */
    public Boolean getThirdParty() { return thirdParty; }
    /**
     * Returns whether the software is marked as third-party.
     *
     * @return true when marked as third-party.
     */
    public boolean isThirdParty() { return Boolean.TRUE.equals(thirdParty); }
    /**
     * Sets the Third Party value and updates the current state.
     * @param thirdParty third party.
     */
    public void setThirdParty(Boolean thirdParty) { this.thirdParty = thirdParty; }
    /**
     * Sets the Third Party value and updates the current state.
     * @param thirdParty third party.
     */
    public void setThirdParty(boolean thirdParty) { this.thirdParty = thirdParty; }

    /**
     * Returns the End Of Sales Date value held by this instance.
     * @return the End Of Sales Date value.
     */
    public String getEndOfSalesDate() { return endOfSalesDate; }
    /**
     * Sets the End Of Sales Date value and updates the current state.
     * @param endOfSalesDate end of sales date.
     */
    public void setEndOfSalesDate(String endOfSalesDate) { this.endOfSalesDate = endOfSalesDate; }

    /**
     * Returns the Support Start Date value held by this instance.
     * @return the Support Start Date value.
     */
    public String getSupportStartDate() { return supportStartDate; }
    /**
     * Sets the Support Start Date value and updates the current state.
     * @param supportStartDate support start date.
     */
    public void setSupportStartDate(String supportStartDate) { this.supportStartDate = supportStartDate; }

    /**
     * Returns the Support End Date value held by this instance.
     * @return the Support End Date value.
     */
    public String getSupportEndDate() { return supportEndDate; }
    /**
     * Sets the Support End Date value and updates the current state.
     * @param supportEndDate support end date.
     */
    public void setSupportEndDate(String supportEndDate) { this.supportEndDate = supportEndDate; }
}
