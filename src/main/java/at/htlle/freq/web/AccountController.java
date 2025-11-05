package at.htlle.freq.web;

import at.htlle.freq.application.AccountService;
import at.htlle.freq.domain.Account;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST-Endpunkte für {@link Account} Stammdaten.
 *
 * <p>Delegiert alle Operationen an den {@link AccountService}, der die
 * Geschäftslogik und Persistenzkapselung übernimmt.</p>
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Liefert alle Accounts.
     *
     * <p>Pfad: {@code GET /accounts}</p>
     *
     * @return 200 OK mit einer JSON-Liste der Accounts.
     */
    @GetMapping
    public List<Account> findAll() {
        return accountService.getAllAccounts();
    }

    /**
     * Liefert einen Account anhand seiner ID.
     *
     * <p>Pfad: {@code GET /accounts/{id}}</p>
     *
     * @param id Account-ID als UUID Path-Variable.
     * @return 200 OK mit dem Account oder 404, falls nicht vorhanden.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Account> findById(@PathVariable UUID id) {
        Optional<Account> acc = accountService.getAccountById(id);
        return acc.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Legt einen neuen Account an.
     *
     * <p>Pfad: {@code POST /accounts}</p>
     * <p>Request-Body: JSON-Repräsentation eines {@link Account} inklusive {@code contactName}.</p>
     *
     * @param account Account-Payload.
     * @return 200 OK mit dem gespeicherten Account-Datensatz.
     */
    @PostMapping
    public Account create(@RequestBody Account account) {
        // Hinweis: Account enthält ContactName → AccountService.createAccount(Account) setzt alles korrekt.
        return accountService.createAccount(account);
    }
}
