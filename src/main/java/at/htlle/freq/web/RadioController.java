package at.htlle.freq.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Vollständiger CRUD-Controller für Radios.
 *
 * <p>Operiert direkt auf dem Datenbank-Schema via {@link NamedParameterJdbcTemplate}.</p>
 */
@RestController
@RequestMapping("/radios")
public class RadioController {

    private final NamedParameterJdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(RadioController.class);
    private static final String TABLE = "Radio";

    public RadioController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------
    // READ
    // ----------------------------

    /**
     * Listet Radios optional nach Site gefiltert.
     *
     * <p>Pfad: {@code GET /radios}</p>
     * <p>Query-Parameter: {@code siteId} (optional).</p>
     *
     * @param siteId optionale Site-ID.
     * @return 200 OK mit einer JSON-Liste von Radios.
     */
    @GetMapping
    public List<Map<String, Object>> findBySite(@RequestParam(required = false) String siteId) {
        if (siteId != null) {
            return jdbc.queryForList("""
                SELECT RadioID, SiteID, AssignedClientID, RadioBrand, RadioSerialNr, Mode, DigitalStandard
                FROM Radio
                WHERE SiteID = :sid
                """, new MapSqlParameterSource("sid", siteId));
        }
        return jdbc.queryForList("""
            SELECT RadioID, SiteID, AssignedClientID, RadioBrand, RadioSerialNr, Mode, DigitalStandard
            FROM Radio
            """, new HashMap<>());
    }

    /**
     * Liefert ein Radio anhand der ID.
     *
     * <p>Pfad: {@code GET /radios/{id}}</p>
     *
     * @param id Primärschlüssel.
     * @return 200 OK mit den Feldwerten oder 404 bei unbekannter ID.
     */
    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT RadioID, SiteID, AssignedClientID, RadioBrand, RadioSerialNr, Mode, DigitalStandard
            FROM Radio
            WHERE RadioID = :id
            """, new MapSqlParameterSource("id", id));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Radio not found");
        }
        return rows.get(0);
    }

    // ----------------------------
    // CREATE
    // ----------------------------

    /**
     * Legt ein Radio an.
     *
     * <p>Pfad: {@code POST /radios}</p>
     * <p>Request-Body: JSON mit Spalten wie {@code siteID}, {@code radioBrand}.</p>
     *
     * @param body Eingabedaten.
     * @throws ResponseStatusException 400 bei leerem Body.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody Map<String, Object> body) {
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        String sql = """
            INSERT INTO Radio (SiteID, AssignedClientID, RadioBrand, RadioSerialNr, Mode, DigitalStandard)
            VALUES (:siteID, :assignedClientID, :radioBrand, :radioSerialNr, :mode, :digitalStandard)
            """;

        jdbc.update(sql, new MapSqlParameterSource(body));
        log.info("[{}] create succeeded: identifiers={}, keys={}", TABLE, extractIdentifiers(body), body.keySet());
    }

    // ----------------------------
    // UPDATE
    // ----------------------------

    /**
     * Aktualisiert ein Radio.
     *
     * <p>Pfad: {@code PUT /radios/{id}}</p>
     * <p>Request-Body: JSON-Objekt mit zu setzenden Spalten.</p>
     *
     * @param id   Primärschlüssel.
     * @param body Feldwerte.
     * @throws ResponseStatusException 400 bei leerem Body, 404 wenn nichts aktualisiert wurde.
     */
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        List<String> sets = new ArrayList<>();
        for (String key : body.keySet()) {
            sets.add(key + " = :" + key);
        }

        String sql = "UPDATE Radio SET " + String.join(", ", sets) + " WHERE RadioID = :id";
        var params = new MapSqlParameterSource(body).addValue("id", id);

        int updated = jdbc.update(sql, params);
        if (updated == 0) {
            log.warn("[{}] update failed: identifiers={}, payloadKeys={}", TABLE, Map.of("RadioID", id), body.keySet());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no radio updated");
        }
        log.info("[{}] update succeeded: identifiers={}, keys={}", TABLE, Map.of("RadioID", id), body.keySet());
    }

    // ----------------------------
    // DELETE
    // ----------------------------

    /**
     * Löscht ein Radio.
     *
     * <p>Pfad: {@code DELETE /radios/{id}}</p>
     *
     * @param id Primärschlüsselwert.
     * @throws ResponseStatusException 404, wenn kein Datensatz gelöscht wurde.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        int count = jdbc.update("DELETE FROM Radio WHERE RadioID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            log.warn("[{}] delete failed: identifiers={}", TABLE, Map.of("RadioID", id));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no radio deleted");
        }
        log.info("[{}] delete succeeded: identifiers={}", TABLE, Map.of("RadioID", id));
    }

    private Map<String, Object> extractIdentifiers(Map<String, Object> body) {
        Map<String, Object> ids = new LinkedHashMap<>();
        body.forEach((key, value) -> {
            if (key != null && key.toLowerCase(Locale.ROOT).endsWith("id")) {
                ids.put(key, value);
            }
        });
        return ids;
    }
}
