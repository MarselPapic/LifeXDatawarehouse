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
    private final ProjectSiteAssignmentService projectSites;

    /**
     * Creates the service with repository and indexing dependencies.
     *
     * @param repo   repository for sites
     * @param lucene Lucene indexing service
     */
    public SiteService(SiteRepository repo, LuceneIndexService lucene, ProjectSiteAssignmentService projectSites) {
        this.repo = repo;
        this.lucene = lucene;
        this.projectSites = projectSites;
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
    public Site createOrUpdateSite(Site incoming, List<UUID> projectIds) {
        Objects.requireNonNull(incoming, "site payload must not be null");

        if (isBlank(incoming.getSiteName()))
            throw new IllegalArgumentException("SiteName is required");
        List<UUID> normalizedProjects = normalizeProjects(projectIds, incoming.getProjectID());
        if (normalizedProjects.isEmpty())
            throw new IllegalArgumentException("At least one ProjectID is required");
        incoming.setProjectID(normalizedProjects.get(0));
        if (incoming.getAddressID() == null)
            throw new IllegalArgumentException("AddressID is required");
        if (incoming.getRedundantServers() == null)
            throw new IllegalArgumentException("RedundantServers is required");
        if (incoming.getRedundantServers() < 0)
            throw new IllegalArgumentException("RedundantServers must not be negative");
        if (incoming.getTenantCount() != null && incoming.getTenantCount() < 0)
            throw new IllegalArgumentException("TenantCount must not be negative");
        if (incoming.getHighAvailability() == null)
            throw new IllegalArgumentException("HighAvailability is required");

        Site saved = repo.save(incoming);
        projectSites.replaceProjectsForSite(saved.getSiteID(), normalizedProjects);
        registerAfterCommitIndexing(saved, normalizedProjects);

        log.info("Site saved: id={} name='{}' projectIDs={}",
                saved.getSiteID(), saved.getSiteName(), normalizedProjects);
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
    public Optional<Site> updateSite(UUID id, Site patch, List<UUID> projectIds) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setSiteName(nvl(patch.getSiteName(), existing.getSiteName()));
            List<UUID> normalizedProjects = resolveUpdatedProjects(id, existing, patch, projectIds);
            existing.setProjectID(normalizedProjects.get(0));
            existing.setAddressID(patch.getAddressID() != null ? patch.getAddressID() : existing.getAddressID());
            existing.setFireZone(nvl(patch.getFireZone(), existing.getFireZone()));
            existing.setTenantCount(patch.getTenantCount() != null ? patch.getTenantCount() : existing.getTenantCount());
            if (patch.getRedundantServers() != null && patch.getRedundantServers() < 0) {
                throw new IllegalArgumentException("RedundantServers must not be negative");
            }
            existing.setRedundantServers(patch.getRedundantServers() != null
                    ? patch.getRedundantServers()
                    : existing.getRedundantServers());
            if (existing.getRedundantServers() == null) {
                throw new IllegalArgumentException("RedundantServers is required");
            }
            if (patch.getTenantCount() != null && patch.getTenantCount() < 0) {
                throw new IllegalArgumentException("TenantCount must not be negative");
            }
            existing.setHighAvailability(patch.getHighAvailability() != null
                    ? patch.getHighAvailability()
                    : existing.getHighAvailability());
            if (existing.getHighAvailability() == null) {
                throw new IllegalArgumentException("HighAvailability is required");
            }

            Site saved = repo.save(existing);
            projectSites.replaceProjectsForSite(saved.getSiteID(), normalizedProjects);
            registerAfterCommitIndexing(saved, normalizedProjects);

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
            projectSites.replaceProjectsForSite(id, List.of());
            repo.deleteById(id);
            log.info("Site deleted: id={} name='{}'", id, s.getSiteName());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    /**
     * Registers the After Commit Indexing for deferred execution.
     * @param s s.
     * @param projectIds project identifiers.
     */
    private void registerAfterCommitIndexing(Site s, List<UUID> projectIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(s, projectIds);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * Indexes the site after the transaction commits.
             */
            @Override
            public void afterCommit() {
                indexToLucene(s, projectIds);
            }
        });
    }

    /**
     * Indexes a site in Lucene for search operations.
     *
     * @param s site entity to index.
     * @param projectIds project identifiers associated with the site.
     */
    private void indexToLucene(Site s, List<UUID> projectIds) {
        try {
            lucene.indexSite(
                    s.getSiteID() != null ? s.getSiteID().toString() : null,
                    projectIds == null ? List.of() : projectIds.stream()
                            .filter(Objects::nonNull)
                            .map(UUID::toString)
                            .toList(),
                    s.getAddressID() != null ? s.getAddressID().toString() : null,
                    s.getSiteName(),
                    s.getFireZone(),
                    s.getTenantCount(),
                    s.getRedundantServers(),
                    s.isHighAvailability()
            );
            log.debug("Site indexed in Lucene: id={}", s.getSiteID());
        } catch (Exception e) {
            log.error("Lucene indexing for Site {} failed", s.getSiteID(), e);
        }
    }

    // ---------- Utils ----------

    /**
     * Checks whether a string is null or blank.
     *
     * @param s input string.
     * @return true when the string is null, empty, or whitespace.
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Returns the fallback when the input is null.
     *
     * @param in input value.
     * @param fallback fallback value.
     * @return input when non-null, otherwise fallback.
     */
    private static String nvl(String in, String fallback) {
        return in != null ? in : fallback;
    }

    /**
     * Normalizes project identifiers into a unique list.
     *
     * @param projects list of project IDs.
     * @param singleProject optional single project ID.
     * @return distinct project IDs in request order.
     */
    private List<UUID> normalizeProjects(List<UUID> projects, UUID singleProject) {
        List<UUID> list = new ArrayList<>();
        if (singleProject != null) list.add(singleProject);
        if (projects != null) list.addAll(projects);
        return list.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Resolves the updated project assignments for a site.
     *
     * @param siteId site identifier.
     * @param existing current site state.
     * @param patch incoming update payload.
     * @param projectIds project IDs supplied directly.
     * @return resolved project IDs to persist.
     */
    private List<UUID> resolveUpdatedProjects(UUID siteId, Site existing, Site patch, List<UUID> projectIds) {
        List<UUID> incoming = normalizeProjects(projectIds, patch.getProjectID());
        if (!incoming.isEmpty()) {
            return incoming;
        }

        List<UUID> currentAssignments = projectSites.getProjectsForSite(siteId);
        if (!currentAssignments.isEmpty()) {
            return currentAssignments;
        }

        if (existing.getProjectID() != null) {
            return List.of(existing.getProjectID());
        }

        throw new IllegalArgumentException("At least one ProjectID is required");
    }
}
