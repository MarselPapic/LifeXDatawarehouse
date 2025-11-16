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
 * Fully featured CRUD controller for client phone integrations.
 *
 * <p>All access goes through {@link NamedParameterJdbcTemplate}.</p>
 */
@RestController
@RequestMapping("/phones")
public class PhoneController {

    private final NamedParameterJdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(PhoneController.class);
    private static final String TABLE = "PhoneIntegration";
    private static final Set<String> UPDATE_WHITELIST = Set.of(
            "ClientID",
            "PhoneType",
            "PhoneBrand",
            "PhoneSerialNr",
            "PhoneFirmware"
    );

    public PhoneController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // READ operations: list all integrations or filter by client
    /**
     * Lists phone integrations and optionally filters by client.
     *
     * <p>Path: {@code GET /phones}</p>
     * <p>Optional {@code clientId} query parameter narrows the result to a client.</p>
     *
     * @param clientId optional client ID.
     * @return 200 OK with a JSON list of phone integrations.
     */
    @GetMapping
    public List<Map<String, Object>> findByClient(@RequestParam(required = false) String clientId) {
        if (clientId != null) {
            return jdbc.queryForList("""
                SELECT PhoneIntegrationID, ClientID, PhoneType, PhoneBrand, 
                       PhoneSerialNr, PhoneFirmware
                FROM PhoneIntegration
                WHERE ClientID = :cid
                """, new MapSqlParameterSource("cid", clientId));
        }

        return jdbc.queryForList("""
            SELECT PhoneIntegrationID, ClientID, PhoneType, PhoneBrand, 
                   PhoneSerialNr, PhoneFirmware
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
        var rows = jdbc.queryForList("""
            SELECT PhoneIntegrationID, ClientID, PhoneType, PhoneBrand, 
                   PhoneSerialNr, PhoneFirmware
            FROM PhoneIntegration
            WHERE PhoneIntegrationID = :id
            """, new MapSqlParameterSource("id", id));

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
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        String sql = """
            INSERT INTO PhoneIntegration
            (ClientID, PhoneType, PhoneBrand, PhoneSerialNr, PhoneFirmware)
            VALUES (:clientID, :phoneType, :phoneBrand, :phoneSerialNr, :phoneFirmware)
            """;

        jdbc.update(sql, new MapSqlParameterSource(body));
        log.info("[{}] create succeeded: identifiers={}, keys={}", TABLE, extractIdentifiers(body), body.keySet());
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
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        Map<String, Object> filteredBody = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (!UPDATE_WHITELIST.contains(key)) {
                log.warn("[{}] update rejected due to invalid column: {}", TABLE, key);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid column: " + key);
            }
            filteredBody.put(key, entry.getValue());
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

        var params = new MapSqlParameterSource(filteredBody).addValue("id", id);
        int updated = jdbc.update(sql, params);

        if (updated == 0) {
            log.warn("[{}] update failed: identifiers={}, payloadKeys={}", TABLE, Map.of("PhoneIntegrationID", id), filteredBody.keySet());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no phone integration updated");
        }
        log.info("[{}] update succeeded: identifiers={}, keys={}", TABLE, Map.of("PhoneIntegrationID", id), filteredBody.keySet());
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
        int count = jdbc.update("DELETE FROM PhoneIntegration WHERE PhoneIntegrationID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            log.warn("[{}] delete failed: identifiers={}", TABLE, Map.of("PhoneIntegrationID", id));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no phone integration deleted");
        }
        log.info("[{}] delete succeeded: identifiers={}", TABLE, Map.of("PhoneIntegrationID", id));
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
