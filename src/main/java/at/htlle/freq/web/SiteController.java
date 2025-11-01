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
 * Vollständiger CRUD-Controller für Site
 * Wird im Frontend über /sites angesprochen.
 */
@RestController
@RequestMapping("/sites")
public class SiteController {

    private final NamedParameterJdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(SiteController.class);
    private static final String TABLE = "Site";

    public SiteController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------
    // READ: Alle Sites oder nach Project filtern
    // ----------------------------

    @GetMapping
    public List<Map<String, Object>> findByProject(@RequestParam(required = false) String projectId) {
        if (projectId != null) {
            return jdbc.queryForList("""
                SELECT SiteID, SiteName, FireZone, TenantCount, AddressID, ProjectID
                FROM Site
                WHERE ProjectID = :pid
                """, new MapSqlParameterSource("pid", projectId));
        }

        return jdbc.queryForList("""
            SELECT SiteID, SiteName, FireZone, TenantCount, AddressID, ProjectID
            FROM Site
            """, new HashMap<>());
    }

    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT SiteID, SiteName, FireZone, TenantCount, AddressID, ProjectID
            FROM Site
            WHERE SiteID = :id
            """, new MapSqlParameterSource("id", id));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found");
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
            INSERT INTO Site (SiteName, ProjectID, AddressID, FireZone, TenantCount)
            VALUES (:siteName, :projectID, :addressID, :fireZone, :tenantCount)
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

        StringBuilder sql = new StringBuilder("UPDATE Site SET ");
        List<String> sets = new ArrayList<>();
        for (String key : body.keySet()) {
            sets.add(key + " = :" + key);
        }
        sql.append(String.join(", ", sets)).append(" WHERE SiteID = :id");

        var params = new MapSqlParameterSource(body).addValue("id", id);
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            log.warn("[{}] update failed: identifiers={}, payloadKeys={}", TABLE, Map.of("SiteID", id), body.keySet());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no site updated");
        }
        log.info("[{}] update succeeded: identifiers={}, keys={}", TABLE, Map.of("SiteID", id), body.keySet());
    }

    // ----------------------------
    // DELETE
    // ----------------------------

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        int count = jdbc.update("DELETE FROM Site WHERE SiteID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            log.warn("[{}] delete failed: identifiers={}", TABLE, Map.of("SiteID", id));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no site deleted");
        }
        log.info("[{}] delete succeeded: identifiers={}", TABLE, Map.of("SiteID", id));
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
