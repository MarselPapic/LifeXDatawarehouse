package at.htlle.freq.domain;

import java.util.*;

public interface AddressRepository {
    Optional<Address> findById(UUID id);
    Address save(Address address);
    List<Address> findAll();
    void deleteById(UUID id);
}
