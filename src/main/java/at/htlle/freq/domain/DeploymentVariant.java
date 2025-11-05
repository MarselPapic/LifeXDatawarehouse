package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Defines a standardized deployment blueprint that can be linked to a
 * {@link Project}. Deployment variants carry business identifiers used for
 * reporting and control whether the variant is currently available for new
 * implementations.
 */
public class DeploymentVariant {
    private UUID variantID;
    private String variantCode;
    private String variantName;
    private String description;
    private boolean isActive;

    public DeploymentVariant() {}
    public DeploymentVariant(UUID variantID, String variantCode, String variantName,
                             String description, boolean isActive) {
        this.variantID = variantID;
        this.variantCode = variantCode;
        this.variantName = variantName;
        this.description = description;
        this.isActive = isActive;
    }

    public UUID getVariantID() { return variantID; }
    public void setVariantID(UUID variantID) { this.variantID = variantID; }

    public String getVariantCode() { return variantCode; }
    public void setVariantCode(String variantCode) { this.variantCode = variantCode; }

    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
