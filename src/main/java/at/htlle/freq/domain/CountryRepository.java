package at.htlle.freq.domain;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for {@link Country} entities.
 */
public interface CountryRepository {
    /**
     * Looks up a country by its ISO code.
     *
     * @param code ISO-3166 code
     * @return matching country or empty optional
     */
    Optional<Country> findById(String code);

    /**
     * Saves a country record.
     *
     * @param country country master data to persist
     * @return the managed instance after persistence
     */
    Country save(Country country);

    /**
     * Returns all country entries.
     *
     * @return list of known countries
     */
    List<Country> findAll();
}
