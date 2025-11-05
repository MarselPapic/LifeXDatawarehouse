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
 * Verwaltet installierte Softwarestände, prüft Statuswerte und hält den Lucene-Index aktuell.
 */
@Service
public class InstalledSoftwareService {

    private static final Logger log = LoggerFactory.getLogger(InstalledSoftwareService.class);

    private final InstalledSoftwareRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Erstellt den Service mit Repository- und Index-Abhängigkeiten.
     *
     * @param repo   Repository für Installationen
     * @param lucene Lucene-Indexdienst
     */
    public InstalledSoftwareService(InstalledSoftwareRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Liefert alle installierten Softwarestände.
     *
     * @return Liste aller Installationen
     */
    public List<InstalledSoftware> getAllInstalledSoftware() {
        return repo.findAll();
    }

    /**
     * Sucht eine Installation anhand ihrer ID.
     *
     * @param id Installation-ID
     * @return Optional mit Installation oder leer
     */
    public Optional<InstalledSoftware> getInstalledSoftwareById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Liefert Installationen für eine Site.
     *
     * @param siteId Site-ID
     * @return Liste der Installationen
     */
    public List<InstalledSoftware> getInstalledSoftwareBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    /**
     * Liefert Installationen zu einer Software.
     *
     * @param softwareId Software-ID
     * @return Liste der Installationen
     */
    public List<InstalledSoftware> getInstalledSoftwareBySoftware(UUID softwareId) {
        Objects.requireNonNull(softwareId, "softwareId must not be null");
        return repo.findBySoftware(softwareId);
    }

    // ---------- Commands ----------

    /**
     * Speichert eine Installation und normalisiert den Status. Indexiert nach Commit in Lucene.
     *
     * @param incoming Installation, die gespeichert werden soll
     * @return gespeicherte Installation
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

        log.info("InstalledSoftware gespeichert: id={} site={} software={} status={}",
                saved.getInstalledSoftwareID(), saved.getSiteID(), saved.getSoftwareID(), saved.getStatus());
        return saved;
    }

    /**
     * Aktualisiert eine Installation und hält den Index synchron.
     *
     * @param id    Installation-ID
     * @param patch Änderungen für die Installation
     * @return Optional mit aktualisierter Installation oder leer
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

            log.info("InstalledSoftware aktualisiert: id={} site={} software={} status={}",
                    id, saved.getSiteID(), saved.getSoftwareID(), saved.getStatus());
            return saved;
        });
    }

    /**
     * Löscht eine Installation.
     *
     * @param id Installation-ID
     */
    @Transactional
    public void deleteInstalledSoftware(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(isw -> {
            log.info("InstalledSoftware gelöscht: id={} site={} software={} status={}",
                    id, isw.getSiteID(), isw.getSoftwareID(), isw.getStatus());
            // Optional: lucene.deleteInstalledSoftware(id.toString());
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
            log.debug("InstalledSoftware in Lucene indexiert: id={}", isw.getInstalledSoftwareID());
        } catch (Exception e) {
            log.error("Lucene-Indexing für InstalledSoftware {} fehlgeschlagen", isw.getInstalledSoftwareID(), e);
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
