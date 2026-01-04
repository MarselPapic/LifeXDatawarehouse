package at.htlle.freq.web;

import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Fully featured CRUD controller for radios.
 *
 * <p>Operates directly on the database schema via {@link NamedParameterJdbcTemplate}.</p>
 */
@RestController
@RequestMapping("/radios")
public class RadioController {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogger audit;
    private static final String TABLE = "Radio";

    /**
     * Creates a controller backed by a {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc JDBC template used for radio queries.
     */
    public RadioController(NamedParameterJdbcTemplate jdbc, AuditLogger audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // READ operations

    /**
     * Lists radios, optionally filtered by site.
     *
     * <p>Path: {@code GET /radios}</p>
     * <p>Optional {@code siteId} query parameter narrows the result to a site.</p>
     *
     * @param siteId optional site ID.
     * @return 200 OK with a JSON list of radios.
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
     * Returns a radio by ID.
     *
     * <p>Path: {@code GET /radios/{id}}</p>
     *
     * @param id primary key.
     * @return 200 OK with the field values or 404 if the ID is unknown.
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

    // CREATE operations

    /**
     * Creates a radio.
     *
     * <p>Path: {@code POST /radios}</p>
     * <p>Request body: JSON with columns such as {@code siteID} or {@code radioBrand}.</p>
     *
     * @param body input payload.
     * @throws ResponseStatusException 400 if the body is empty.
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
        audit.created(TABLE, extractIdentifiers(body), body);
    }

    // UPDATE operations

    /**
     * Updates a radio.
     *
     * <p>Path: {@code PUT /radios/{id}}</p>
     * <p>Request body: JSON object with the columns to update.</p>
     *
     * @param id   primary key.
     * @param body field values.
     * @throws ResponseStatusException 400 if the body is empty, 404 if nothing was updated.
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no radio updated");
        }
        audit.updated(TABLE, Map.of("RadioID", id), body);
    }

    // DELETE operations

    /**
     * Deletes a radio.
     *
     * <p>Path: {@code DELETE /radios/{id}}</p>
     *
     * @param id primary key value.
     * @throws ResponseStatusException 404 if no row was deleted.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        int count = jdbc.update("DELETE FROM Radio WHERE RadioID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no radio deleted");
        }
        audit.deleted(TABLE, Map.of("RadioID", id));
    }

    /**
     * Extracts identifier-like entries from the payload for logging.
     *
     * @param body request payload.
     * @return key/value pairs whose names end with {@code id} (case-insensitive).
     */
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
