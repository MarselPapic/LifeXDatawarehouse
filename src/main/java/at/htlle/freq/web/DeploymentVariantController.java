package at.htlle.freq.web;

import at.htlle.freq.application.DeploymentVariantService;
import at.htlle.freq.domain.DeploymentVariant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/deployment-variants")
public class DeploymentVariantController {

    private final DeploymentVariantService service;

    public DeploymentVariantController(DeploymentVariantService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeploymentVariant> list() {
        return service.getAllVariants();
    }

    @GetMapping("/{id}")
    public DeploymentVariant byId(@PathVariable UUID id) {
        return service.getVariantById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DeploymentVariant not found"));
    }

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
