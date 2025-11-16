package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository interface for {@link Server} infrastructure nodes.
 */
public interface ServerRepository {
    /**
     * Finds a server by its identifier.
     *
     * @param id primary key
     * @return optional server entity
     */
    Optional<Server> findById(UUID id);

    /**
     * Returns all servers located at a site.
     *
     * @param siteId identifier of the {@link Site}
     * @return list of servers running at the site
     */
    List<Server> findBySite(UUID siteId);

    /**
     * Persists the provided server aggregate.
     *
     * @param server server to store
     * @return managed server entity
     */
    Server save(Server server);

    /**
     * Lists all server records.
     *
     * @return snapshot of every server
     */
    List<Server> findAll();

    /**
     * Deletes a server by its identifier.
     *
     * @param id primary key
     */
    void deleteById(UUID id);
}
