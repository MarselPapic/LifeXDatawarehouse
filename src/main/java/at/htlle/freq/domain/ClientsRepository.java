package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository interface for {@link Clients} endpoints.
 */
public interface ClientsRepository {
    /**
     * Retrieves a client by its identifier.
     *
     * @param id client primary key
     * @return optional containing the client when found
     */
    Optional<Clients> findById(UUID id);

    /**
     * Returns all clients installed at a specific site.
     *
     * @param siteId identifier of the hosting {@link Site}
     * @return list of client devices assigned to the site
     */
    List<Clients> findBySite(UUID siteId);

    /**
     * Persists the provided client aggregate.
     *
     * @param client client endpoint to store
     * @return the managed entity after persistence
     */
    Clients save(Clients client);

    /**
     * Lists all clients across all sites.
     *
     * @return snapshot of every client endpoint
     */
    List<Clients> findAll();
}
