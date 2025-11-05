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
 * REST-Controller für {@link Address} Datensätze.
 *
 * <p>Verwendet den {@link AddressService}, um CRUD-Vorgänge auf der
 * Datenbank abzubilden.</p>
 */
@RestController
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    // ---------- READ ----------

    /**
     * Listet alle Adressen auf.
     *
     * <p>Pfad: {@code GET /addresses}</p>
     *
     * @return 200 OK mit einer JSON-Liste aller {@link Address Adressen}.
     */
    @GetMapping
    public List<Address> list() {
        return service.getAllAddresses();
    }

    /**
     * Liefert eine Adresse anhand der ID.
     *
     * <p>Pfad: {@code GET /addresses/{id}}</p>
     *
     * @param id Address-ID als UUID Path-Variable.
     * @return 200 OK mit der Adresse oder 404, wenn nicht gefunden.
     */
    @GetMapping("/{id}")
    public Address byId(@PathVariable UUID id) {
        return service.getAddressById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    // ---------- WRITE ----------

    /**
     * Erzeugt eine neue Adresse.
     *
     * <p>Pfad: {@code POST /addresses}</p>
     * <p>Request-Body: JSON-Repräsentation einer {@link Address}.</p>
     *
     * @param payload neue Adresse.
     * @return 201 Created mit der gespeicherten Adresse.
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
     * Aktualisiert eine bestehende Adresse.
     *
     * <p>Pfad: {@code PUT /addresses/{id}}</p>
     * <p>Request-Body: Teil- oder Voll-Payload einer {@link Address}.</p>
     *
     * @param id    Address-ID als UUID Path-Variable.
     * @param patch Änderungen im JSON-Format.
     * @return 200 OK mit der aktualisierten Adresse oder 404 bei unbekannter ID.
     */
    @PutMapping("/{id}")
    public Address update(@PathVariable UUID id, @RequestBody Address patch) {
        Optional<Address> updated = service.updateAddress(id, patch);
        return updated.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }
}
