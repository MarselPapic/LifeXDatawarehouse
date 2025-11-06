// src/main/java/at/htlle/freq/application/AddressService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.Address;
import at.htlle.freq.domain.AddressRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages address data, performs validation, and keeps the Lucene index in sync after
 * successful database transactions.
 */
@Service
public class AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

    private final AddressRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and Lucene dependencies.
     *
     * @param repo   repository for address entities
     * @param lucene service that keeps the index synchronized
     */
    public AddressService(AddressRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns every address.
     *
     * @return complete list of addresses
     */
    public List<Address> getAllAddresses() {
        return repo.findAll();
    }

    /**
     * Fetches an address by its identifier.
     *
     * @param id unique key of the address
     * @return optional with the matching address or empty otherwise
     */
    public Optional<Address> getAddressById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    // ---------- Commands ----------

    /**
     * Persists a new or existing address and indexes it in Lucene once the transaction commits.
     * Includes mandatory field checks for street and city.
     *
     * @param incoming address to store
     * @return stored address including its identifier
     */
    @Transactional
    public Address createAddress(Address incoming) {
        Objects.requireNonNull(incoming, "address payload must not be null");

        if (isBlank(incoming.getStreet())) {
            throw new IllegalArgumentException("Street is required");
        }
        if (isBlank(incoming.getCityID())) {
            throw new IllegalArgumentException("CityID is required");
        }

        // Persist the entity; the repository generates a UUID when necessary.
        repo.save(incoming);
        UUID id = incoming.getAddressID();

        // Index the record after the commit so Lucene and the database stay aligned.
        registerAfterCommitIndexing(incoming);

        log.info("Address saved: id={} street='{}' cityID='{}'", id, incoming.getStreet(), incoming.getCityID());
        return incoming;
    }

    /**
     * Updates an existing address and triggers indexing afterwards.
     *
     * @param id    unique key of the address
     * @param patch values to merge into the stored entity
     * @return optional containing the updated address or empty if it does not exist
     */
    @Transactional
    public Optional<Address> updateAddress(UUID id, Address patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setStreet(nvl(patch.getStreet(), existing.getStreet()));
            existing.setCityID(nvl(patch.getCityID(), existing.getCityID()));

            repo.save(existing);
            registerAfterCommitIndexing(existing);
            log.info("Address updated: id={} street='{}' cityID='{}'", id, existing.getStreet(), existing.getCityID());
            return existing;
        });
    }

    /**
     * Permanently deletes an address.
     *
     * @param id unique key of the address
     */
    @Transactional
    public void deleteAddress(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.deleteById(id);
        log.info("Address deleted: id={}", id);
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(Address a) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(a);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexToLucene(a);
            }
        });
    }

    private void indexToLucene(Address a) {
        try {
            lucene.indexAddress(
                    a.getAddressID() != null ? a.getAddressID().toString() : null,
                    a.getStreet(),
                    a.getCityID()
            );
            log.debug("Address indexed in Lucene: id={}", a.getAddressID());
        } catch (Exception e) {
            log.error("Lucene indexing for Address {} failed", a.getAddressID(), e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String in, String fallback) {
        return in != null ? in : fallback;
    }
}
