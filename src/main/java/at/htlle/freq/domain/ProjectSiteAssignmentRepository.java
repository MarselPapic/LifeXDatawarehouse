package at.htlle.freq.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing links between {@link Project} and {@link Site} via the ProjectSite table.
 */
public interface ProjectSiteAssignmentRepository {
    List<UUID> findProjectIdsBySite(UUID siteId);

    List<UUID> findSiteIdsByProject(UUID projectId);

    void replaceProjectsForSite(UUID siteId, Collection<UUID> projectIds);

    void replaceSitesForProject(UUID projectId, Collection<UUID> siteIds);
}
