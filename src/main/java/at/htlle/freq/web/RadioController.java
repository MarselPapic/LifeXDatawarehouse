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
    private static final Set<String> CREATE_COLUMNS = Set.of(
            "SiteID",
            "AssignedClientID",
            "RadioBrand",
            "RadioSerialNr",
            "Mode",
            "DigitalStandard"
    );
    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "SiteID",
            "Mode"
    );
    private static final Set<String> UPDATE_COLUMNS = CREATE_COLUMNS;

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
            UUID siteUuid = parseUuid(siteId, "siteId");
            return jdbc.queryForList("""
                SELECT RadioID, SiteID, AssignedClientID, RadioBrand, RadioSerialNr, Mode, DigitalStandard
                FROM Radio
                WHERE SiteID = :sid
                """, new MapSqlParameterSource("sid", siteUuid));
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
        UUID radioId = parseUuid(id, "RadioID");
        var rows = jdbc.queryForList("""
            SELECT RadioID, SiteID, AssignedClientID, RadioBrand, RadioSerialNr, Mode, DigitalStandard
            FROM Radio
            WHERE RadioID = :id
            """, new MapSqlParameterSource("id", radioId));

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
        Map<String, Object> filteredBody = requireAllowedKeys(body, CREATE_COLUMNS);
        requireRequiredKeys(filteredBody, REQUIRED_COLUMNS);

        String columns = String.join(", ", filteredBody.keySet());
        String values = ":" + String.join(", :", filteredBody.keySet());
        String sql = "INSERT INTO Radio (" + columns + ") VALUES (" + values + ")";

        jdbc.update(sql, new MapSqlParameterSource(filteredBody));
        audit.created(TABLE, extractIdentifiers(filteredBody), filteredBody);
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
        UUID radioId = parseUuid(id, "RadioID");
        Map<String, Object> filteredBody = requireAllowedKeys(body, UPDATE_COLUMNS);

        List<String> sets = new ArrayList<>();
        for (String key : filteredBody.keySet()) {
            sets.add(key + " = :" + key);
        }

        String sql = "UPDATE Radio SET " + String.join(", ", sets) + " WHERE RadioID = :id";
        var params = new MapSqlParameterSource(filteredBody).addValue("id", radioId);

        int updated = jdbc.update(sql, params);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no radio updated");
        }
        audit.updated(TABLE, Map.of("RadioID", radioId), filteredBody);
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
        UUID radioId = parseUuid(id, "RadioID");
        int count = jdbc.update("DELETE FROM Radio WHERE RadioID = :id",
                new MapSqlParameterSource("id", radioId));

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no radio deleted");
        }
        audit.deleted(TABLE, Map.of("RadioID", radioId));
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

    private Map<String, Object> requireAllowedKeys(Map<String, Object> body, Set<String> allowed) {
        if (body == null || body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        Map<String, String> allowedLookup = new HashMap<>();
        for (String column : allowed) {
            allowedLookup.put(column.toLowerCase(Locale.ROOT), column);
        }
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid column: null");
            }
            String canonical = allowedLookup.get(key.toLowerCase(Locale.ROOT));
            if (canonical == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid column: " + key);
            }
            filtered.put(canonical, entry.getValue());
        }
        if (filtered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }
        return filtered;
    }

    private void requireRequiredKeys(Map<String, Object> body, Set<String> required) {
        for (String key : required) {
            Object value = body.get(key);
            if (value == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
            }
            if (value instanceof String str && str.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
            }
        }
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " must be a valid UUID", ex);
        }
    }
}
