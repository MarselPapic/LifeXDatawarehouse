package at.htlle.freq.application;

import at.htlle.freq.domain.Clients;
import at.htlle.freq.domain.ClientsRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Service-Schicht für Clients (WorkingPositions)
 * Kümmert sich um DB-Operationen, Validation und Lucene-Indexierung.
 */
@Service
public class ClientsService {

    private static final Logger log = LoggerFactory.getLogger(ClientsService.class);

    private final ClientsRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Erstellt den Service mit Repository- und Lucene-Komponenten.
     *
     * @param repo   Repository für Clients
     * @param lucene Index-Dienst für Lucene
     */
    public ClientsService(ClientsRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ----------------------------
    // READ
    // ----------------------------

    /**
     * Liefert alle Clients einer Site.
     *
     * @param siteId technische Site-ID
     * @return Liste der Clients der Site
     */
    public List<Clients> findBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    /**
     * Liefert alle Clients ohne Filter.
     *
     * @return vollständige Client-Liste
     */
    public List<Clients> findAll() {
        return repo.findAll();
    }

    /**
     * Sucht einen Client anhand seiner ID.
     *
     * @param id Client-ID
     * @return Optional mit Client oder leer
     */
    public Optional<Clients> findById(UUID id) {
        return repo.findById(id);
    }

    // ----------------------------
    // CREATE
    // ----------------------------

    /**
     * Persistiert einen neuen Client und indexiert ihn nach Commit in Lucene.
     * Validiert Pflichtfelder wie Site, Name und Installationsart.
     *
     * @param in neuer Client
     * @return gespeicherter Client inklusive ID
     */
    @Transactional
    public Clients create(Clients in) {
        Objects.requireNonNull(in, "client payload must not be null");
        if (in.getSiteID() == null) throw new IllegalArgumentException("siteID is required");
        if (isBlank(in.getClientName())) throw new IllegalArgumentException("clientName is required");
        if (isBlank(in.getInstallType())) throw new IllegalArgumentException("installType is required (LOCAL/BROWSER)");

        // persist (JdbcClientsRepository setzt die UUID via RETURNING)
        repo.save(in);

        // nach Commit in Lucene indexieren
        registerAfterCommitIndexing(in);

        log.info("Client gespeichert: id={} name='{}'", in.getClientID(), in.getClientName());
        return in;
    }

    // ----------------------------
    // UPDATE
    // ----------------------------

    /**
     * Aktualisiert einen bestehenden Client und synchronisiert den Index.
     *
     * @param id    Client-ID
     * @param patch Änderungswerte
     * @return aktualisierter Client
     */
    @Transactional
    public Clients update(UUID id, Clients patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        var existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("client not found"));

        existing.setSiteID(nvl(patch.getSiteID(), existing.getSiteID()));
        existing.setClientName(nvl(patch.getClientName(), existing.getClientName()));
        existing.setClientBrand(nvl(patch.getClientBrand(), existing.getClientBrand()));
        existing.setClientSerialNr(nvl(patch.getClientSerialNr(), existing.getClientSerialNr()));
        existing.setClientOS(nvl(patch.getClientOS(), existing.getClientOS()));
        existing.setPatchLevel(nvl(patch.getPatchLevel(), existing.getPatchLevel()));
        existing.setInstallType(nvl(patch.getInstallType(), existing.getInstallType()));

        repo.save(existing);
        registerAfterCommitIndexing(existing);

        log.info("Client aktualisiert: id={} name='{}'", id, existing.getClientName());
        return existing;
    }

// ----------------------------
// DELETE
//WÄRE EIGENTLICH WICHTIG, ABER UNSER LUCENE UNTERSTÜTZ DAS NOCH NICHT
//DA ES NOCH NICHT IM CONTROLLER INTEGRIERT IST
// ----------------------------
/*
    @Transactional
    public boolean delete(UUID id) {
        Objects.requireNonNull(id, "id must not be null");

        var existing = repo.findById(id);
        if (existing.isEmpty()) {
            return false;
        }

        repo.deleteById(id);
        log.info("Client gelöscht: id={}", id);

        // optional: Lucene-Eintrag löschen, falls dein LuceneService das unterstützt
        try {
            lucene.deleteClient(id.toString());
        } catch (Exception e) {
            log.warn("Lucene-Löschung für Client {} fehlgeschlagen", id, e);
        }

        return true;
    }
 */
    // ----------------------------
    // Lucene Indexing Helpers
    // ----------------------------

    private void registerAfterCommitIndexing(Clients c) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(c);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { indexToLucene(c); }
        });
    }

    private void indexToLucene(Clients c) {
        try {
            lucene.indexClient(
                    c.getClientID() != null ? c.getClientID().toString() : null,
                    c.getSiteID() != null ? c.getSiteID().toString() : null,
                    c.getClientName(),
                    c.getClientBrand(),
                    c.getClientOS(),
                    c.getInstallType()
            );
            log.debug("Client in Lucene indexiert: id={}", c.getClientID());
        } catch (Exception e) {
            // Indexierung darf DB nicht kippen
            log.error("Lucene-Indexing für Client {} fehlgeschlagen", c.getClientID(), e);
        }
    }

    // ----------------------------
    // Utility
    // ----------------------------

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static <T> T nvl(T in, T fallback) { return in != null ? in : fallback; }
}
