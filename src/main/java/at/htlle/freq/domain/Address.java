package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Describes a physical location that can be assigned to {@link Site} or
 * {@link Project} entities. The address holds the street level information and
 * references the {@link City} record via {@code cityID} to enrich the location
 * with region specific metadata.
 */
public class Address {
    private UUID addressID;
    private String street;
    private String cityID; // FK -> City.cityID

    /**
     * Creates an empty address instance.
     */
    public Address() {}
    /**
     * Creates an address with its primary attributes.
     *
     * @param addressID address identifier.
     * @param street street name and number.
     * @param cityID foreign key to the city record.
     */
    public Address(UUID addressID, String street, String cityID) {
        this.addressID = addressID;
        this.street = street;
        this.cityID = cityID;
    }

    /**
     * Returns the Address ID value held by this instance.
     * @return the Address ID value.
     */
    public UUID getAddressID() { return addressID; }
    /**
     * Sets the Address ID value and updates the current state.
     * @param addressID address identifier.
     */
    public void setAddressID(UUID addressID) { this.addressID = addressID; }

    /**
     * Returns the Street value held by this instance.
     * @return the Street value.
     */
    public String getStreet() { return street; }
    /**
     * Sets the Street value and updates the current state.
     * @param street street.
     */
    public void setStreet(String street) { this.street = street; }

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
}
