package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository abstraction for {@link PhoneIntegration} peripherals.
 */
public interface PhoneIntegrationRepository {
    /**
     * Finds a phone integration by its id.
     *
     * @param id primary key of the integration
     * @return optional phone integration entity
     */
    Optional<PhoneIntegration> findById(UUID id);

    /**
     * Retrieves all phone integrations assigned to a client workstation.
     *
     * @param clientId owner client workstation identifier
     * @return list of matching phone integrations
     */
    List<PhoneIntegration> findByClient(UUID clientId);

    /**
     * Stores the given phone integration entity.
     *
     * @param phone entity to persist
     * @return managed instance after persistence
     */
    PhoneIntegration save(PhoneIntegration phone);

    /**
     * Lists all phone integrations.
     *
     * @return snapshot of every phone integration entry
     */
    List<PhoneIntegration> findAll();

    /**
     * Deletes a phone integration by its identifier.
     *
     * @param id primary key
     */
    void deleteById(UUID id);
}
