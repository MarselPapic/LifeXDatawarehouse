package at.htlle.freq.web;

import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Fully featured CRUD controller for site phone integrations.
 *
 * <p>All access goes through {@link NamedParameterJdbcTemplate}.</p>
 */
@RestController
@RequestMapping("/phones")
public class PhoneController {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogger audit;
    private static final String TABLE = "PhoneIntegration";
    private static final Set<String> CREATE_COLUMNS = Set.of(
            "SiteID",
            "PhoneType",
            "PhoneBrand",
            "InterfaceName",
            "Capacity",
            "PhoneFirmware"
    );
    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "SiteID",
            "PhoneType"
    );
    private static final Set<String> UPDATE_WHITELIST = CREATE_COLUMNS;

    /**
     * Creates a controller backed by a {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc JDBC template used for phone integration queries.
     */
    public PhoneController(NamedParameterJdbcTemplate jdbc, AuditLogger audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // READ operations: list all integrations or filter by site
    /**
     * Lists phone integrations and optionally filters by site.
     *
     * <p>Path: {@code GET /phones}</p>
     * <p>Optional {@code siteId} query parameter narrows the result to a site.</p>
     *
     * @param siteId optional site ID.
     * @return 200 OK with a JSON list of phone integrations.
     */
    @GetMapping
    public List<Map<String, Object>> findBySite(@RequestParam(required = false) String siteId) {
        if (siteId != null) {
            UUID siteUuid = parseUuid(siteId, "siteId");
            return jdbc.queryForList("""
                SELECT PhoneIntegrationID, SiteID, PhoneType, PhoneBrand,
                       InterfaceName, Capacity, PhoneFirmware
                FROM PhoneIntegration
                WHERE SiteID = :sid
                """, new MapSqlParameterSource("sid", siteUuid));
        }

        return jdbc.queryForList("""
            SELECT PhoneIntegrationID, SiteID, PhoneType, PhoneBrand,
                   InterfaceName, Capacity, PhoneFirmware
            FROM PhoneIntegration
            """, new HashMap<>());
    }

    /**
     * Returns a phone integration by ID.
     *
     * <p>Path: {@code GET /phones/{id}}</p>
     *
     * @param id primary key.
     * @return 200 OK with the field values or 404 if the ID is unknown.
     */
    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        UUID phoneId = parseUuid(id, "PhoneIntegrationID");
        var rows = jdbc.queryForList("""
            SELECT PhoneIntegrationID, SiteID, PhoneType, PhoneBrand,
                   InterfaceName, Capacity, PhoneFirmware
            FROM PhoneIntegration
            WHERE PhoneIntegrationID = :id
            """, new MapSqlParameterSource("id", phoneId));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PhoneIntegration not found");
        }
        return rows.get(0);
    }

    // CREATE operations
    /**
     * Creates a new phone integration.
     *
     * <p>Path: {@code POST /phones}</p>
     * <p>Request body: JSON with column fields such as {@code clientID} or {@code phoneType}.</p>
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
        String sql = "INSERT INTO PhoneIntegration (" + columns + ") VALUES (" + values + ")";

        jdbc.update(sql, new MapSqlParameterSource(filteredBody));
        audit.created(TABLE, extractIdentifiers(filteredBody), filteredBody);
    }

    // UPDATE operations
    /**
     * Updates a phone integration.
     *
     * <p>Path: {@code PUT /phones/{id}}</p>
     * <p>Request body: JSON object with the columns to update.</p>
     *
     * @param id   primary key.
     * @param body field values.
     * @throws ResponseStatusException 400 if the body is empty, 404 if no row was updated.
     */
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        UUID phoneId = parseUuid(id, "PhoneIntegrationID");
        if (body == null || body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        Map<String, String> allowedLookup = new HashMap<>();
        for (String column : UPDATE_WHITELIST) {
            allowedLookup.put(column.toLowerCase(Locale.ROOT), column);
        }

        Map<String, Object> filteredBody = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid column: null");
            }
            String canonical = allowedLookup.get(key.toLowerCase(Locale.ROOT));
            if (canonical == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid column: " + key);
            }
            filteredBody.put(canonical, entry.getValue());
        }

        if (filteredBody.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no valid columns provided");
        }

        var setClauses = new ArrayList<String>();
        for (String key : filteredBody.keySet()) {
            setClauses.add(key + " = :" + key);
        }

        String sql = "UPDATE PhoneIntegration SET " + String.join(", ", setClauses) +
                " WHERE PhoneIntegrationID = :id";

        var params = new MapSqlParameterSource(filteredBody).addValue("id", phoneId);
        int updated = jdbc.update(sql, params);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no phone integration updated");
        }
        audit.updated(TABLE, Map.of("PhoneIntegrationID", phoneId), filteredBody);
    }

    // DELETE operations
    /**
     * Deletes a phone integration.
     *
     * <p>Path: {@code DELETE /phones/{id}}</p>
     *
     * @param id primary key value.
     * @throws ResponseStatusException 404 if no row was deleted.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        UUID phoneId = parseUuid(id, "PhoneIntegrationID");
        int count = jdbc.update("DELETE FROM PhoneIntegration WHERE PhoneIntegrationID = :id",
                new MapSqlParameterSource("id", phoneId));

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no phone integration deleted");
        }
        audit.deleted(TABLE, Map.of("PhoneIntegrationID", phoneId));
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
