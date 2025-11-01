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
 * Vollständiger CRUD-Controller für Server
 * Wird vom Frontend über /servers angesprochen.
 */
@RestController
@RequestMapping("/servers")
public class ServerController {

    private final NamedParameterJdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(ServerController.class);
    private static final String TABLE = "Server";

    public ServerController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------
    // READ
    // ----------------------------

    @GetMapping
    public List<Map<String, Object>> findBySite(@RequestParam(required = false) String siteId) {
        if (siteId != null) {
            return jdbc.queryForList("""
                SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr,
                       ServerOS, PatchLevel, VirtualPlatform, VirtualVersion, HighAvailability
                FROM Server
                WHERE SiteID = :sid
                """, new MapSqlParameterSource("sid", siteId));
        }

        return jdbc.queryForList("""
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr,
                   ServerOS, PatchLevel, VirtualPlatform, VirtualVersion, HighAvailability
            FROM Server
            """, new HashMap<>());
    }

    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr,
                   ServerOS, PatchLevel, VirtualPlatform, VirtualVersion, HighAvailability
            FROM Server
            WHERE ServerID = :id
            """, new MapSqlParameterSource("id", id));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Server not found");
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
            INSERT INTO Server (SiteID, ServerName, ServerBrand, ServerSerialNr,
                                ServerOS, PatchLevel, VirtualPlatform, VirtualVersion, HighAvailability)
            VALUES (:siteID, :serverName, :serverBrand, :serverSerialNr,
                    :serverOS, :patchLevel, :virtualPlatform, :virtualVersion, :highAvailability)
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

        List<String> sets = new ArrayList<>();
        for (String key : body.keySet()) {
            sets.add(key + " = :" + key);
        }

        String sql = "UPDATE Server SET " + String.join(", ", sets) + " WHERE ServerID = :id";
        var params = new MapSqlParameterSource(body).addValue("id", id);

        int updated = jdbc.update(sql, params);
        if (updated == 0) {
            log.warn("[{}] update failed: identifiers={}, payloadKeys={}", TABLE, Map.of("ServerID", id), body.keySet());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no server updated");
        }
        log.info("[{}] update succeeded: identifiers={}, keys={}", TABLE, Map.of("ServerID", id), body.keySet());
    }

    // ----------------------------
    // DELETE
    // ----------------------------

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        int count = jdbc.update("DELETE FROM Server WHERE ServerID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            log.warn("[{}] delete failed: identifiers={}", TABLE, Map.of("ServerID", id));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no server deleted");
        }
        log.info("[{}] delete succeeded: identifiers={}", TABLE, Map.of("ServerID", id));
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
