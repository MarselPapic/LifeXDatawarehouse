// src/main/java/at/htlle/freq/application/PhoneIntegrationService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.PhoneIntegration;
import at.htlle.freq.domain.PhoneIntegrationRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Manages phone integrations, validates required fields, and synchronizes data with Lucene.
 */
@Service
public class PhoneIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneIntegrationService.class);

    private final PhoneIntegrationRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index dependencies.
     *
     * @param repo   repository for phone integrations
     * @param lucene Lucene indexing service
     */
    public PhoneIntegrationService(PhoneIntegrationRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all phone integrations.
     *
     * @return list of all integrations
     */
    public List<PhoneIntegration> getAllPhoneIntegrations() {
        return repo.findAll();
    }

    /**
     * Retrieves an integration by its identifier.
     *
     * @param id integration identifier
     * @return optional containing the integration or empty otherwise
     */
    public Optional<PhoneIntegration> getPhoneIntegrationById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns integrations for a client.
     *
     * @param clientId client identifier
     * @return list of integrations
     */
    public List<PhoneIntegration> getPhoneIntegrationsBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    // ---------- Commands ----------

    /**
     * Saves a phone integration and indexes it in Lucene after the commit.
     * Validates the site and type.
     *
     * @param incoming integration to persist
     * @return stored integration
     */
    @Transactional
    public PhoneIntegration createOrUpdatePhoneIntegration(PhoneIntegration incoming) {
        Objects.requireNonNull(incoming, "phoneIntegration payload must not be null");

        if (incoming.getSiteID() == null)
            throw new IllegalArgumentException("SiteID is required");
        if (isBlank(incoming.getPhoneType()))
            throw new IllegalArgumentException("PhoneType is required");

        PhoneIntegration saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("PhoneIntegration saved: id={} site={} type='{}'",
                saved.getPhoneIntegrationID(), saved.getSiteID(), saved.getPhoneType());
        return saved;
    }

    /**
     * Updates a phone integration and synchronizes Lucene.
     *
     * @param id    integration identifier
     * @param patch changes to merge into the entity
     * @return optional with the updated integration or empty otherwise
     */
    @Transactional
    public Optional<PhoneIntegration> updatePhoneIntegration(UUID id, PhoneIntegration patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setSiteID(patch.getSiteID() != null ? patch.getSiteID() : existing.getSiteID());
            existing.setPhoneType(nvl(patch.getPhoneType(), existing.getPhoneType()));
            existing.setPhoneBrand(nvl(patch.getPhoneBrand(), existing.getPhoneBrand()));
            existing.setInterfaceName(nvl(patch.getInterfaceName(), existing.getInterfaceName()));
            existing.setCapacity(nvl(patch.getCapacity(), existing.getCapacity()));
            existing.setPhoneFirmware(nvl(patch.getPhoneFirmware(), existing.getPhoneFirmware()));

            PhoneIntegration saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("PhoneIntegration updated: id={} site={} type='{}'",
                    id, saved.getSiteID(), saved.getPhoneType());
            return saved;
        });
    }

    /**
     * Deletes a phone integration.
     *
     * @param id integration identifier
     */
    @Transactional
    public void deletePhoneIntegration(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(p -> {
            repo.deleteById(id);
            log.info("PhoneIntegration deleted: id={} site={} type='{}'",
                    id, p.getSiteID(), p.getPhoneType());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    /**
     * Registers the After Commit Indexing for deferred execution.
     * @param p p.
     */
    private void registerAfterCommitIndexing(PhoneIntegration p) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(p);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * Indexes the phone integration after the transaction commits.
             */
            @Override
            public void afterCommit() {
                indexToLucene(p);
            }
        });
    }

    /**
     * Indexes a phone integration in Lucene for search operations.
     *
     * @param p phone integration entity to index.
     */
    private void indexToLucene(PhoneIntegration p) {
        try {
            lucene.indexPhoneIntegration(
                    p.getPhoneIntegrationID() != null ? p.getPhoneIntegrationID().toString() : null,
                    p.getSiteID() != null ? p.getSiteID().toString() : null,
                    p.getPhoneType(),
                    p.getPhoneBrand(),
                    p.getInterfaceName(),
                    p.getCapacity(),
                    p.getPhoneFirmware()
            );
            log.debug("PhoneIntegration indexed in Lucene: id={}", p.getPhoneIntegrationID());
        } catch (Exception e) {
            log.error("Lucene indexing for PhoneIntegration {} failed", p.getPhoneIntegrationID(), e);
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
    private static <T> T nvl(T in, T fallback) {
        return in != null ? in : fallback;
    }
}
