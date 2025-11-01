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

@Service
public class AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

    private final AddressRepository repo;
    private final LuceneIndexService lucene;

    public AddressService(AddressRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    public List<Address> getAllAddresses() {
        return repo.findAll();
    }

    public Optional<Address> getAddressById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    // ---------- Commands ----------

    /**
     * Legt eine Address an (oder updated, falls ID gesetzt ist)
     * und indexiert sie nach erfolgreichem Commit in Lucene.
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

        // Persistieren (Repo generiert UUID, falls null)
        repo.save(incoming);
        UUID id = incoming.getAddressID();

        // Nach Commit indexieren, damit Index & DB konsistent bleiben
        registerAfterCommitIndexing(incoming);

        log.info("Address gespeichert: id={} street='{}' cityID='{}'", id, incoming.getStreet(), incoming.getCityID());
        return incoming;
    }

    /**
     * Optionales Update analog zu AccountService.updateAccount.
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
            log.info("Address aktualisiert: id={} street='{}' cityID='{}'", id, existing.getStreet(), existing.getCityID());
            return existing;
        });
    }

    @Transactional
    public void deleteAddress(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.deleteById(id);
        log.info("Address gelöscht: id={}", id);
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
            log.debug("Address in Lucene indexiert: id={}", a.getAddressID());
        } catch (Exception e) {
            log.error("Lucene-Indexing für Address {} fehlgeschlagen", a.getAddressID(), e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String in, String fallback) {
        return in != null ? in : fallback;
    }
}
