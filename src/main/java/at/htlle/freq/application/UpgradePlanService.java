// src/main/java/at/htlle/freq/application/UpgradePlanService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.UpgradePlan;
import at.htlle.freq.domain.UpgradePlanRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Koordiniert Upgrade-Pläne, validiert Pflichtfelder und synchronisiert Lucene.
 */
@Service
public class UpgradePlanService {

    private static final Logger log = LoggerFactory.getLogger(UpgradePlanService.class);

    private final UpgradePlanRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Erstellt den Service mit Repository- und Index-Abhängigkeiten.
     *
     * @param repo   Repository für Upgrade-Pläne
     * @param lucene Lucene-Indexdienst
     */
    public UpgradePlanService(UpgradePlanRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Liefert alle Upgrade-Pläne.
     *
     * @return Liste der Upgrade-Pläne
     */
    public List<UpgradePlan> getAllUpgradePlans() {
        return repo.findAll();
    }

    /**
     * Holt einen Upgrade-Plan anhand seiner ID.
     *
     * @param id Plan-ID
     * @return Optional mit Upgrade-Plan oder leer
     */
    public Optional<UpgradePlan> getUpgradePlanById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Liefert Upgrade-Pläne für eine Site.
     *
     * @param siteId Site-ID
     * @return Liste der Upgrade-Pläne
     */
    public List<UpgradePlan> getUpgradePlansBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    // ---------- Commands ----------

    /**
     * Speichert einen Upgrade-Plan, validiert Pflichtfelder und indexiert nach Commit.
     *
     * @param incoming Upgrade-Plan, der gespeichert werden soll
     * @return gespeicherter Upgrade-Plan
     */
    @Transactional
    public UpgradePlan createOrUpdateUpgradePlan(UpgradePlan incoming) {
        Objects.requireNonNull(incoming, "upgrade plan payload must not be null");

        if (incoming.getSiteID() == null)
            throw new IllegalArgumentException("SiteID is required");
        if (incoming.getSoftwareID() == null)
            throw new IllegalArgumentException("SoftwareID is required");
        if (isBlank(incoming.getStatus()))
            throw new IllegalArgumentException("Status is required");

        UpgradePlan saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("UpgradePlan gespeichert: id={} site={} software={} status='{}'",
                saved.getUpgradePlanID(), saved.getSiteID(), saved.getSoftwareID(), saved.getStatus());
        return saved;
    }

    /**
     * Aktualisiert einen Upgrade-Plan und synchronisiert Lucene.
     *
     * @param id    Plan-ID
     * @param patch Änderungen, die übernommen werden sollen
     * @return Optional mit aktualisiertem Plan oder leer
     */
    @Transactional
    public Optional<UpgradePlan> updateUpgradePlan(UUID id, UpgradePlan patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setSiteID(patch.getSiteID() != null ? patch.getSiteID() : existing.getSiteID());
            existing.setSoftwareID(patch.getSoftwareID() != null ? patch.getSoftwareID() : existing.getSoftwareID());
            existing.setPlannedWindowStart(nvl(patch.getPlannedWindowStart(), existing.getPlannedWindowStart()));
            existing.setPlannedWindowEnd(nvl(patch.getPlannedWindowEnd(), existing.getPlannedWindowEnd()));
            existing.setStatus(nvl(patch.getStatus(), existing.getStatus()));
            existing.setCreatedAt(nvl(patch.getCreatedAt(), existing.getCreatedAt()));
            existing.setCreatedBy(nvl(patch.getCreatedBy(), existing.getCreatedBy()));

            UpgradePlan saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("UpgradePlan aktualisiert: id={} status='{}'", id, saved.getStatus());
            return saved;
        });
    }

    /**
     * Löscht einen Upgrade-Plan.
     *
     * @param id Plan-ID
     */
    @Transactional
    public void deleteUpgradePlan(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(up -> {
            log.info("UpgradePlan gelöscht: id={} site={} software={}",
                    id, up.getSiteID(), up.getSoftwareID());
            // Optional: lucene.deleteUpgradePlan(id.toString());
        });
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(UpgradePlan up) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(up);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexToLucene(up);
            }
        });
    }

    private void indexToLucene(UpgradePlan up) {
        try {
            lucene.indexUpgradePlan(
                    up.getUpgradePlanID() != null ? up.getUpgradePlanID().toString() : null,
                    up.getSiteID() != null ? up.getSiteID().toString() : null,
                    up.getSoftwareID() != null ? up.getSoftwareID().toString() : null,
                    up.getPlannedWindowStart(),
                    up.getPlannedWindowEnd(),
                    up.getStatus(),
                    up.getCreatedAt(),
                    up.getCreatedBy()
            );
            log.debug("UpgradePlan in Lucene indexiert: id={}", up.getUpgradePlanID());
        } catch (Exception e) {
            log.error("Lucene-Indexing für UpgradePlan {} fehlgeschlagen", up.getUpgradePlanID(), e);
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
