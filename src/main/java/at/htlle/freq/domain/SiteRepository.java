package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository interface for {@link Site} aggregates.
 */
public interface SiteRepository {
    /**
     * Finds a site by its identifier.
     *
     * @param id primary key
     * @return optional site entity
     */
    Optional<Site> findById(UUID id);

    /**
     * Returns all sites that belong to a project.
     *
     * @param projectId identifier of the {@link Project}
     * @return list of sites under the project
     */
    List<Site> findByProject(UUID projectId);

    /**
     * Persists the provided site aggregate.
     *
     * @param site site to store
     * @return managed site entity
     */
    Site save(Site site);

    /**
     * Lists all sites available in the repository.
     *
     * @return snapshot of sites
     */
    List<Site> findAll();

    /**
     * Deletes a site by its identifier.
     *
     * @param id primary key
     */
    void deleteById(UUID id);
}
