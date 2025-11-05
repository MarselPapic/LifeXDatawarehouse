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

    public City() {}
    public City(String cityID, String cityName, String countryCode) {
        this.cityID = cityID;
        this.cityName = cityName;
        this.countryCode = countryCode;
    }

    public String getCityID() { return cityID; }
    public void setCityID(String cityID) { this.cityID = cityID; }

    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
}
