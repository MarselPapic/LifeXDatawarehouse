package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository abstraction for {@link Radio} devices.
 */
public interface RadioRepository {
    /**
     * Looks up a radio by its identifier.
     *
     * @param id primary key of the radio
     * @return optional radio entity
     */
    Optional<Radio> findById(UUID id);

    /**
     * Retrieves all radios located at a site.
     *
     * @param siteId hosting {@link Site} identifier
     * @return list of radios deployed at the site
     */
    List<Radio> findBySite(UUID siteId);

    /**
     * Persists the supplied radio entity.
     *
     * @param radio radio to store
     * @return managed entity after persistence
     */
    Radio save(Radio radio);

    /**
     * Returns all radios in the repository.
     *
     * @return snapshot of radios
     */
    List<Radio> findAll();
}
