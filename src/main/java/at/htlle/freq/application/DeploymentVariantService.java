// src/main/java/at/htlle/freq/application/DeploymentVariantService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.DeploymentVariant;
import at.htlle.freq.domain.DeploymentVariantRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Manages deployment variants and orchestrates synchronization with the Lucene index.
 */
@Service
public class DeploymentVariantService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentVariantService.class);

    private final DeploymentVariantRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index dependencies.
     *
     * @param repo   repository for deployment variants
     * @param lucene Lucene index service
     */
    public DeploymentVariantService(DeploymentVariantRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns every deployment variant.
     *
     * @return list of all variants
     */
    public List<DeploymentVariant> getAllVariants() {
        return repo.findAll();
    }

    /**
     * Retrieves a variant by its identifier.
     *
     * @param id variant identifier
     * @return optional containing the variant or empty otherwise
     */
    public Optional<DeploymentVariant> getVariantById(UUID id) {
        Objects.requireNonNull(id, "variant id must not be null");
        return repo.findById(id);
    }

    /**
     * Searches for a variant by its code.
     *
     * @param code unique code
     * @return optional containing the variant or empty otherwise
     */
    public Optional<DeploymentVariant> getVariantByCode(String code) {
        if (isBlank(code)) return Optional.empty();
        return repo.findByCode(code.trim());
    }

    /**
     * Searches for a variant by its name.
     *
     * @param name human readable name
     * @return optional containing the variant or empty otherwise
     */
    public Optional<DeploymentVariant> getVariantByName(String name) {
        if (isBlank(name)) return Optional.empty();
        return repo.findByName(name.trim());
    }

    // ---------- Commands ----------

    /**
     * Saves a deployment variant and indexes it after a successful commit.
     * Validates required fields for code and name.
     *
     * @param incoming variant to persist
     * @return stored variant
     */
    @Transactional
    public DeploymentVariant createOrUpdateVariant(DeploymentVariant incoming) {
        Objects.requireNonNull(incoming, "variant payload must not be null");

        if (isBlank(incoming.getVariantCode()))
            throw new IllegalArgumentException("VariantCode is required");
        if (isBlank(incoming.getVariantName()))
            throw new IllegalArgumentException("VariantName is required");

        if (incoming.getActive() == null) {
            incoming.setActive(Boolean.FALSE);
        }

        DeploymentVariant saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("DeploymentVariant saved: id={} code='{}' name='{}'",
                saved.getVariantID(), saved.getVariantCode(), saved.getVariantName());
        return saved;
    }

    /**
     * Updates an existing variant and synchronizes the Lucene index.
     *
     * @param id    variant identifier
     * @param patch changes to merge into the stored entity
     * @return optional containing the updated variant or empty otherwise
     */
    @Transactional
    public Optional<DeploymentVariant> updateVariant(UUID id, DeploymentVariant patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setVariantCode(nvl(patch.getVariantCode(), existing.getVariantCode()));
            existing.setVariantName(nvl(patch.getVariantName(), existing.getVariantName()));
            existing.setDescription(nvl(patch.getDescription(), existing.getDescription()));
            if (patch.getActive() != null) {
                existing.setActive(patch.getActive());
            }

            DeploymentVariant saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("DeploymentVariant updated: id={} name='{}'",
                    id, saved.getVariantName());
            return saved;
        });
    }

    /**
     * Permanently deletes a variant.
     *
     * @param id variant identifier
     */
    @Transactional
    public void deleteVariant(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(v -> {
            repo.deleteById(id);
            log.info("DeploymentVariant deleted: id={} name='{}'",
                    id, v.getVariantName());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    /**
     * Registers the After Commit Indexing for deferred execution.
     *
     * @param v deployment variant to index after the transaction commits.
     */
    private void registerAfterCommitIndexing(DeploymentVariant v) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(v);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * Indexes the deployment variant after the transaction commits.
             */
            @Override
            public void afterCommit() {
                indexToLucene(v);
            }
        });
    }

    /**
     * Indexes a deployment variant in Lucene for search operations.
     *
     * @param v deployment variant entity to index.
     */
    private void indexToLucene(DeploymentVariant v) {
        try {
            lucene.indexDeploymentVariant(
                    v.getVariantID() != null ? v.getVariantID().toString() : null,
                    v.getVariantCode(),
                    v.getVariantName(),
                    v.getDescription(),
                    v.isActive()
            );
            log.debug("DeploymentVariant indexed in Lucene: id={}", v.getVariantID());
        } catch (Exception e) {
            log.error("Lucene indexing for DeploymentVariant {} failed", v.getVariantID(), e);
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
}
