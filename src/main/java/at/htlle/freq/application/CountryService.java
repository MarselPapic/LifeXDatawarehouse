// src/main/java/at/htlle/freq/application/CountryService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.Country;
import at.htlle.freq.domain.CountryRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages country master data and keeps Lucene synchronized after successful transactions.
 */
@Service
public class CountryService {

    private static final Logger log = LoggerFactory.getLogger(CountryService.class);

    private final CountryRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index dependencies.
     *
     * @param repo   repository for countries
     * @param lucene Lucene index service
     */
    public CountryService(CountryRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all countries.
     *
     * @return list of every country
     */
    public List<Country> getAllCountries() {
        return repo.findAll();
    }

    /**
     * Loads a country by its code.
     *
     * @param code country code (primary key)
     * @return optional containing the country or empty if missing
     */
    public Optional<Country> getCountryByCode(String code) {
        Objects.requireNonNull(code, "country code must not be null");
        return repo.findById(code);
    }

    // ---------- Commands ----------

    /**
     * Saves a country and indexes it in Lucene after the commit. Validates required fields for
     * code and name.
     *
     * @param incoming country to create or update
     * @return persisted country
     */
    @Transactional
    public Country createOrUpdateCountry(Country incoming) {
        Objects.requireNonNull(incoming, "country payload must not be null");

        if (isBlank(incoming.getCountryCode()))
            throw new IllegalArgumentException("CountryCode is required");
        if (isBlank(incoming.getCountryName()))
            throw new IllegalArgumentException("CountryName is required");

        Country saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("Country saved: code={} name='{}'",
                saved.getCountryCode(), saved.getCountryName());
        return saved;
    }

    /**
     * Updates an existing country and synchronizes Lucene afterwards.
     *
     * @param code  country code
     * @param patch changes to merge into the entity
     * @return optional containing the updated country or empty otherwise
     */
    @Transactional
    public Optional<Country> updateCountry(String code, Country patch) {
        Objects.requireNonNull(code, "country code must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(code).map(existing -> {
            existing.setCountryName(nvl(patch.getCountryName(), existing.getCountryName()));

            Country saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("Country updated: code={} name='{}'",
                    code, saved.getCountryName());
            return saved;
        });
    }

    /**
     * Permanently removes a country from the database.
     *
     * @param code country code
     */
    @Transactional
    public void deleteCountry(String code) {
        Objects.requireNonNull(code, "country code must not be null");
        repo.findById(code).ifPresent(c -> {
            repo.deleteById(code);
            log.info("Country deleted: code={} name='{}'",
                    code, c.getCountryName());
            // Optionally remove the entry from Lucene when delete support is added.
        });
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(Country c) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(c);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexToLucene(c);
            }
        });
    }

    private void indexToLucene(Country c) {
        try {
            lucene.indexCountry(
                    c.getCountryCode(),
                    c.getCountryName()
            );
            log.debug("Country indexed in Lucene: code={}", c.getCountryCode());
        } catch (Exception e) {
            log.error("Lucene indexing for Country {} failed", c.getCountryCode(), e);
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
