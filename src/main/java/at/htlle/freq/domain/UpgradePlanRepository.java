package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository interface for {@link UpgradePlan} schedules.
 */
public interface UpgradePlanRepository {
    /**
     * Finds an upgrade plan by its identifier.
     *
     * @param id primary key
     * @return optional plan entity
     */
    Optional<UpgradePlan> findById(UUID id);

    /**
     * Retrieves plans defined for a site.
     *
     * @param siteId identifier of the {@link Site}
     * @return list of upgrade plans targeting the site
     */
    List<UpgradePlan> findBySite(UUID siteId);

    /**
     * Persists an upgrade plan definition.
     *
     * @param plan plan to store
     * @return managed plan entity
     */
    UpgradePlan save(UpgradePlan plan);

    /**
     * Lists all upgrade plans.
     *
     * @return snapshot of upgrade planning records
     */
    List<UpgradePlan> findAll();

    /**
     * Deletes an upgrade plan by its identifier.
     *
     * @param id primary key
     */
    void deleteById(UUID id);
}
