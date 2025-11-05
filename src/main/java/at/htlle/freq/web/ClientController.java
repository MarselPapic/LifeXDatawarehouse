// src/main/java/at/htlle/freq/web/ClientController.java
package at.htlle.freq.web;

import at.htlle.freq.application.ClientsService;
import at.htlle.freq.domain.Clients;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST-Controller für {@link Clients} einer Site.
 *
 * <p>Verwendet den {@link ClientsService} zur Persistenz.</p>
 */
@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientsService service;

    public ClientController(ClientsService service) {
        this.service = service;
    }

    /**
     * Listet Clients optional gefiltert nach Site.
     *
     * <p>Pfad: {@code GET /clients}</p>
     * <p>Query-Parameter: {@code siteId} (optional, UUID) für die Filterung.</p>
     *
     * @param siteId optionale Site-ID.
     * @return 200 OK mit einer Liste von {@link Clients}.
     */
    @GetMapping
    public List<Clients> findBySite(@RequestParam(required = false) UUID siteId) {
        if (siteId == null) {
            return service.findAll();
        }
        return service.findBySite(siteId);
    }

    /**
     * Legt einen neuen Client an.
     *
     * <p>Pfad: {@code POST /clients}</p>
     * <p>Request-Body: JSON-Repräsentation eines {@link Clients}.</p>
     *
     * @param client Client-Payload.
     * @return 200 OK mit dem gespeicherten Datensatz oder entsprechende Fehlercodes (400/500).
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Clients client) {
        try {
            Clients saved = service.create(client);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body("Create client failed");
        }
    }
}
