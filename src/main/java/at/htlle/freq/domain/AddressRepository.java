package at.htlle.freq.domain;

import java.util.*;

/**
 * Persistence abstraction for {@link Address} entities.
 */
public interface AddressRepository {
    /**
     * Loads an address by its technical identifier.
     *
     * @param id primary key of the address
     * @return the matching address or {@link Optional#empty()} if not found
     */
    Optional<Address> findById(UUID id);

    /**
     * Persists a new or existing address entity.
     *
     * @param address the address to create or update
     * @return the saved entity, potentially carrying generated identifiers
     */
    Address save(Address address);

    /**
     * Retrieves all addresses from the data store.
     *
     * @return immutable snapshot of all known addresses
     */
    List<Address> findAll();

    /**
     * Deletes the address with the given identifier.
     *
     * @param id primary key of the address to remove
     */
    void deleteById(UUID id);
}
