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
    private Boolean active;

    /**
     * Creates a new DeploymentVariant instance.
     */
    public DeploymentVariant() {}
    /**
     * Creates a new DeploymentVariant instance and initializes it with the provided values.
     * @param variantID variant identifier.
     * @param variantCode variant code.
     * @param variantName variant name.
     * @param description description.
     * @param active active.
     */
    public DeploymentVariant(UUID variantID, String variantCode, String variantName,
                             String description, Boolean active) {
        this.variantID = variantID;
        this.variantCode = variantCode;
        this.variantName = variantName;
        this.description = description;
        this.active = active;
    }

    /**
     * Returns the Variant ID value held by this instance.
     * @return the Variant ID value.
     */
    public UUID getVariantID() { return variantID; }
    /**
     * Sets the Variant ID value and updates the current state.
     * @param variantID variant identifier.
     */
    public void setVariantID(UUID variantID) { this.variantID = variantID; }

    /**
     * Returns the Variant Code value held by this instance.
     * @return the Variant Code value.
     */
    public String getVariantCode() { return variantCode; }
    /**
     * Sets the Variant Code value and updates the current state.
     * @param variantCode variant code.
     */
    public void setVariantCode(String variantCode) { this.variantCode = variantCode; }

    /**
     * Returns the Variant Name value held by this instance.
     * @return the Variant Name value.
     */
    public String getVariantName() { return variantName; }
    /**
     * Sets the Variant Name value and updates the current state.
     * @param variantName variant name.
     */
    public void setVariantName(String variantName) { this.variantName = variantName; }

    /**
     * Returns the Description value held by this instance.
     * @return the Description value.
     */
    public String getDescription() { return description; }
    /**
     * Sets the Description value and updates the current state.
     * @param description description.
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * Executes the equals operation.
     * @param Boolean.TRUE.equals(active boolean true equals active.
     * @return true when the condition is met; otherwise false.
     */
    public boolean isActive() { return Boolean.TRUE.equals(active); }

    /**
     * Returns the Active value held by this instance.
     * @return true when the condition is met; otherwise false.
     */
    public Boolean getActive() { return active; }

    /**
     * Sets the Active value and updates the current state.
     * @param active active.
     */
    public void setActive(Boolean active) { this.active = active; }
}
