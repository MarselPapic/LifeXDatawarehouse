package at.htlle.freq.application;

import at.htlle.freq.domain.ProjectSiteAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service component that manages Project Site Assignment operations.
 */
@Service
public class ProjectSiteAssignmentService {

    private final ProjectSiteAssignmentRepository repository;

    /**
     * Creates a service backed by a {@link ProjectSiteAssignmentRepository}.
     *
     * @param repository repository used for assignment persistence.
     */
    public ProjectSiteAssignmentService(ProjectSiteAssignmentRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns project identifiers assigned to a site.
     *
     * @param siteId site identifier.
     * @return list of project IDs linked to the site.
     */
    public List<UUID> getProjectsForSite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repository.findProjectIdsBySite(siteId);
    }

    /**
     * Returns site identifiers assigned to a project.
     *
     * @param projectId project identifier.
     * @return list of site IDs linked to the project.
     */
    public List<UUID> getSitesForProject(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        return repository.findSiteIdsByProject(projectId);
    }

    /**
     * Replaces all project assignments for a site.
     *
     * @param siteId site identifier.
     * @param projectIds project identifiers.
     */
    @Transactional
    public void replaceProjectsForSite(UUID siteId, Collection<UUID> projectIds) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        repository.replaceProjectsForSite(siteId, normalize(projectIds));
    }

    /**
     * Replaces all site assignments for a project.
     *
     * @param projectId project identifier.
     * @param siteIds site identifiers.
     */
    @Transactional
    public void replaceSitesForProject(UUID projectId, Collection<UUID> siteIds) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        repository.replaceSitesForProject(projectId, normalize(siteIds));
    }

    /**
     * Normalizes identifier collections to a distinct list.
     *
     * @param ids identifiers to normalize.
     * @return distinct list of identifiers.
     */
    private List<UUID> normalize(Collection<UUID> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
