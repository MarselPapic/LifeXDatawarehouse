package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository interface for {@link Clients} client workstations.
 */
public interface ClientsRepository {
    /**
     * Retrieves a client workstation by its identifier.
     *
     * @param id client workstation primary key
     * @return optional containing the client workstation when found
     */
    Optional<Clients> findById(UUID id);

    /**
     * Returns all client workstations installed at a specific site.
     *
     * @param siteId identifier of the hosting {@link Site}
     * @return list of client workstations assigned to the site
     */
    List<Clients> findBySite(UUID siteId);

    /**
     * Persists the provided client workstation aggregate.
     *
     * @param client client workstation to store
     * @return the managed entity after persistence
     */
    Clients save(Clients client);

    /**
     * Lists all client workstations across all sites.
     *
     * @return snapshot of every client workstation
     */
    List<Clients> findAll();
}
