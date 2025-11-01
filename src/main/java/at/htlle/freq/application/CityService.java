
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

@Service
public class CityService {

    private static final Logger log = LoggerFactory.getLogger(CityService.class);

    private final CityRepository repo;
    private final LuceneIndexService lucene;

    public CityService(CityRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    public List<City> getAllCities() {
        return repo.findAll();
    }

    public Optional<City> getCityById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    public List<City> getCitiesByCountry(String countryCode) {
        Objects.requireNonNull(countryCode, "countryCode must not be null");
        return repo.findByCountry(countryCode);
    }

    // ---------- Commands ----------

    /**
     * Legt eine City an oder aktualisiert sie, und indexiert sie in Lucene
     * NACH erfolgreichem Commit der DB-Transaktion.
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
