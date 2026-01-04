// src/main/java/at/htlle/freq/web/AddressController.java
package at.htlle.freq.web;

import at.htlle.freq.application.AddressService;
import at.htlle.freq.domain.Address;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for {@link Address} records.
 *
 * <p>Uses {@link AddressService} to perform database CRUD operations.</p>
 */
@RestController
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService service;
    private final AuditLogger audit;

    /**
     * Creates a controller that delegates address operations to {@link AddressService}.
     *
     * @param service service used for address CRUD operations.
     */
    public AddressController(AddressService service, AuditLogger audit) {
        this.service = service;
        this.audit = audit;
    }

    // READ operations

    /**
     * Lists all addresses.
     *
     * <p>Path: {@code GET /addresses}</p>
     *
     * @return 200 OK with a JSON list of {@link Address} entries.
     */
    @GetMapping
    public List<Address> list() {
        return service.getAllAddresses();
    }

    /**
     * Returns an address by ID.
     *
     * <p>Path: {@code GET /addresses/{id}}</p>
     *
     * @param id address ID as UUID path variable.
     * @return 200 OK with the address or 404 if it is not found.
     */
    @GetMapping("/{id}")
    public Address byId(@PathVariable UUID id) {
        return service.getAddressById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    // WRITE operations

    /**
     * Creates a new address.
     *
     * <p>Path: {@code POST /addresses}</p>
     * <p>Request body: JSON representation of an {@link Address}.</p>
     *
     * @param payload new address.
     * @return 201 Created with the stored address.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Address create(@RequestBody Address payload) {
        try {
            Address created = service.createAddress(payload);
            Map<String, Object> identifiers = new HashMap<>();
            identifiers.put("AddressID", created.getAddressID());
            audit.created("Address", identifiers, created);
            return created;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    /**
     * Updates an existing address.
     *
     * <p>Path: {@code PUT /addresses/{id}}</p>
     * <p>Request body: partial or full {@link Address} payload.</p>
     *
     * @param id    address ID as UUID path variable.
     * @param patch changes in JSON format.
     * @return 200 OK with the updated address or 404 if the ID is unknown.
     */
    @PutMapping("/{id}")
    public Address update(@PathVariable UUID id, @RequestBody Address patch) {
        Optional<Address> updated = service.updateAddress(id, patch);
        Address result = updated.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        audit.updated("Address", Map.of("AddressID", id), result);
        return result;
    }
}
