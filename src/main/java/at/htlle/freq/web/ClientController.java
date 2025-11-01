// src/main/java/at/htlle/freq/web/ClientController.java
package at.htlle.freq.web;

import at.htlle.freq.application.ClientsService;
import at.htlle.freq.domain.Clients;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Clients (WorkingPosition) zu einer Site. */
@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientsService service;

    public ClientController(ClientsService service) {
        this.service = service;
    }

    // GET /clients?siteId={uuid}
    @GetMapping
    public List<Clients> findBySite(@RequestParam(required = false) UUID siteId) {
        if (siteId == null) {
            return service.findAll();
        }
        return service.findBySite(siteId);
    }

    // POST /clients
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
