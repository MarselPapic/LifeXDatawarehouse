// src/main/java/at/htlle/freq/application/ProjectService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.Project;
import at.htlle.freq.domain.ProjectLifecycleStatus;
import at.htlle.freq.domain.ProjectRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.*;

/**
 * Manages projects, normalizes lifecycle information, and keeps Lucene synchronized.
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and indexing dependencies.
     *
     * @param repo   repository for projects
     * @param lucene Lucene indexing service
     */
    public ProjectService(ProjectRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all projects.
     *
     * @return list of every project
     */
    public List<Project> getAllProjects() {
        return repo.findAll();
    }

    /**
     * Retrieves a project by its identifier.
     *
     * @param id project identifier
     * @return optional containing the project or empty otherwise
     */
    public Optional<Project> getProjectById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Searches for a project by its SAP identifier.
     *
     * @param sapId SAP project number
     * @return optional containing the project or empty otherwise
     */
    public Optional<Project> getProjectBySapId(String sapId) {
        if (isBlank(sapId)) return Optional.empty();
        return repo.findBySapId(sapId.trim());
    }

    // ---------- Commands ----------

    /**
     * Saves a project, applies defaults, and indexes it in Lucene after the commit.
     *
     * @param incoming project to persist
     * @return stored project
     */
    @Transactional
    public Project createOrUpdateProject(Project incoming) {
        Objects.requireNonNull(incoming, "project payload must not be null");

        trimIdentifiers(incoming);

        if (isBlank(incoming.getProjectName()))
            throw new IllegalArgumentException("ProjectName is required");
        if (isBlank(incoming.getProjectSAPID()))
            throw new IllegalArgumentException("ProjectSAPID is required");
        if (incoming.getLifecycleStatus() == null) incoming.setLifecycleStatus(ProjectLifecycleStatus.ACTIVE);
        if (isBlank(incoming.getCreateDateTime())) incoming.setCreateDateTime(LocalDate.now().toString());

        validateRequiredIdentifiers(incoming);
        ensureUniqueSapId(incoming);

        Project saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("Project saved: id={} name='{}' SAP={}",
                saved.getProjectID(), saved.getProjectName(), saved.getProjectSAPID());
        return saved;
    }

    /**
     * Updates a project and keeps Lucene synchronized.
     *
     * @param id    project identifier
     * @param patch changes to merge into the entity
     * @return optional containing the updated project or empty otherwise
     */
    @Transactional
    public Optional<Project> updateProject(UUID id, Project patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setProjectSAPID(nvl(patch.getProjectSAPID(), existing.getProjectSAPID()));
            existing.setProjectName(nvl(patch.getProjectName(), existing.getProjectName()));
            existing.setDeploymentVariantID(
                    patch.getDeploymentVariantID() != null ? patch.getDeploymentVariantID() : existing.getDeploymentVariantID());
            existing.setBundleType(nvl(patch.getBundleType(), existing.getBundleType()));
            existing.setCreateDateTime(nvl(patch.getCreateDateTime(), existing.getCreateDateTime()));
            existing.setLifecycleStatus(patch.getLifecycleStatus() != null ? patch.getLifecycleStatus() : existing.getLifecycleStatus());
            existing.setAccountID(patch.getAccountID() != null ? patch.getAccountID() : existing.getAccountID());
            existing.setAddressID(patch.getAddressID() != null ? patch.getAddressID() : existing.getAddressID());
            existing.setSpecialNotes(nvl(trimToNull(patch.getSpecialNotes()), existing.getSpecialNotes()));

            trimIdentifiers(existing);
            validateRequiredIdentifiers(existing);
            ensureUniqueSapId(existing);

            Project saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("Project updated: id={} name='{}'", id, saved.getProjectName());
            return saved;
        });
    }

    /**
     * Deletes a project.
     *
     * @param id project identifier
     */
    @Transactional
    public void deleteProject(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(p -> {
            repo.deleteById(id);
            log.info("Project deleted: id={} name='{}'", id, p.getProjectName());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    /**
     * Registers the After Commit Indexing for deferred execution.
     * @param p p.
     */
    private void registerAfterCommitIndexing(Project p) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(p);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * Executes the after Commit operation.
             */
            @Override
            public void afterCommit() {
                indexToLucene(p);
            }
        });
    }

    /**
     * Validates the Required Identifiers against the required constraints.
     * @param project project.
     */
    private void validateRequiredIdentifiers(Project project) {
        if (project.getDeploymentVariantID() == null) {
            throw new IllegalArgumentException("DeploymentVariantID is required");
        }
        if (project.getAccountID() == null) {
            throw new IllegalArgumentException("AccountID is required");
        }
        if (project.getAddressID() == null) {
            throw new IllegalArgumentException("AddressID is required");
        }
    }

    /**
     * Ensures the SAP ID is unique across projects.
     *
     * @param project project to validate.
     */
    private void ensureUniqueSapId(Project project) {
        repo.findBySapId(project.getProjectSAPID()).ifPresent(existing -> {
            if (project.getProjectID() == null || !project.getProjectID().equals(existing.getProjectID())) {
                throw new IllegalArgumentException("ProjectSAPID already exists: " + project.getProjectSAPID());
            }
        });
    }

    /**
     * Trims string identifiers and normalizes notes.
     *
     * @param project project to normalize.
     */
    private void trimIdentifiers(Project project) {
        if (project.getProjectSAPID() != null) {
            project.setProjectSAPID(project.getProjectSAPID().trim());
        }
        if (project.getProjectName() != null) {
            project.setProjectName(project.getProjectName().trim());
        }
        if (project.getBundleType() != null) {
            project.setBundleType(project.getBundleType().trim());
        }
        if (project.getCreateDateTime() != null) {
            project.setCreateDateTime(project.getCreateDateTime().trim());
        }
        project.setSpecialNotes(trimToNull(project.getSpecialNotes()));
    }

    /**
     * Indexes a project in Lucene for search operations.
     *
     * @param p project entity to index.
     */
    private void indexToLucene(Project p) {
        try {
            lucene.indexProject(
                    p.getProjectID() != null ? p.getProjectID().toString() : null,
                    p.getProjectSAPID(),
                    p.getProjectName(),
                    p.getDeploymentVariantID() != null ? p.getDeploymentVariantID().toString() : null,
                    p.getBundleType(),
                    p.getLifecycleStatus() != null ? p.getLifecycleStatus().name() : null,
                    p.getAccountID() != null ? p.getAccountID().toString() : null,
                    p.getAddressID() != null ? p.getAddressID().toString() : null,
                    p.getSpecialNotes()
            );
            log.debug("Project indexed in Lucene: id={}", p.getProjectID());
        } catch (Exception e) {
            log.error("Lucene indexing for Project {} failed", p.getProjectID(), e);
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
     * Trims a string and converts blank values to null.
     *
     * @param value input string.
     * @return trimmed string or null when blank.
     */
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
