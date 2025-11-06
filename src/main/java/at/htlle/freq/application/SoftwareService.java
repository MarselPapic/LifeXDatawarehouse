// src/main/java/at/htlle/freq/application/SoftwareService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.Software;
import at.htlle.freq.domain.SoftwareRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Manages software master data, validates required fields, and synchronizes Lucene.
 */
@Service
public class SoftwareService {

    private static final Logger log = LoggerFactory.getLogger(SoftwareService.class);

    private final SoftwareRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and indexing dependencies.
     *
     * @param repo   repository for software
     * @param lucene Lucene indexing service
     */
    public SoftwareService(SoftwareRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all software entries.
     *
     * @return list of software records
     */
    public List<Software> getAllSoftware() {
        return repo.findAll();
    }

    /**
     * Retrieves software by its identifier.
     *
     * @param id software identifier
     * @return optional containing the software or empty otherwise
     */
    public Optional<Software> getSoftwareById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns software entries by name.
     *
     * @param name search term
     * @return list of matching software entries
     */
    public List<Software> getSoftwareByName(String name) {
        if (isBlank(name)) return List.of();
        return repo.findByName(name.trim());
    }

    // ---------- Commands ----------

    /**
     * Saves software, validates required fields, and indexes the record in Lucene after the commit.
     *
     * @param incoming software entity to persist
     * @return stored software
     */
    @Transactional
    public Software createOrUpdateSoftware(Software incoming) {
        Objects.requireNonNull(incoming, "software payload must not be null");

        if (isBlank(incoming.getName()))
            throw new IllegalArgumentException("Name is required");
        if (isBlank(incoming.getRelease()))
            throw new IllegalArgumentException("Release is required");

        if (incoming.getThirdParty() == null) {
            incoming.setThirdParty(false);
        }

        Software saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("Software saved: id={} name='{}' release='{}'",
                saved.getSoftwareID(), saved.getName(), saved.getRelease());
        return saved;
    }

    /**
     * Updates software data and synchronizes Lucene.
     *
     * @param id    software identifier
     * @param patch changes to merge into the entity
     * @return optional containing the updated software or empty otherwise
     */
    @Transactional
    public Optional<Software> updateSoftware(UUID id, Software patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setName(nvl(patch.getName(), existing.getName()));
            existing.setRelease(nvl(patch.getRelease(), existing.getRelease()));
            existing.setRevision(nvl(patch.getRevision(), existing.getRevision()));
            existing.setSupportPhase(nvl(patch.getSupportPhase(), existing.getSupportPhase()));
            existing.setLicenseModel(nvl(patch.getLicenseModel(), existing.getLicenseModel()));
            if (patch.getThirdParty() != null) {
                existing.setThirdParty(patch.getThirdParty());
            }
            existing.setEndOfSalesDate(nvl(patch.getEndOfSalesDate(), existing.getEndOfSalesDate()));
            existing.setSupportStartDate(nvl(patch.getSupportStartDate(), existing.getSupportStartDate()));
            existing.setSupportEndDate(nvl(patch.getSupportEndDate(), existing.getSupportEndDate()));

            Software saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("Software updated: id={} name='{}'", id, saved.getName());
            return saved;
        });
    }

    /**
     * Deletes a software record.
     *
     * @param id software identifier
     */
    @Transactional
    public void deleteSoftware(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(sw -> {
            log.info("Software deleted: id={} name='{}' release='{}'",
                    id, sw.getName(), sw.getRelease());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(Software sw) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(sw);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexToLucene(sw);
            }
        });
    }

    private void indexToLucene(Software sw) {
        try {
            lucene.indexSoftware(
                    sw.getSoftwareID() != null ? sw.getSoftwareID().toString() : null,
                    sw.getName(),
                    sw.getRelease(),
                    sw.getRevision(),
                    sw.getSupportPhase(),
                    sw.getLicenseModel(),
                    sw.isThirdParty(),
                    sw.getEndOfSalesDate(),
                    sw.getSupportStartDate(),
                    sw.getSupportEndDate()
            );
            log.debug("Software indexed in Lucene: id={}", sw.getSoftwareID());
        } catch (Exception e) {
            log.error("Lucene indexing for Software {} failed", sw.getSoftwareID(), e);
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
