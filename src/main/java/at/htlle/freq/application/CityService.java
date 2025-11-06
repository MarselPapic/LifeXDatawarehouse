
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
 * Manages cities, validates master data, and keeps them synchronized with the Lucene index.
 */
@Service
public class CityService {

    private static final Logger log = LoggerFactory.getLogger(CityService.class);

    private final CityRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with the required dependencies.
     *
     * @param repo   repository for cities
     * @param lucene Lucene index service
     */
    public CityService(CityRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Retrieves every city.
     *
     * @return list of all city records
     */
    public List<City> getAllCities() {
        return repo.findAll();
    }

    /**
     * Loads a city by its identifier.
     *
     * @param id unique city identifier
     * @return optional containing the city or empty if not present
     */
    public Optional<City> getCityById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Retrieves all cities of a given country.
     *
     * @param countryCode ISO country code
     * @return list of the country's cities
     */
    public List<City> getCitiesByCountry(String countryCode) {
        Objects.requireNonNull(countryCode, "countryCode must not be null");
        return repo.findByCountry(countryCode);
    }

    // ---------- Commands ----------

    /**
     * Persists a city and indexes it in Lucene after a successful commit.
     * Performs mandatory field validations for ID and name.
     *
     * @param incoming city to store
     * @return persisted city record
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

        log.info("City saved: id={} name='{}'", saved.getCityID(), saved.getCityName());
        return saved;
    }

    /**
     * Updates a city based on a patch representation.
     *
     * @param id    unique city identifier
     * @param patch values to merge into the existing entity
     * @return optional with the updated city or empty if it was not found
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

            log.info("City updated: id={} name='{}'", id, saved.getCityName());
            return saved;
        });
    }

    /**
     * Permanently deletes a city.
     *
     * @param id unique city identifier
     */
    @Transactional
    public void deleteCity(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Optional<City> existing = repo.findById(id);
        repo.deleteById(id);
        existing.ifPresentOrElse(
                c -> log.info("City deleted: id={} name='{}'", id, c.getCityName()),
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
            log.debug("City indexed in Lucene: id={}", c.getCityID());
        } catch (Exception e) {
            log.error("Lucene indexing for City {} failed", c.getCityID(), e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String in, String fallback) {
        return in != null ? in : fallback;
    }
}
