package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository abstraction for {@link ServiceContract} agreements.
 */
public interface ServiceContractRepository {
    /**
     * Finds a contract by its identifier.
     *
     * @param id primary key
     * @return optional contract entity
     */
    Optional<ServiceContract> findById(UUID id);

    /**
     * Retrieves all contracts owned by an account.
     *
     * @param accountId identifier of the {@link Account}
     * @return list of contracts linked to the account
     */
    List<ServiceContract> findByAccount(UUID accountId);

    /**
     * Persists the given contract.
     *
     * @param contract contract to store
     * @return managed contract entity
     */
    ServiceContract save(ServiceContract contract);

    /**
     * Lists all service contracts.
     *
     * @return snapshot of contracts
     */
    List<ServiceContract> findAll();
}
