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
            return jdbc.queryForList("""
                SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr,
                       ServerOS, PatchLevel, VirtualPlatform, VirtualVersion
                FROM Server
                WHERE SiteID = :sid
                """, new MapSqlParameterSource("sid", siteId));
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
        var rows = jdbc.queryForList("""
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr,
                   ServerOS, PatchLevel, VirtualPlatform, VirtualVersion
            FROM Server
            WHERE ServerID = :id
            """, new MapSqlParameterSource("id", id));

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
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        String sql = """
            INSERT INTO Server (SiteID, ServerName, ServerBrand, ServerSerialNr,
                                ServerOS, PatchLevel, VirtualPlatform, VirtualVersion)
            VALUES (:siteID, :serverName, :serverBrand, :serverSerialNr,
                    :serverOS, :patchLevel, :virtualPlatform, :virtualVersion)
            """;

        jdbc.update(sql, new MapSqlParameterSource(body));
        audit.created(TABLE, extractIdentifiers(body), body);
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
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        List<String> sets = new ArrayList<>();
        for (String key : body.keySet()) {
            sets.add(key + " = :" + key);
        }

        String sql = "UPDATE Server SET " + String.join(", ", sets) + " WHERE ServerID = :id";
        var params = new MapSqlParameterSource(body).addValue("id", id);

        int updated = jdbc.update(sql, params);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no server updated");
        }
        audit.updated(TABLE, Map.of("ServerID", id), body);
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
        int count = jdbc.update("DELETE FROM Server WHERE ServerID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no server deleted");
        }
        audit.deleted(TABLE, Map.of("ServerID", id));
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
}
