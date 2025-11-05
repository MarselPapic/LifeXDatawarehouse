
package at.htlle.freq.application;

import at.htlle.freq.domain.City;
import at.htlle.freq.domain.CityRepository;
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
 * Verwaltet Städte, validiert Stammdaten und synchronisiert sie mit dem Lucene-Index.
 */
@Service
public class CityService {

    private static final Logger log = LoggerFactory.getLogger(CityService.class);

    private final CityRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Erstellt den Service mit den erforderlichen Abhängigkeiten.
     *
     * @param repo   Repository für Städte
     * @param lucene Index-Service für Lucene
     */
    public CityService(CityRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Liefert alle Städte.
     *
     * @return Liste aller Stadt-Datensätze
     */
    public List<City> getAllCities() {
        return repo.findAll();
    }

    /**
     * Holt eine Stadt anhand ihrer ID.
     *
     * @param id eindeutige Stadt-ID
     * @return Optional mit der Stadt oder leer, falls nicht vorhanden
     */
    public Optional<City> getCityById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Liefert alle Städte eines Landes.
     *
     * @param countryCode ISO-Ländercode
     * @return Liste der Städte des Landes
     */
    public List<City> getCitiesByCountry(String countryCode) {
        Objects.requireNonNull(countryCode, "countryCode must not be null");
        return repo.findByCountry(countryCode);
    }

    // ---------- Commands ----------

    /**
     * Speichert eine Stadt und indexiert sie nach erfolgreichem Commit in Lucene.
     * Führt Pflichtfeldprüfungen für ID und Name durch.
     *
     * @param incoming zu speichernde Stadt
     * @return gespeicherter Stadt-Datensatz
     */
    @Transactional
    public City createOrUpdateCity(City incoming) {
        Objects.requireNonNull(incoming, "city payload must not be null");

        if (isBlank(incoming.getCityID()))
            throw new IllegalArgumentException("CityID is required");
        if (isBlank(incoming.getCityName()))
            throw new IllegalArgumentException("CityName is required");

        City saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("City gespeichert: id={} name='{}'", saved.getCityID(), saved.getCityName());
        return saved;
    }

    /**
     * Aktualisiert eine Stadt anhand einer Patch-Repräsentation.
     *
     * @param id    eindeutige Stadt-ID
     * @param patch Änderungen, die übernommen werden sollen
     * @return Optional mit der aktualisierten Stadt oder leer, falls nicht gefunden
     */
    @Transactional
    public Optional<City> updateCity(String id, City patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setCityName(nvl(patch.getCityName(), existing.getCityName()));
            existing.setCountryCode(nvl(patch.getCountryCode(), existing.getCountryCode()));

            City saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("City aktualisiert: id={} name='{}'", id, saved.getCityName());
            return saved;
        });
    }

    /**
     * Löscht eine Stadt dauerhaft.
     *
     * @param id eindeutige Stadt-ID
     */
    @Transactional
    public void deleteCity(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Optional<City> existing = repo.findById(id);
        repo.deleteById(id);
        existing.ifPresentOrElse(
                c -> log.info("City gelöscht: id={} name='{}'", id, c.getCityName()),
                () -> log.info("City delete requested: id={} (not found)", id)
        );
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(City c) {
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

    private void indexToLucene(City c) {
        try {
            lucene.indexCity(
                    c.getCityID(),
                    c.getCityName(),
                    c.getCountryCode()
            );
            log.debug("City in Lucene indexiert: id={}", c.getCityID());
        } catch (Exception e) {
            log.error("Lucene-Indexing für City {} fehlgeschlagen", c.getCityID(), e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String in, String fallback) {
        return in != null ? in : fallback;
    }
}
