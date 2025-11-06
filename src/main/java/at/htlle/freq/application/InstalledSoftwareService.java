// src/main/java/at/htlle/freq/application/InstalledSoftwareService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.InstalledSoftwareRepository;
import at.htlle.freq.domain.InstalledSoftwareStatus;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Manages installed software states, validates status values, and keeps the Lucene index up to date.
 */
@Service
public class InstalledSoftwareService {

    private static final Logger log = LoggerFactory.getLogger(InstalledSoftwareService.class);

    private final InstalledSoftwareRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index dependencies.
     *
     * @param repo   repository for installations
     * @param lucene Lucene indexing service
     */
    public InstalledSoftwareService(InstalledSoftwareRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all installed software states.
     *
     * @return list of all installations
     */
    public List<InstalledSoftware> getAllInstalledSoftware() {
        return repo.findAll();
    }

    /**
     * Retrieves an installation by its identifier.
     *
     * @param id installation identifier
     * @return optional containing the installation or empty otherwise
     */
    public Optional<InstalledSoftware> getInstalledSoftwareById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns installations for a site.
     *
     * @param siteId site identifier
     * @return list of installations
     */
    public List<InstalledSoftware> getInstalledSoftwareBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    /**
     * Returns installations for a software record.
     *
     * @param softwareId software identifier
     * @return list of installations
     */
    public List<InstalledSoftware> getInstalledSoftwareBySoftware(UUID softwareId) {
        Objects.requireNonNull(softwareId, "softwareId must not be null");
        return repo.findBySoftware(softwareId);
    }

    // ---------- Commands ----------

    /**
     * Saves an installation, normalizes the status, and indexes it in Lucene after the commit.
     *
     * @param incoming installation to persist
     * @return stored installation
     */
    @Transactional
    public InstalledSoftware createOrUpdateInstalledSoftware(InstalledSoftware incoming) {
        Objects.requireNonNull(incoming, "installedSoftware payload must not be null");

        if (incoming.getSiteID() == null)
            throw new IllegalArgumentException("SiteID is required");
        if (incoming.getSoftwareID() == null)
            throw new IllegalArgumentException("SoftwareID is required");

        incoming.setStatus(normalizeStatus(incoming.getStatus()));

        InstalledSoftware saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("InstalledSoftware saved: id={} site={} software={} status={}",
                saved.getInstalledSoftwareID(), saved.getSiteID(), saved.getSoftwareID(), saved.getStatus());
        return saved;
    }

    /**
     * Updates an installation and keeps the index synchronized.
     *
     * @param id    installation identifier
     * @param patch changes to apply to the installation
     * @return optional containing the updated installation or empty otherwise
     */
    @Transactional
    public Optional<InstalledSoftware> updateInstalledSoftware(UUID id, InstalledSoftware patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setSiteID(patch.getSiteID() != null ? patch.getSiteID() : existing.getSiteID());
            existing.setSoftwareID(patch.getSoftwareID() != null ? patch.getSoftwareID() : existing.getSoftwareID());
            if (patch.getStatus() != null) {
                existing.setStatus(normalizeStatus(patch.getStatus()));
            } else if (existing.getStatus() == null) {
                existing.setStatus(normalizeStatus(null));
            }

            InstalledSoftware saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("InstalledSoftware updated: id={} site={} software={} status={}",
                    id, saved.getSiteID(), saved.getSoftwareID(), saved.getStatus());
            return saved;
        });
    }

    /**
     * Deletes an installation.
     *
     * @param id installation identifier
     */
    @Transactional
    public void deleteInstalledSoftware(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(isw -> {
            log.info("InstalledSoftware deleted: id={} site={} software={} status={}",
                    id, isw.getSiteID(), isw.getSoftwareID(), isw.getStatus());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(InstalledSoftware isw) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(isw);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexToLucene(isw);
            }
        });
    }

    private void indexToLucene(InstalledSoftware isw) {
        try {
            lucene.indexInstalledSoftware(
                    isw.getInstalledSoftwareID() != null ? isw.getInstalledSoftwareID().toString() : null,
                    isw.getSiteID() != null ? isw.getSiteID().toString() : null,
                    isw.getSoftwareID() != null ? isw.getSoftwareID().toString() : null,
                    isw.getStatus()
            );
            log.debug("InstalledSoftware indexed in Lucene: id={}", isw.getInstalledSoftwareID());
        } catch (Exception e) {
            log.error("Lucene indexing for InstalledSoftware {} failed", isw.getInstalledSoftwareID(), e);
        }
    }

    private String normalizeStatus(String status) {
        try {
            return InstalledSoftwareStatus.from(status).dbValue();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
}
