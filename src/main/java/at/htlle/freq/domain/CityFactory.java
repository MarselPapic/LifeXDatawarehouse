package at.htlle.freq.domain;

import org.springframework.stereotype.Component;

/**
 * Factory responsible for creating City instances.
 */
@Component
public class CityFactory {
    /**
     * Instantiates a {@link City} using the provided natural key and metadata.
     *
     * @param cityID natural key used across the warehouse
     * @param cityName localized display name
     * @param countryCode reference to the owning {@link Country}
     * @return an immutable city value object
     */
    public City create(String cityID, String cityName, String countryCode) {
        return new City(cityID, cityName, countryCode);
    }
}
