// src/main/java/at/htlle/freq/application/AccountService.java
package at.htlle.freq.application;

import at.htlle.freq.domain.Account;
import at.htlle.freq.domain.AccountRepository;
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
 * Manages accounts, orchestrates database access, and triggers Lucene index synchronization
 * after successful transactions.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with the required repository and Lucene components.
     *
     * @param repo   repository for persistent account access
     * @param lucene service that synchronizes the Lucene index
     */
    public AccountService(AccountRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns every account without additional filtering.
     *
     * @return list of all existing accounts
     */
    public List<Account> getAllAccounts() {
        return repo.findAll();
    }

    /**
     * Looks up an account by its unique identifier.
     *
     * @param id technical primary key
     * @return optional containing the account or empty if it does not exist
     */
    public Optional<Account> getAccountById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Looks up an account by its name.
     *
     * @param name human readable account name
     * @return optional containing the account or empty when no valid name is provided
     */
    public Optional<Account> getAccountByName(String name) {
        if (isBlank(name)) return Optional.empty();
        return repo.findByName(name.trim());
    }

    // ---------- Commands ----------

    /**
     * Persists a new or existing account and triggers Lucene indexing after the transaction commits.
     * Performs mandatory-field validation for the account name.
     *
     * @param incoming account data to persist
     * @return the saved account including the generated ID
     */
    @Transactional
    public Account createAccount(Account incoming) {
        Objects.requireNonNull(incoming, "account payload must not be null");

        // Perform mandatory validation.
        if (isBlank(incoming.getAccountName())) {
            throw new IllegalArgumentException("AccountName is required");
        }

        // Persist the entity; the repository generates a UUID when none is provided.
        Account saved = repo.save(incoming);
        UUID id = saved.getAccountID();

        // Index the record after the commit so Lucene remains aligned with the database.
        registerAfterCommitIndexing(saved);

        log.info("Account saved: id={} name='{}'", id, saved.getAccountName());
        return saved;
    }

    /**
     * Updates an existing account with the provided patch data and synchronizes the Lucene index.
     *
     * @param id    technical primary key
     * @param patch fields that should be merged into the existing object
     * @return optional with the updated account or empty when it cannot be found
     */
    @Transactional
    public Optional<Account> updateAccount(UUID id, Account patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            // Overwrite fields using simple replacement; extend to advanced patch logic if required.
            existing.setAccountName(nvl(patch.getAccountName(), existing.getAccountName()));
            existing.setContactName(nvl(patch.getContactName(), existing.getContactName()));
            existing.setContactEmail(nvl(patch.getContactEmail(), existing.getContactEmail()));
            existing.setContactPhone(nvl(patch.getContactPhone(), existing.getContactPhone()));
            existing.setVatNumber(nvl(patch.getVatNumber(), existing.getVatNumber()));
            existing.setCountry(nvl(patch.getCountry(), existing.getCountry()));

            Account saved = repo.save(existing);
            registerAfterCommitIndexing(saved);
            log.info("Account updated: id={} name='{}'", id, saved.getAccountName());
            return saved;
        });
    }

    /**
     * Permanently removes an account from the database.
     *
     * @param id technical primary key
     */
    @Transactional
    public void deleteAccount(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.deleteById(id);
        registerAfterCommitDeletion(id);
        log.info("Account deleted: id={}", id);
    }

    // ---------- Internals ----------

    private void registerAfterCommitIndexing(Account a) {
        // When no transaction is active, index immediately (useful for tests).
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

    private void registerAfterCommitDeletion(UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteFromLucene(id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteFromLucene(id);
            }
        });
    }

    private void deleteFromLucene(UUID id) {
        if (id == null) {
            return;
        }
        try {
            lucene.deleteDocument(id.toString());
            log.debug("Account removed from Lucene: id={}", id);
        } catch (Exception e) {
            log.error("Lucene delete for Account {} failed", id, e);
        }
    }

    private void indexToLucene(Account a) {
        try {
            lucene.indexAccount(
                    a.getAccountID() != null ? a.getAccountID().toString() : null,
                    a.getAccountName(),
                    a.getCountry(),
                    a.getContactEmail()
            );
            log.debug("Account indexed in Lucene: id={}", a.getAccountID());
        } catch (Exception e) {
            // Indexing failures must not trigger a database rollback.
            log.error("Lucene indexing for Account {} failed", a.getAccountID(), e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String in, String fallback) {
        return in != null ? in : fallback;
    }
}
