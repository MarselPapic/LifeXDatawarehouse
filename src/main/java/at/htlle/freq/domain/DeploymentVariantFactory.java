package at.htlle.freq.domain;

import org.springframework.stereotype.Component;

/**
 * Factory responsible for creating Deployment Variant instances.
 */
@Component
public class DeploymentVariantFactory {
    /**
     * Produces a {@link DeploymentVariant} definition based on business
     * metadata. New variants receive a {@code null} id so that persistence can
     * allocate the UUID.
     *
     * @param code unique code shared with SAP
     * @param name descriptive label
     * @param description human readable explanation of the variant
     * @param active flag indicating whether the variant may be used for new projects
     * @return transient deployment variant entity
     */
    public DeploymentVariant create(String code, String name, String description, boolean active) {
        return new DeploymentVariant(null, code, name, description, active);
    }
}
