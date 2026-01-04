// src/main/java/at/htlle/freq/application/ServiceContractService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.ServiceContract;
import at.htlle.freq.domain.ServiceContractRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Manages service contracts, validates required fields, and keeps Lucene synchronized.
 */
@Service
public class ServiceContractService {

    private static final Logger log = LoggerFactory.getLogger(ServiceContractService.class);

    private final ServiceContractRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index dependencies.
     *
     * @param repo   repository for contracts
     * @param lucene Lucene indexing service
     */
    public ServiceContractService(ServiceContractRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all service contracts.
     *
     * @return list of contracts
     */
    public List<ServiceContract> getAllContracts() {
        return repo.findAll();
    }

    /**
     * Retrieves a contract by its identifier.
     *
     * @param id contract identifier
     * @return optional containing the contract or empty otherwise
     */
    public Optional<ServiceContract> getContractById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns contracts of an account.
     *
     * @param accountId account identifier
     * @return list of the account's contracts
     */
    public List<ServiceContract> getContractsByAccount(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        return repo.findByAccount(accountId);
    }

    // ---------- Commands ----------

    /**
     * Saves a contract, validates required fields, and indexes it in Lucene after the commit.
     *
     * @param incoming contract to persist
     * @return stored contract
     */
    @Transactional
    public ServiceContract createOrUpdateContract(ServiceContract incoming) {
        Objects.requireNonNull(incoming, "contract payload must not be null");

        if (incoming.getAccountID() == null)
            throw new IllegalArgumentException("AccountID is required");
        if (incoming.getProjectID() == null)
            throw new IllegalArgumentException("ProjectID is required");
        if (isBlank(incoming.getContractNumber()))
            throw new IllegalArgumentException("ContractNumber is required");
        if (isBlank(incoming.getStatus()))
            throw new IllegalArgumentException("Status is required");

        ServiceContract saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("ServiceContract saved: id={} contractNumber='{}' status='{}'",
                saved.getContractID(), saved.getContractNumber(), saved.getStatus());
        return saved;
    }

    /**
     * Updates a contract and synchronizes Lucene.
     *
     * @param id    contract identifier
     * @param patch changes to merge into the entity
     * @return optional containing the updated contract or empty otherwise
     */
    @Transactional
    public Optional<ServiceContract> updateContract(UUID id, ServiceContract patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setAccountID(patch.getAccountID() != null ? patch.getAccountID() : existing.getAccountID());
            existing.setProjectID(patch.getProjectID() != null ? patch.getProjectID() : existing.getProjectID());
            existing.setSiteID(patch.getSiteID() != null ? patch.getSiteID() : existing.getSiteID());
            existing.setContractNumber(nvl(patch.getContractNumber(), existing.getContractNumber()));
            existing.setStatus(nvl(patch.getStatus(), existing.getStatus()));
            existing.setStartDate(nvl(patch.getStartDate(), existing.getStartDate()));
            existing.setEndDate(nvl(patch.getEndDate(), existing.getEndDate()));

            ServiceContract saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("ServiceContract updated: id={} number='{}' status='{}'",
                    id, saved.getContractNumber(), saved.getStatus());
            return saved;
        });
    }

    /**
     * Deletes a contract.
     *
     * @param id contract identifier
     */
    @Transactional
    public void deleteContract(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(sc -> {
            repo.deleteById(id);
            log.info("ServiceContract deleted: id={} contractNumber='{}'", id, sc.getContractNumber());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    /**
     * Registers the After Commit Indexing for deferred execution.
     * @param sc sc.
     */
    private void registerAfterCommitIndexing(ServiceContract sc) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(sc);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * Indexes the service contract after the transaction commits.
             */
            @Override
            public void afterCommit() {
                indexToLucene(sc);
            }
        });
    }

    /**
     * Indexes a service contract in Lucene for search operations.
     *
     * @param sc service contract entity to index.
     */
    private void indexToLucene(ServiceContract sc) {
        try {
            lucene.indexServiceContract(
                    sc.getContractID() != null ? sc.getContractID().toString() : null,
                    sc.getAccountID() != null ? sc.getAccountID().toString() : null,
                    sc.getProjectID() != null ? sc.getProjectID().toString() : null,
                    sc.getSiteID() != null ? sc.getSiteID().toString() : null,
                    sc.getContractNumber(),
                    sc.getStatus(),
                    sc.getStartDate() != null ? sc.getStartDate().toString() : null,
                    sc.getEndDate() != null ? sc.getEndDate().toString() : null
            );
            log.debug("ServiceContract indexed in Lucene: id={}", sc.getContractID());
        } catch (Exception e) {
            log.error("Lucene indexing for ServiceContract {} failed", sc.getContractID(), e);
        }
    }

    // ---------- Utils ----------

    /**
     * Checks whether a string is null or blank.
     *
     * @param s input string.
     * @return true when the string is null, empty, or whitespace.
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Returns the fallback when the input is null.
     *
     * @param in input value.
     * @param fallback fallback value.
     * @return input when non-null, otherwise fallback.
     */
    private static <T> T nvl(T in, T fallback) {
        return in != null ? in : fallback;
    }
}
