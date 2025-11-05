package at.htlle.freq.domain;

import java.util.UUID;

public class Project {
    private UUID projectID;
    private String projectSAPID;
    private String projectName;
    private UUID deploymentVariantID;
    private String bundleType;
    private String createDateTime; // ggf. später auf LocalDate/Time ändern
    private ProjectLifecycleStatus lifecycleStatus;
    private UUID accountID;
    private UUID addressID;

    public Project() {}
    public Project(UUID projectID, String projectSAPID, String projectName, UUID deploymentVariantID,
                   String bundleType, String createDateTime, ProjectLifecycleStatus lifecycleStatus,
                   UUID accountID, UUID addressID) {
        this.projectID = projectID;
        this.projectSAPID = projectSAPID;
        this.projectName = projectName;
        this.deploymentVariantID = deploymentVariantID;
        this.bundleType = bundleType;
        this.createDateTime = createDateTime;
        this.lifecycleStatus = lifecycleStatus;
        this.accountID = accountID;
        this.addressID = addressID;
    }

    public UUID getProjectID() { return projectID; }
    public void setProjectID(UUID projectID) { this.projectID = projectID; }

    public String getProjectSAPID() { return projectSAPID; }
    public void setProjectSAPID(String projectSAPID) { this.projectSAPID = projectSAPID; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public UUID getDeploymentVariantID() { return deploymentVariantID; }
    public void setDeploymentVariantID(UUID deploymentVariantID) { this.deploymentVariantID = deploymentVariantID; }

    public String getBundleType() { return bundleType; }
    public void setBundleType(String bundleType) { this.bundleType = bundleType; }

    public String getCreateDateTime() { return createDateTime; }
    public void setCreateDateTime(String createDateTime) { this.createDateTime = createDateTime; }

    public ProjectLifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(ProjectLifecycleStatus lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    public UUID getAccountID() { return accountID; }
    public void setAccountID(UUID accountID) { this.accountID = accountID; }

    public UUID getAddressID() { return addressID; }
    public void setAddressID(UUID addressID) { this.addressID = addressID; }
}
