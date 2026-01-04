package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Aggregates all information about a deployment project that is executed for
 * a customer {@link Account}. Projects reference the {@link DeploymentVariant}
 * that governs the technical scope, track lifecycle state via
 * {@link ProjectLifecycleStatus}, capture special installation notes, and own
 * subordinate {@link Site} locations.
 */
public class Project {
    private UUID projectID;
    private String projectSAPID;
    private String projectName;
    private UUID deploymentVariantID;
    private String bundleType;
    private String createDateTime; // potentially switch to LocalDate/Time later
    private ProjectLifecycleStatus lifecycleStatus;
    private UUID accountID;
    private UUID addressID;
    private String specialNotes;

    /**
     * Creates a new Project instance.
     */
    public Project() {}
    /**
     * Creates a project with all attributes set.
     *
     * @param projectID project identifier.
     * @param projectSAPID project SAP identifier.
     * @param projectName project name.
     * @param deploymentVariantID deployment variant identifier.
     * @param bundleType bundle type.
     * @param createDateTime creation timestamp string.
     * @param lifecycleStatus lifecycle status.
     * @param accountID account identifier.
     * @param addressID address identifier.
     * @param specialNotes special notes.
     */
    public Project(UUID projectID, String projectSAPID, String projectName, UUID deploymentVariantID,
                   String bundleType, String createDateTime, ProjectLifecycleStatus lifecycleStatus,
                   UUID accountID, UUID addressID, String specialNotes) {
        this.projectID = projectID;
        this.projectSAPID = projectSAPID;
        this.projectName = projectName;
        this.deploymentVariantID = deploymentVariantID;
        this.bundleType = bundleType;
        this.createDateTime = createDateTime;
        this.lifecycleStatus = lifecycleStatus;
        this.accountID = accountID;
        this.addressID = addressID;
        this.specialNotes = specialNotes;
    }

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
     * Returns the Project SAPID value held by this instance.
     * @return the Project SAPID value.
     */
    public String getProjectSAPID() { return projectSAPID; }
    /**
     * Sets the Project SAPID value and updates the current state.
     * @param projectSAPID project sapid.
     */
    public void setProjectSAPID(String projectSAPID) { this.projectSAPID = projectSAPID; }

    /**
     * Returns the Project Name value held by this instance.
     * @return the Project Name value.
     */
    public String getProjectName() { return projectName; }
    /**
     * Sets the Project Name value and updates the current state.
     * @param projectName project name.
     */
    public void setProjectName(String projectName) { this.projectName = projectName; }

    /**
     * Returns the Deployment Variant ID value held by this instance.
     * @return the Deployment Variant ID value.
     */
    public UUID getDeploymentVariantID() { return deploymentVariantID; }
    /**
     * Sets the Deployment Variant ID value and updates the current state.
     * @param deploymentVariantID deployment variant identifier.
     */
    public void setDeploymentVariantID(UUID deploymentVariantID) { this.deploymentVariantID = deploymentVariantID; }

    /**
     * Returns the Bundle Type value held by this instance.
     * @return the Bundle Type value.
     */
    public String getBundleType() { return bundleType; }
    /**
     * Sets the Bundle Type value and updates the current state.
     * @param bundleType bundle type.
     */
    public void setBundleType(String bundleType) { this.bundleType = bundleType; }

    /**
     * Returns the Create Date Time value held by this instance.
     * @return the Create Date Time value.
     */
    public String getCreateDateTime() { return createDateTime; }
    /**
     * Sets the Create Date Time value and updates the current state.
     * @param createDateTime create date time.
     */
    public void setCreateDateTime(String createDateTime) { this.createDateTime = createDateTime; }

    /**
     * Returns the Lifecycle Status value held by this instance.
     * @return the Lifecycle Status value.
     */
    public ProjectLifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
    /**
     * Sets the Lifecycle Status value and updates the current state.
     * @param lifecycleStatus lifecycle status.
     */
    public void setLifecycleStatus(ProjectLifecycleStatus lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    /**
     * Returns the Account ID value held by this instance.
     * @return the Account ID value.
     */
    public UUID getAccountID() { return accountID; }
    /**
     * Sets the Account ID value and updates the current state.
     * @param accountID account identifier.
     */
    public void setAccountID(UUID accountID) { this.accountID = accountID; }

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
     * Returns the Special Notes value held by this instance.
     * @return the Special Notes value.
     */
    public String getSpecialNotes() { return specialNotes; }
    /**
     * Sets the Special Notes value and updates the current state.
     * @param specialNotes special notes.
     */
    public void setSpecialNotes(String specialNotes) { this.specialNotes = specialNotes; }
}
