package at.htlle.freq.domain;

import java.util.List;
import java.util.Optional;

/**
 * Data access abstraction for {@link City} master data.
 */
public interface CityRepository {
    /**
     * Finds a city by its natural identifier.
     *
     * @param id natural key of the city
     * @return optional result when the city exists
     */
    Optional<City> findById(String id);

    /**
     * Returns all cities that belong to the given country.
     *
     * @param countryCode ISO country code used in {@link Country}
     * @return list of matching city records
     */
    List<City> findByCountry(String countryCode);

    /**
     * Saves a new or updated city record.
     *
     * @param city entity to persist
     * @return the managed city instance
     */
    City save(City city);

    /**
     * Loads every city available in the repository.
     *
     * @return list of all cities
     */
    List<City> findAll();

    /**
     * Deletes a city using its natural identifier.
     *
     * @param id natural key of the city to remove
     */
    void deleteById(String id);
}
