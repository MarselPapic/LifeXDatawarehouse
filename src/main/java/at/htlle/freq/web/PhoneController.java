package at.htlle.freq.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Vollständiger CRUD-Controller für Telefon-Integrationen eines Clients. */
@RestController
@RequestMapping("/phones")
public class PhoneController {

    private final NamedParameterJdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(PhoneController.class);
    private static final String TABLE = "PhoneIntegration";

    public PhoneController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------
    // READ: Alle oder nach Client filtern
    // ----------------------------
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

    // ----------------------------
    // CREATE
    // ----------------------------
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

    // ----------------------------
    // UPDATE
    // ----------------------------
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        var setClauses = new ArrayList<String>();
        for (String key : body.keySet()) {
            setClauses.add(key + " = :" + key);
        }

        String sql = "UPDATE PhoneIntegration SET " + String.join(", ", setClauses) +
                " WHERE PhoneIntegrationID = :id";

        var params = new MapSqlParameterSource(body).addValue("id", id);
        int updated = jdbc.update(sql, params);

        if (updated == 0) {
            log.warn("[{}] update failed: identifiers={}, payloadKeys={}", TABLE, Map.of("PhoneIntegrationID", id), body.keySet());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no phone integration updated");
        }
        log.info("[{}] update succeeded: identifiers={}, keys={}", TABLE, Map.of("PhoneIntegrationID", id), body.keySet());
    }

    // ----------------------------
    // DELETE
    // ----------------------------
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
