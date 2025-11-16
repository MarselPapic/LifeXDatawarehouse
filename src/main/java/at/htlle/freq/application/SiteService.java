// src/main/java/at/htlle/freq/application/SiteService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.Site;
import at.htlle.freq.domain.SiteRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Manages sites, validates required fields, and synchronizes Lucene.
 */
@Service
public class SiteService {

    private static final Logger log = LoggerFactory.getLogger(SiteService.class);

    private final SiteRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and indexing dependencies.
     *
     * @param repo   repository for sites
     * @param lucene Lucene indexing service
     */
    public SiteService(SiteRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all sites.
     *
     * @return list of sites
     */
    public List<Site> getAllSites() {
        return repo.findAll();
    }

    /**
     * Retrieves a site by its identifier.
     *
     * @param id site identifier
     * @return optional containing the site or empty otherwise
     */
    public Optional<Site> getSiteById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns sites of a project.
     *
     * @param projectId project identifier
     * @return list of sites
     */
    public List<Site> getSitesByProject(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        return repo.findByProject(projectId);
    }

    // ---------- Commands ----------

    /**
     * Saves a site, validates required fields, and indexes it after the commit.
     *
     * @param incoming site to persist
     * @return stored site
     */
    @Transactional
    public Site createOrUpdateSite(Site incoming) {
        Objects.requireNonNull(incoming, "site payload must not be null");

        if (isBlank(incoming.getSiteName()))
            throw new IllegalArgumentException("SiteName is required");
        if (incoming.getProjectID() == null)
            throw new IllegalArgumentException("ProjectID is required");
        if (incoming.getAddressID() == null)
            throw new IllegalArgumentException("AddressID is required");

        Site saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("Site saved: id={} name='{}' projectID={}",
                saved.getSiteID(), saved.getSiteName(), saved.getProjectID());
        return saved;
    }

    /**
     * Updates a site and synchronizes Lucene.
     *
     * @param id    site identifier
     * @param patch changes to merge into the entity
     * @return optional containing the updated site or empty otherwise
     */
    @Transactional
    public Optional<Site> updateSite(UUID id, Site patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setSiteName(nvl(patch.getSiteName(), existing.getSiteName()));
            existing.setProjectID(patch.getProjectID() != null ? patch.getProjectID() : existing.getProjectID());
            existing.setAddressID(patch.getAddressID() != null ? patch.getAddressID() : existing.getAddressID());
            existing.setFireZone(nvl(patch.getFireZone(), existing.getFireZone()));
            existing.setTenantCount(patch.getTenantCount() != null ? patch.getTenantCount() : existing.getTenantCount());

            Site saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("Site updated: id={} name='{}'", id, saved.getSiteName());
            return saved;
        });
    }

    /**
     * Deletes a site.
     *
     * @param id site identifier
     */
    @Transactional
    public void deleteSite(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(s -> {
            repo.deleteById(id);
            log.info("Site deleted: id={} name='{}'", id, s.getSiteName());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(Site s) {
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

    private void indexToLucene(Site s) {
        try {
            lucene.indexSite(
                    s.getSiteID() != null ? s.getSiteID().toString() : null,
                    s.getProjectID() != null ? s.getProjectID().toString() : null,
                    s.getAddressID() != null ? s.getAddressID().toString() : null,
                    s.getSiteName(),
                    s.getFireZone(),
                    s.getTenantCount()
            );
            log.debug("Site indexed in Lucene: id={}", s.getSiteID());
        } catch (Exception e) {
            log.error("Lucene indexing for Site {} failed", s.getSiteID(), e);
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
