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
 * Coordinates upgrade plans, validates required fields, and synchronizes Lucene.
 */
@Service
public class UpgradePlanService {

    private static final Logger log = LoggerFactory.getLogger(UpgradePlanService.class);

    private final UpgradePlanRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index dependencies.
     *
     * @param repo   repository for upgrade plans
     * @param lucene Lucene indexing service
     */
    public UpgradePlanService(UpgradePlanRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all upgrade plans.
     *
     * @return list of upgrade plans
     */
    public List<UpgradePlan> getAllUpgradePlans() {
        return repo.findAll();
    }

    /**
     * Retrieves an upgrade plan by its identifier.
     *
     * @param id plan identifier
     * @return optional containing the upgrade plan or empty otherwise
     */
    public Optional<UpgradePlan> getUpgradePlanById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns upgrade plans for a site.
     *
     * @param siteId site identifier
     * @return list of upgrade plans
     */
    public List<UpgradePlan> getUpgradePlansBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    // ---------- Commands ----------

    /**
     * Saves an upgrade plan, validates required fields, and indexes it after the commit.
     *
     * @param incoming upgrade plan to persist
     * @return stored upgrade plan
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

        log.info("UpgradePlan saved: id={} site={} software={} status='{}'",
                saved.getUpgradePlanID(), saved.getSiteID(), saved.getSoftwareID(), saved.getStatus());
        return saved;
    }

    /**
     * Updates an upgrade plan and synchronizes Lucene.
     *
     * @param id    plan identifier
     * @param patch changes to merge into the entity
     * @return optional containing the updated plan or empty otherwise
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

            log.info("UpgradePlan updated: id={} status='{}'", id, saved.getStatus());
            return saved;
        });
    }

    /**
     * Deletes an upgrade plan.
     *
     * @param id plan identifier
     */
    @Transactional
    public void deleteUpgradePlan(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(up -> {
            log.info("UpgradePlan deleted: id={} site={} software={}",
                    id, up.getSiteID(), up.getSoftwareID());
            // Optionally remove the entry from Lucene once delete support exists.
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
            log.debug("UpgradePlan indexed in Lucene: id={}", up.getUpgradePlanID());
        } catch (Exception e) {
            log.error("Lucene indexing for UpgradePlan {} failed", up.getUpgradePlanID(), e);
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
