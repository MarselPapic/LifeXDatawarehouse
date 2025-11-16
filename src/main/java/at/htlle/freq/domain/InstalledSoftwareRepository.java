package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository interface for {@link InstalledSoftware} relationships.
 */
public interface InstalledSoftwareRepository {
    /**
     * Fetches an installation link by its identifier.
     *
     * @param id primary key
     * @return optional installed software record
     */
    Optional<InstalledSoftware> findById(UUID id);

    /**
     * Retrieves all installations located at a site.
     *
     * @param siteId identifier of the {@link Site}
     * @return list of installed software entries
     */
    List<InstalledSoftware> findBySite(UUID siteId);

    /**
     * Retrieves overview rows for a site's installed software enriched with software metadata.
     *
     * @param siteId identifier of the {@link Site}
     * @return list of projection rows containing installation and software details
     */
    List<SiteSoftwareOverview> findOverviewBySite(UUID siteId);

    /**
     * Lists installations for a specific software package.
     *
     * @param softwareId identifier of the {@link Software}
     * @return list of installations referencing the software
     */
    List<InstalledSoftware> findBySoftware(UUID softwareId);

    /**
     * Persists an installed software relationship.
     *
     * @param isw entity to store
     * @return managed entity after persistence
     */
    InstalledSoftware save(InstalledSoftware isw);

    /**
     * Returns all installation relationships.
     *
     * @return list of all entries
     */
    List<InstalledSoftware> findAll();

    /**
     * Deletes an installed software relationship by its identifier.
     *
     * @param id primary key
     */
    void deleteById(UUID id);
}
