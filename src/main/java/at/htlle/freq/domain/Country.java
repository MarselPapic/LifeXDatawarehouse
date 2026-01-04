package at.htlle.freq.domain;

/**
 * Stores ISO country master data used across the data warehouse. Cities and
 * accounts refer to the {@code countryCode} to normalize reporting and link to
 * geographical context.
 */
public class Country {
    private String countryCode; // ISO-3166-1 alpha-2
    private String countryName;

    /**
     * Creates a new Country instance.
     */
    public Country() {}
    /**
     * Creates a new Country instance and initializes it with the provided values.
     * @param countryCode country code.
     * @param countryName country name.
     */
    public Country(String countryCode, String countryName) {
        this.countryCode = countryCode;
        this.countryName = countryName;
    }

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

    /**
     * Returns the Country Name value held by this instance.
     * @return the Country Name value.
     */
    public String getCountryName() { return countryName; }
    /**
     * Sets the Country Name value and updates the current state.
     * @param countryName country name.
     */
    public void setCountryName(String countryName) { this.countryName = countryName; }
}
