package at.htlle.freq.web;

import at.htlle.freq.application.DeploymentVariantService;
import at.htlle.freq.domain.DeploymentVariant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for {@link DeploymentVariant} records.
 *
 * <p>Uses {@link DeploymentVariantService} for business logic and persistence.</p>
 */
@RestController
@RequestMapping("/deployment-variants")
public class DeploymentVariantController {

    private final DeploymentVariantService service;

    public DeploymentVariantController(DeploymentVariantService service) {
        this.service = service;
    }

    /**
     * Lists all deployment variants.
     *
     * <p>Path: {@code GET /deployment-variants}</p>
     *
     * @return 200 OK with a list of {@link DeploymentVariant}.
     */
    @GetMapping
    public List<DeploymentVariant> list() {
        return service.getAllVariants();
    }

    /**
     * Returns a variant by ID.
     *
     * <p>Path: {@code GET /deployment-variants/{id}}</p>
     *
     * @param id variant ID as UUID.
     * @return 200 OK with the variant or 404 if the ID is unknown.
     */
    @GetMapping("/{id}")
    public DeploymentVariant byId(@PathVariable UUID id) {
        return service.getVariantById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DeploymentVariant not found"));
    }

    /**
     * Creates or updates a variant.
     *
     * <p>Path: {@code POST /deployment-variants}</p>
     * <p>Request body: JSON representation of a {@link DeploymentVariant}.</p>
     *
     * @param payload variant including identifier.
     * @return 201 Created with the stored variant.
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
