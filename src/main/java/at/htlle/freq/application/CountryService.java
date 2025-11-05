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
 * Verwaltet Länderstammdaten und hält Lucene nach erfolgreichen Transaktionen synchron.
 */
@Service
public class CountryService {

    private static final Logger log = LoggerFactory.getLogger(CountryService.class);

    private final CountryRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Erstellt den Service mit Repository- und Index-Abhängigkeiten.
     *
     * @param repo   Repository für Länder
     * @param lucene Indexservice für Lucene
     */
    public CountryService(CountryRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Liefert alle Länder.
     *
     * @return Liste aller Länder
     */
    public List<Country> getAllCountries() {
        return repo.findAll();
    }

    /**
     * Holt ein Land anhand seines Codes.
     *
     * @param code Ländercode (Primärschlüssel)
     * @return Optional mit Land oder leer
     */
    public Optional<Country> getCountryByCode(String code) {
        Objects.requireNonNull(code, "country code must not be null");
        return repo.findById(code);
    }

    // ---------- Commands ----------

    /**
     * Speichert ein Land und indexiert es nach dem Commit in Lucene. Validiert
     * Pflichtfelder für Code und Name.
     *
     * @param incoming Land, das erstellt oder aktualisiert werden soll
     * @return gespeichertes Land
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

        log.info("Country gespeichert: code={} name='{}'",
                saved.getCountryCode(), saved.getCountryName());
        return saved;
    }

    /**
     * Aktualisiert ein bestehendes Land und synchronisiert anschließend Lucene.
     *
     * @param code  Ländercode
     * @param patch Änderungen, die übernommen werden sollen
     * @return Optional mit dem aktualisierten Land oder leer
     */
    @Transactional
    public Optional<Country> updateCountry(String code, Country patch) {
        Objects.requireNonNull(code, "country code must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(code).map(existing -> {
            existing.setCountryName(nvl(patch.getCountryName(), existing.getCountryName()));

            Country saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("Country aktualisiert: code={} name='{}'",
                    code, saved.getCountryName());
            return saved;
        });
    }

    /**
     * Entfernt ein Land dauerhaft aus der Datenbank.
     *
     * @param code Ländercode
     */
    @Transactional
    public void deleteCountry(String code) {
        Objects.requireNonNull(code, "country code must not be null");
        repo.findById(code).ifPresent(c -> {
            log.info("Country gelöscht: code={} name='{}'",
                    code, c.getCountryName());
            // Optional: lucene.deleteCountry(code);
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
            log.debug("Country in Lucene indexiert: code={}", c.getCountryCode());
        } catch (Exception e) {
            log.error("Lucene-Indexing für Country {} fehlgeschlagen", c.getCountryCode(), e);
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
