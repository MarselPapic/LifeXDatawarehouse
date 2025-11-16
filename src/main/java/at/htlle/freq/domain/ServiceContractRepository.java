package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository abstraction for {@link ServiceContract} agreements with customer
 * accounts.
 */
public interface ServiceContractRepository {
    /**
     * Finds a service contract by its identifier.
     *
     * @param id primary key
     * @return optional service contract entity
     */
    Optional<ServiceContract> findById(UUID id);

    /**
     * Retrieves all contracts owned by a customer account.
     *
     * @param accountId identifier of the customer {@link Account}
     * @return list of contracts linked to the customer account
     */
    List<ServiceContract> findByAccount(UUID accountId);

    /**
     * Persists the given service contract.
     *
     * @param contract contract to store
     * @return managed contract entity
     */
    ServiceContract save(ServiceContract contract);

    /**
     * Lists all service contracts.
     *
     * @return snapshot of service contracts
     */
    List<ServiceContract> findAll();

    /**
     * Deletes a service contract by its identifier.
     *
     * @param id primary key
     */
    void deleteById(UUID id);
}
