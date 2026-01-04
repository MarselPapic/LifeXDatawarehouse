// src/main/java/at/htlle/freq/web/ClientController.java
package at.htlle.freq.web;

import at.htlle.freq.application.ClientsService;
import at.htlle.freq.domain.Clients;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for {@link Clients} within a site.
 *
 * <p>Uses {@link ClientsService} for persistence.</p>
 */
@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientsService service;
    private final AuditLogger audit;

    /**
     * Creates a controller that delegates client operations to {@link ClientsService}.
     *
     * @param service service used for client CRUD operations.
     */
    public ClientController(ClientsService service, AuditLogger audit) {
        this.service = service;
        this.audit = audit;
    }

    /**
     * Lists clients, optionally filtered by site.
     *
     * <p>Path: {@code GET /clients}</p>
     * <p>Query parameter: {@code siteId} (optional, UUID) for filtering.</p>
     *
     * @param siteId optional site ID.
     * @return 200 OK with a list of {@link Clients}.
     */
    @GetMapping
    public List<Clients> findBySite(@RequestParam(required = false) UUID siteId) {
        if (siteId == null) {
            return service.findAll();
        }
        return service.findBySite(siteId);
    }

    /**
     * Creates a new client.
     *
     * <p>Path: {@code POST /clients}</p>
     * <p>Request body: JSON representation of a {@link Clients} record validated by the service.</p>
     *
     * @param client client payload.
     * @return HTTP 200 containing the saved entity; HTTP 400/500 with an explanatory error message otherwise.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Clients client) {
        try {
            Clients saved = service.create(client);
            Map<String, Object> identifiers = new HashMap<>();
            identifiers.put("ClientID", saved.getClientID());
            audit.created("Client", identifiers, saved);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body("Create client failed");
        }
    }
}
