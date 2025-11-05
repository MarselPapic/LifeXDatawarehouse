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
 * Betreut Adressdaten, führt Validierungen durch und hält den Lucene-Index nach
 * erfolgreichen Datenbanktransaktionen konsistent.
 */
@Service
public class AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

    private final AddressRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Erstellt den Service mit Repository- und Lucene-Abhängigkeiten.
     *
     * @param repo   Repository für Adressentitäten
     * @param lucene Service zur Index-Synchronisierung
     */
    public AddressService(AddressRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Liefert sämtliche Adressen.
     *
     * @return vollständige Liste aller Adressen
     */
    public List<Address> getAllAddresses() {
        return repo.findAll();
    }

    /**
     * Sucht eine Adresse anhand ihrer ID.
     *
     * @param id eindeutiger Schlüssel der Adresse
     * @return Optional mit der gefundenen Adresse oder leer
     */
    public Optional<Address> getAddressById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    // ---------- Commands ----------

    /**
     * Persistiert eine neue oder bestehende Adresse und indexiert sie nach dem
     * Transaktions-Commit in Lucene. Enthält Pflichtfeldprüfungen für Straße und City.
     *
     * @param incoming zu speichernde Adresse
     * @return die gespeicherte Adresse inklusive ID
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
     * Aktualisiert eine bestehende Adresse und stößt anschließend das Indexieren an.
     *
     * @param id    eindeutiger Schlüssel der Adresse
     * @param patch Änderungswerte, die übernommen werden sollen
     * @return Optional mit der aktualisierten Adresse oder leer, falls nicht gefunden
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

    /**
     * Löscht eine Adresse dauerhaft.
     *
     * @param id eindeutiger Schlüssel der Adresse
     */
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
