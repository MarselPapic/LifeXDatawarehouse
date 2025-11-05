package at.htlle.freq.web;

import at.htlle.freq.application.DeploymentVariantService;
import at.htlle.freq.domain.DeploymentVariant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * REST-Controller für {@link DeploymentVariant} Datensätze.
 *
 * <p>Verwendet den {@link DeploymentVariantService} für Geschäftslogik und Persistenz.</p>
 */
@RestController
@RequestMapping("/deployment-variants")
public class DeploymentVariantController {

    private final DeploymentVariantService service;

    public DeploymentVariantController(DeploymentVariantService service) {
        this.service = service;
    }

    /**
     * Listet alle Deployment-Varianten.
     *
     * <p>Pfad: {@code GET /deployment-variants}</p>
     *
     * @return 200 OK mit einer Liste von {@link DeploymentVariant}.
     */
    @GetMapping
    public List<DeploymentVariant> list() {
        return service.getAllVariants();
    }

    /**
     * Liefert eine Variante anhand der ID.
     *
     * <p>Pfad: {@code GET /deployment-variants/{id}}</p>
     *
     * @param id Variant-ID als UUID.
     * @return 200 OK mit der Variante oder 404 bei unbekannter ID.
     */
    @GetMapping("/{id}")
    public DeploymentVariant byId(@PathVariable UUID id) {
        return service.getVariantById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DeploymentVariant not found"));
    }

    /**
     * Legt eine Variante an bzw. aktualisiert sie.
     *
     * <p>Pfad: {@code POST /deployment-variants}</p>
     * <p>Request-Body: JSON-Repräsentation einer {@link DeploymentVariant}.</p>
     *
     * @param payload Variante inkl. Identifier.
     * @return 201 Created mit der gespeicherten Variante.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentVariant create(@RequestBody DeploymentVariant payload) {
        try {
            return service.createOrUpdateVariant(payload);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
