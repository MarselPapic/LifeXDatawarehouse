package at.htlle.freq.domain;

/**
 * Stores ISO country master data used across the data warehouse. Cities and
 * accounts refer to the {@code countryCode} to normalize reporting and link to
 * geographical context.
 */
public class Country {
    private String countryCode; // ISO-3166-1 alpha-2
    private String countryName;

    public Country() {}
    public Country(String countryCode, String countryName) {
        this.countryCode = countryCode;
        this.countryName = countryName;
    }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
}
