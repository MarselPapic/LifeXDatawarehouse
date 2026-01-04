package at.htlle.freq.domain;

/**
 * Holds the master data for a city used in address records. The natural key
 * {@code cityID} is referenced by {@link Address} entities while
 * {@code countryCode} links each city to a {@link Country} entry.
 */
public class City {
    private String cityID;      // natural key
    private String cityName;
    private String countryCode; // FK -> Country.countryCode

    /**
     * Creates a new City instance.
     */
    public City() {}
    /**
     * Creates a new City instance and initializes it with the provided values.
     * @param cityID city identifier.
     * @param cityName city name.
     * @param countryCode country code.
     */
    public City(String cityID, String cityName, String countryCode) {
        this.cityID = cityID;
        this.cityName = cityName;
        this.countryCode = countryCode;
    }

    /**
     * Returns the City ID value held by this instance.
     * @return the City ID value.
     */
    public String getCityID() { return cityID; }
    /**
     * Sets the City ID value and updates the current state.
     * @param cityID city identifier.
     */
    public void setCityID(String cityID) { this.cityID = cityID; }

    /**
     * Returns the City Name value held by this instance.
     * @return the City Name value.
     */
    public String getCityName() { return cityName; }
    /**
     * Sets the City Name value and updates the current state.
     * @param cityName city name.
     */
    public void setCityName(String cityName) { this.cityName = cityName; }

    /**
     * Returns the Country Code value held by this instance.
     * @return the Country Code value.
     */
    public String getCountryCode() { return countryCode; }
    /**
     * Sets the Country Code value and updates the current state.
     * @param countryCode country code.
     */
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
}
