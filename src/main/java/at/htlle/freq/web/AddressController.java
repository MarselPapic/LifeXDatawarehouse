// src/main/java/at/htlle/freq/web/AddressController.java
package at.htlle.freq.web;

import at.htlle.freq.application.AddressService;
import at.htlle.freq.domain.Address;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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

    public AddressController(AddressService service) {
        this.service = service;
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
            return service.createAddress(payload);
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
        return updated.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }
}
