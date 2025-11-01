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
 * Vollständiger CRUD-Controller für Radio
 * Wird im Frontend über /radios angesprochen.
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
