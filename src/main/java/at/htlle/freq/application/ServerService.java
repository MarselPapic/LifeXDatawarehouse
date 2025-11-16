// src/main/java/at/htlle/freq/application/ServerService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.Server;
import at.htlle.freq.domain.ServerRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Manages server hardware, validates required fields, and keeps Lucene up to date.
 */
@Service
public class ServerService {

    private static final Logger log = LoggerFactory.getLogger(ServerService.class);

    private final ServerRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index components.
     *
     * @param repo   repository for servers
     * @param lucene Lucene indexing service
     */
    public ServerService(ServerRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all servers.
     *
     * @return list of servers
     */
    public List<Server> getAllServers() {
        return repo.findAll();
    }

    /**
     * Retrieves a server by its identifier.
     *
     * @param id server identifier
     * @return optional containing the server or empty otherwise
     */
    public Optional<Server> getServerById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns servers for a site.
     *
     * @param siteId site identifier
     * @return list of servers
     */
    public List<Server> getServersBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    // ---------- Commands ----------

    /**
     * Saves a server and validates required fields such as site, name, brand, and serial number.
     * Indexes the record in Lucene after the commit.
     *
     * @param incoming server to persist
     * @return stored server
     */
    @Transactional
    public Server createOrUpdateServer(Server incoming) {
        Objects.requireNonNull(incoming, "server payload must not be null");

        if (incoming.getSiteID() == null)
            throw new IllegalArgumentException("SiteID is required");
        if (isBlank(incoming.getServerName()))
            throw new IllegalArgumentException("ServerName is required");
        if (isBlank(incoming.getServerBrand()))
            throw new IllegalArgumentException("ServerBrand is required");
        if (isBlank(incoming.getServerSerialNr()))
            throw new IllegalArgumentException("ServerSerialNr is required");

        Server saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("Server saved: id={} site={} name='{}' brand='{}'",
                saved.getServerID(), saved.getSiteID(), saved.getServerName(), saved.getServerBrand());
        return saved;
    }

    /**
     * Updates a server and synchronizes Lucene.
     *
     * @param id    server identifier
     * @param patch changes to merge into the entity
     * @return optional containing the updated server or empty otherwise
     */
    @Transactional
    public Optional<Server> updateServer(UUID id, Server patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setSiteID(patch.getSiteID() != null ? patch.getSiteID() : existing.getSiteID());
            existing.setServerName(nvl(patch.getServerName(), existing.getServerName()));
            existing.setServerBrand(nvl(patch.getServerBrand(), existing.getServerBrand()));
            existing.setServerSerialNr(nvl(patch.getServerSerialNr(), existing.getServerSerialNr()));
            existing.setServerOS(nvl(patch.getServerOS(), existing.getServerOS()));
            existing.setPatchLevel(nvl(patch.getPatchLevel(), existing.getPatchLevel()));
            existing.setVirtualPlatform(nvl(patch.getVirtualPlatform(), existing.getVirtualPlatform()));
            existing.setVirtualVersion(nvl(patch.getVirtualVersion(), existing.getVirtualVersion()));
            if (patch.getHighAvailability() != null) {
                existing.setHighAvailability(patch.getHighAvailability());
            }

            Server saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("Server updated: id={} name='{}'", id, saved.getServerName());
            return saved;
        });
    }

    /**
     * Deletes a server.
     *
     * @param id server identifier
     */
    @Transactional
    public void deleteServer(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(s -> {
            repo.deleteById(id);
            log.info("Server deleted: id={} name='{}' brand='{}'", id, s.getServerName(), s.getServerBrand());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(Server s) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(s);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexToLucene(s);
            }
        });
    }

    private void indexToLucene(Server s) {
        try {
            lucene.indexServer(
                    s.getServerID() != null ? s.getServerID().toString() : null,
                    s.getSiteID() != null ? s.getSiteID().toString() : null,
                    s.getServerName(),
                    s.getServerBrand(),
                    s.getServerSerialNr(),
                    s.getServerOS(),
                    s.getPatchLevel(),
                    s.getVirtualPlatform(),
                    s.getVirtualVersion(),
                    s.isHighAvailability()
            );
            log.debug("Server indexed in Lucene: id={}", s.getServerID());
        } catch (Exception e) {
            log.error("Lucene indexing for Server {} failed", s.getServerID(), e);
        }
    }

    // ---------- Utils ----------

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String in, String fallback) {
        return in != null ? in : fallback;
    }
}
