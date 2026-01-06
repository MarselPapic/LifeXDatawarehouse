package at.htlle.freq.web;

import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * REST controller for server inventory endpoints.
 *
 * <p>Uses {@link NamedParameterJdbcTemplate} to execute SQL queries directly against the
 * {@code Server} table.</p>
 */
@RestController
@RequestMapping("/servers")
public class ServerController {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogger audit;
    private static final String TABLE = "Server";
    private static final Set<String> CREATE_COLUMNS = Set.of(
            "SiteID",
            "ServerName",
            "ServerBrand",
            "ServerSerialNr",
            "ServerOS",
            "PatchLevel",
            "VirtualPlatform",
            "VirtualVersion"
    );
    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "SiteID",
            "ServerName"
    );
    private static final Set<String> UPDATE_COLUMNS = CREATE_COLUMNS;

    /**
     * Creates a controller backed by the provided JDBC template.
     *
     * @param jdbc JDBC access component for SQL queries.
     */
    public ServerController(NamedParameterJdbcTemplate jdbc, AuditLogger audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // READ operations

    /**
     * Lists servers, optionally filtered by site.
     *
     * <p>Path: {@code GET /servers}</p>
     * <p>Optional {@code siteId} query parameter narrows the result to a site.</p>
     *
     * @param siteId optional site ID.
     * @return 200 OK with server rows as JSON.
     */
    @GetMapping
    public List<Map<String, Object>> findBySite(@RequestParam(required = false) String siteId) {
        if (siteId != null) {
            UUID siteUuid = parseUuid(siteId, "siteId");
            return jdbc.queryForList("""
                SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr,
                       ServerOS, PatchLevel, VirtualPlatform, VirtualVersion
                FROM Server
                WHERE SiteID = :sid
                """, new MapSqlParameterSource("sid", siteUuid));
        }

        return jdbc.queryForList("""
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr,
                   ServerOS, PatchLevel, VirtualPlatform, VirtualVersion
            FROM Server
            """, new HashMap<>());
    }

    /**
     * Returns a server by ID.
     *
     * <p>Path: {@code GET /servers/{id}}</p>
     *
     * @param id server ID.
     * @return 200 OK with the column values or 404 if the ID is unknown.
     */
    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        UUID serverId = parseUuid(id, "ServerID");
        var rows = jdbc.queryForList("""
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr,
                   ServerOS, PatchLevel, VirtualPlatform, VirtualVersion
            FROM Server
            WHERE ServerID = :id
            """, new MapSqlParameterSource("id", serverId));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Server not found");
        }
        return rows.get(0);
    }

    // CREATE operations

    /**
     * Creates a server.
     *
     * <p>Path: {@code POST /servers}</p>
     * <p>Request body: JSON with server fields such as {@code siteID} or {@code serverName}.</p>
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
        String sql = "INSERT INTO Server (" + columns + ") VALUES (" + values + ")";

        jdbc.update(sql, new MapSqlParameterSource(filteredBody));
        audit.created(TABLE, extractIdentifiers(filteredBody), filteredBody);
    }

    // UPDATE operations

    /**
     * Updates a server.
     *
     * <p>Path: {@code PUT /servers/{id}}</p>
     * <p>Request body: JSON object with the columns to update.</p>
     *
     * @param id   server ID.
     * @param body field values.
     * @throws ResponseStatusException 400 if the body is empty, 404 if nothing was updated.
     */
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        UUID serverId = parseUuid(id, "ServerID");
        Map<String, Object> filteredBody = requireAllowedKeys(body, UPDATE_COLUMNS);

        List<String> sets = new ArrayList<>();
        for (String key : filteredBody.keySet()) {
            sets.add(key + " = :" + key);
        }

        String sql = "UPDATE Server SET " + String.join(", ", sets) + " WHERE ServerID = :id";
        var params = new MapSqlParameterSource(filteredBody).addValue("id", serverId);

        int updated = jdbc.update(sql, params);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no server updated");
        }
        audit.updated(TABLE, Map.of("ServerID", serverId), filteredBody);
    }

    // DELETE operations

    /**
     * Deletes a server.
     *
     * <p>Path: {@code DELETE /servers/{id}}</p>
     *
     * @param id server ID.
     * @throws ResponseStatusException 404 if no row was deleted.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        UUID serverId = parseUuid(id, "ServerID");
        int count = jdbc.update("DELETE FROM Server WHERE ServerID = :id",
                new MapSqlParameterSource("id", serverId));

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no server deleted");
        }
        audit.deleted(TABLE, Map.of("ServerID", serverId));
    }

    /**
     * Extracts identifier-like keys from the request payload for logging.
     *
     * @param body raw request payload.
     * @return map of keys ending in {@code id} (case-insensitive).
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
