package at.htlle.freq.domain;

import org.springframework.stereotype.Component;

@Component
public class AddressFactory {
    /**
     * Builds a new {@link Address} for the given street and city. The
     * {@code addressID} is not set so that the database sequence can populate it
     * on insert.
     *
     * @param street human readable street and house number
     * @param cityID identifier of the {@link City} the address belongs to
     * @return a transient address entity
     */
    public Address create(String street, String cityID) {
        return new Address(null, street, cityID);
    }
}
