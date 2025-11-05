package at.htlle.freq.domain;

import org.springframework.stereotype.Component;

@Component
public class CountryFactory {
    /**
     * Creates a {@link Country} value object populated with its ISO code and
     * human readable name.
     *
     * @param countryCode ISO-3166-1 alpha-2 code
     * @param countryName localized country name
     * @return immutable country representation
     */
    public Country create(String countryCode, String countryName) {
        return new Country(countryCode, countryName);
    }
}
