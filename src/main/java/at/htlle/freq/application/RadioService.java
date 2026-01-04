// src/main/java/at/htlle/freq/application/RadioService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.Radio;
import at.htlle.freq.domain.RadioRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Manages radios, validates required fields, and synchronizes Lucene.
 */
@Service
public class RadioService {

    private static final Logger log = LoggerFactory.getLogger(RadioService.class);

    private final RadioRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index dependencies.
     *
     * @param repo   repository for radios
     * @param lucene Lucene indexing service
     */
    public RadioService(RadioRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all radios.
     *
     * @return list of radios
     */
    public List<Radio> getAllRadios() {
        return repo.findAll();
    }

    /**
     * Retrieves a radio by its identifier.
     *
     * @param id radio identifier
     * @return optional containing the radio or empty otherwise
     */
    public Optional<Radio> getRadioById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns radios for a site.
     *
     * @param siteId site identifier
     * @return list of radios
     */
    public List<Radio> getRadiosBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    // ---------- Commands ----------

    /**
     * Saves a radio and validates required fields such as site, brand, serial number, and mode.
     * Indexes the record in Lucene after the commit.
     *
     * @param incoming radio to persist
     * @return stored radio
     */
    @Transactional
    public Radio createOrUpdateRadio(Radio incoming) {
        Objects.requireNonNull(incoming, "radio payload must not be null");

        if (incoming.getSiteID() == null)
            throw new IllegalArgumentException("SiteID is required");
        if (isBlank(incoming.getRadioBrand()))
            throw new IllegalArgumentException("RadioBrand is required");
        if (isBlank(incoming.getRadioSerialNr()))
            throw new IllegalArgumentException("RadioSerialNr is required");
        if (isBlank(incoming.getMode()))
            throw new IllegalArgumentException("Mode is required");

        Radio saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("Radio saved: id={} site={} brand='{}' serialNr='{}'",
                saved.getRadioID(), saved.getSiteID(), saved.getRadioBrand(), saved.getRadioSerialNr());
        return saved;
    }

    /**
     * Updates a radio and synchronizes Lucene.
     *
     * @param id    radio identifier
     * @param patch changes to merge into the entity
     * @return optional containing the updated radio or empty otherwise
     */
    @Transactional
    public Optional<Radio> updateRadio(UUID id, Radio patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setSiteID(patch.getSiteID() != null ? patch.getSiteID() : existing.getSiteID());
            existing.setAssignedClientID(patch.getAssignedClientID() != null ? patch.getAssignedClientID() : existing.getAssignedClientID());
            existing.setRadioBrand(nvl(patch.getRadioBrand(), existing.getRadioBrand()));
            existing.setRadioSerialNr(nvl(patch.getRadioSerialNr(), existing.getRadioSerialNr()));
            existing.setMode(nvl(patch.getMode(), existing.getMode()));
            existing.setDigitalStandard(nvl(patch.getDigitalStandard(), existing.getDigitalStandard()));

            Radio saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("Radio updated: id={} brand='{}'", id, saved.getRadioBrand());
            return saved;
        });
    }

    /**
     * Deletes a radio.
     *
     * @param id radio identifier
     */
    @Transactional
    public void deleteRadio(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(r -> {
            repo.deleteById(id);
            log.info("Radio deleted: id={} brand='{}' serialNr='{}'",
                    id, r.getRadioBrand(), r.getRadioSerialNr());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    /**
     * Registers the After Commit Indexing for deferred execution.
     * @param r r.
     */
    private void registerAfterCommitIndexing(Radio r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(r);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * Indexes the radio after the transaction commits.
             */
            @Override
            public void afterCommit() {
                indexToLucene(r);
            }
        });
    }

    /**
     * Indexes a radio in Lucene for search operations.
     *
     * @param r radio entity to index.
     */
    private void indexToLucene(Radio r) {
        try {
            lucene.indexRadio(
                    r.getRadioID() != null ? r.getRadioID().toString() : null,
                    r.getSiteID() != null ? r.getSiteID().toString() : null,
                    r.getAssignedClientID() != null ? r.getAssignedClientID().toString() : null,
                    r.getRadioBrand(),
                    r.getRadioSerialNr(),
                    r.getMode(),
                    r.getDigitalStandard()
            );
            log.debug("Radio indexed in Lucene: id={}", r.getRadioID());
        } catch (Exception e) {
            log.error("Lucene indexing for Radio {} failed", r.getRadioID(), e);
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
