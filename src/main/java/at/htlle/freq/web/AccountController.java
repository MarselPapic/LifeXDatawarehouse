package at.htlle.freq.web;

import at.htlle.freq.application.AccountService;
import at.htlle.freq.domain.Account;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST endpoints for {@link Account} master data.
 *
 * <p>Delegates all operations to {@link AccountService}, which encapsulates business logic and persistence.</p>
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AuditLogger audit;

    /**
     * Creates a controller that delegates account operations to {@link AccountService}.
     *
     * @param accountService service used for account CRUD operations.
     */
    public AccountController(AccountService accountService, AuditLogger audit) {
        this.accountService = accountService;
        this.audit = audit;
    }

    /**
     * Returns all accounts.
     *
     * <p>Path: {@code GET /accounts}</p>
     *
     * @return 200 OK with a JSON list of accounts.
     */
    @GetMapping
    public List<Account> findAll() {
        return accountService.getAllAccounts();
    }

    /**
     * Returns an account by its ID.
     *
     * <p>Path: {@code GET /accounts/{id}}</p>
     *
     * @param id account ID as UUID path variable.
     * @return 200 OK with the account or 404 if it does not exist.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Account> findById(@PathVariable UUID id) {
        Optional<Account> acc = accountService.getAccountById(id);
        return acc.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new account.
     *
     * <p>Path: {@code POST /accounts}</p>
     * <p>Request body: JSON representation of an {@link Account} including {@code contactName}.</p>
     *
     * @param account account payload.
     * @return 201 Created with the stored account record.
     */
    @PostMapping
    public ResponseEntity<Account> create(@RequestBody Account account) {
        // Account already carries the contactName field; AccountService#createAccount handles it along with the rest.
        Account created = accountService.createAccount(account);
        Map<String, Object> identifiers = new HashMap<>();
        identifiers.put("AccountID", created.getAccountID());
        audit.created("Account", identifiers, created);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getAccountID())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}
