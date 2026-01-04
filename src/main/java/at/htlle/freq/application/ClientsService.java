package at.htlle.freq.application;

import at.htlle.freq.domain.Clients;
import at.htlle.freq.domain.ClientsRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Service layer for clients (working positions).
 * Handles database operations, validation, and Lucene indexing.
 */
@Service
public class ClientsService {

    private static final Logger log = LoggerFactory.getLogger(ClientsService.class);

    private final ClientsRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and Lucene components.
     *
     * @param repo   repository for clients
     * @param lucene Lucene indexing service
     */
    public ClientsService(ClientsRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ----------------------------
    // READ
    // ----------------------------

    /**
     * Returns all clients of a site.
     *
     * @param siteId technical site identifier
     * @return list of the site's clients
     */
    public List<Clients> findBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    /**
     * Returns every client without filtering.
     *
     * @return complete client list
     */
    public List<Clients> findAll() {
        return repo.findAll();
    }

    /**
     * Looks up a client by its identifier.
     *
     * @param id client identifier
     * @return optional containing the client or empty when missing
     */
    public Optional<Clients> findById(UUID id) {
        return repo.findById(id);
    }

    // ----------------------------
    // CREATE
    // ----------------------------

    /**
     * Persists a new client and indexes it in Lucene after the commit.
     * Validates required fields such as site, name, and installation type.
     *
     * @param in new client
     * @return stored client including the generated ID
     */
    @Transactional
    public Clients create(Clients in) {
        Objects.requireNonNull(in, "client payload must not be null");
        if (in.getSiteID() == null) throw new IllegalArgumentException("siteID is required");
        if (isBlank(in.getClientName())) throw new IllegalArgumentException("clientName is required");
        if (isBlank(in.getInstallType())) throw new IllegalArgumentException("installType is required (LOCAL/BROWSER)");

        // Persist the entity; JdbcClientsRepository assigns the UUID via RETURNING.
        repo.save(in);

        // Index the record in Lucene after the commit.
        registerAfterCommitIndexing(in);

        log.info("Client saved: id={} name='{}'", in.getClientID(), in.getClientName());
        return in;
    }

    // ----------------------------
    // UPDATE
    // ----------------------------

    /**
     * Updates an existing client and synchronizes the index.
     *
     * @param id    client identifier
     * @param patch values to apply
     * @return updated client
     */
    @Transactional
    public Clients update(UUID id, Clients patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        var existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("client not found"));

        existing.setSiteID(nvl(patch.getSiteID(), existing.getSiteID()));
        existing.setClientName(nvl(patch.getClientName(), existing.getClientName()));
        existing.setClientBrand(nvl(patch.getClientBrand(), existing.getClientBrand()));
        existing.setClientSerialNr(nvl(patch.getClientSerialNr(), existing.getClientSerialNr()));
        existing.setClientOS(nvl(patch.getClientOS(), existing.getClientOS()));
        existing.setPatchLevel(nvl(patch.getPatchLevel(), existing.getPatchLevel()));
        existing.setInstallType(nvl(patch.getInstallType(), existing.getInstallType()));
        existing.setWorkingPositionType(nvl(patch.getWorkingPositionType(), existing.getWorkingPositionType()));
        existing.setOtherInstalledSoftware(nvl(patch.getOtherInstalledSoftware(), existing.getOtherInstalledSoftware()));

        repo.save(existing);
        registerAfterCommitIndexing(existing);

        log.info("Client updated: id={} name='{}'", id, existing.getClientName());
        return existing;
    }

// ----------------------------
// DELETE
// Deletion support would be useful, but the Lucene integration cannot remove entries yet,
// therefore the controller does not expose a delete endpoint at this time.
// ----------------------------
    // ----------------------------
    // Lucene Indexing Helpers
    // ----------------------------

    /**
     * Registers the After Commit Indexing for deferred execution.
     * @param c c.
     */
    private void registerAfterCommitIndexing(Clients c) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(c);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { indexToLucene(c); }
        });
    }

    /**
     * Indexes the To Lucene for search operations.
     * @param c c.
     */
    private void indexToLucene(Clients c) {
        try {
            lucene.indexClient(
                    c.getClientID() != null ? c.getClientID().toString() : null,
                    c.getSiteID() != null ? c.getSiteID().toString() : null,
                    c.getClientName(),
                    c.getClientBrand(),
                    c.getClientOS(),
                    c.getInstallType(),
                    c.getWorkingPositionType(),
                    c.getOtherInstalledSoftware()
            );
            log.debug("Client indexed in Lucene: id={}", c.getClientID());
        } catch (Exception e) {
            // Indexing failures must not trigger a database rollback.
            log.error("Lucene indexing for Client {} failed", c.getClientID(), e);
        }
    }

    // ----------------------------
    // Utility
    // ----------------------------

    /**
     * Checks whether a string is null or blank.
     *
     * @param s input string.
     * @return true when the string is null, empty, or whitespace.
     */
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /**
     * Returns the fallback when the input is null.
     *
     * @param in input value.
     * @param fallback fallback value.
     * @return input when non-null, otherwise fallback.
     */
    private static <T> T nvl(T in, T fallback) { return in != null ? in : fallback; }
}
