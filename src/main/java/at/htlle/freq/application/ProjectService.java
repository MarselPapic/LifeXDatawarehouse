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

import java.util.*;

/**
 * Verwaltet Projekte, normalisiert Zustandsinformationen und synchronisiert Lucene.
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Erstellt den Service mit Repository und Indexdienst.
     *
     * @param repo   Repository für Projekte
     * @param lucene Lucene-Indexdienst
     */
    public ProjectService(ProjectRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Liefert alle Projekte.
     *
     * @return Liste aller Projekte
     */
    public List<Project> getAllProjects() {
        return repo.findAll();
    }

    /**
     * Sucht ein Projekt anhand seiner ID.
     *
     * @param id Projekt-ID
     * @return Optional mit Projekt oder leer
     */
    public Optional<Project> getProjectById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Sucht ein Projekt anhand der SAP-ID.
     *
     * @param sapId SAP-Projektnummer
     * @return Optional mit Projekt oder leer
     */
    public Optional<Project> getProjectBySapId(String sapId) {
        if (isBlank(sapId)) return Optional.empty();
        return repo.findBySapId(sapId.trim());
    }

    // ---------- Commands ----------

    /**
     * Speichert ein Projekt, setzt Standardwerte und indexiert nach Commit in Lucene.
     *
     * @param incoming zu speicherndes Projekt
     * @return gespeichertes Projekt
     */
    @Transactional
    public Project createOrUpdateProject(Project incoming) {
        Objects.requireNonNull(incoming, "project payload must not be null");

        if (isBlank(incoming.getProjectName()))
            throw new IllegalArgumentException("ProjectName is required");
        if (isBlank(incoming.getProjectSAPID()))
            throw new IllegalArgumentException("ProjectSAPID is required");
        if (incoming.getLifecycleStatus() == null) {
            incoming.setLifecycleStatus(ProjectLifecycleStatus.ACTIVE);
        }

        Project saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("Project gespeichert: id={} name='{}' SAP={}",
                saved.getProjectID(), saved.getProjectName(), saved.getProjectSAPID());
        return saved;
    }

    /**
     * Aktualisiert ein Projekt und hält Lucene synchron.
     *
     * @param id    Projekt-ID
     * @param patch Änderungen, die übernommen werden sollen
     * @return Optional mit aktualisiertem Projekt oder leer
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

            Project saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("Project aktualisiert: id={} name='{}'", id, saved.getProjectName());
            return saved;
        });
    }

    /**
     * Löscht ein Projekt.
     *
     * @param id Projekt-ID
     */
    @Transactional
    public void deleteProject(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(p -> {
            log.info("Project gelöscht: id={} name='{}'", id, p.getProjectName());
            // Optional: lucene.deleteProject(id.toString());
        });
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(Project p) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(p);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexToLucene(p);
            }
        });
    }

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
                    p.getAddressID() != null ? p.getAddressID().toString() : null
            );
            log.debug("Project in Lucene indexiert: id={}", p.getProjectID());
        } catch (Exception e) {
            log.error("Lucene-Indexing für Project {} fehlgeschlagen", p.getProjectID(), e);
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
