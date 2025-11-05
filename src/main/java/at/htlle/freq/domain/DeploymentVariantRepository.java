package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository contract for {@link DeploymentVariant} definitions.
 */
public interface DeploymentVariantRepository {
    /**
     * Finds a deployment variant by its identifier.
     *
     * @param id primary key
     * @return optional containing the variant when present
     */
    Optional<DeploymentVariant> findById(UUID id);

    /**
     * Looks up a variant via its business code.
     *
     * @param code unique variant code
     * @return optional result
     */
    Optional<DeploymentVariant> findByCode(String code);

    /**
     * Searches a variant by its descriptive name.
     *
     * @param name descriptive label
     * @return optional result
     */
    Optional<DeploymentVariant> findByName(String name);

    /**
     * Persists the provided deployment variant.
     *
     * @param dv variant definition to store
     * @return the managed entity after persistence
     */
    DeploymentVariant save(DeploymentVariant dv);

    /**
     * Returns all registered deployment variants.
     *
     * @return list of variants
     */
    List<DeploymentVariant> findAll();
}
