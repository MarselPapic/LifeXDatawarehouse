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
 * Vollständiger CRUD-Controller für Sites.
 *
 * <p>Nutzen des {@link NamedParameterJdbcTemplate} für Datenbankoperationen.</p>
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

    /**
     * Listet Sites optional gefiltert nach Projekt.
     *
     * <p>Pfad: {@code GET /sites}</p>
     * <p>Query-Parameter: {@code projectId} (optional).</p>
     *
     * @param projectId optionaler Projekt-FK.
     * @return 200 OK mit Sites als JSON.
     */
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

    /**
     * Liefert eine Site anhand der ID.
     *
     * <p>Pfad: {@code GET /sites/{id}}</p>
     *
     * @param id Site-ID.
     * @return 200 OK mit Feldwerten oder 404 bei unbekannter ID.
     */
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

    /**
     * Legt eine Site an.
     *
     * <p>Pfad: {@code POST /sites}</p>
     * <p>Request-Body: JSON mit Feldern wie {@code siteName}, {@code projectID}.</p>
     *
     * @param body Eingabedaten.
     * @throws ResponseStatusException 400 bei leerem Body.
     */
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

    /**
     * Aktualisiert eine Site.
     *
     * <p>Pfad: {@code PUT /sites/{id}}</p>
     * <p>Request-Body: JSON-Objekt mit zu setzenden Spalten.</p>
     *
     * @param id   Site-ID.
     * @param body Feldwerte.
     * @throws ResponseStatusException 400 bei leerem Body, 404 wenn nichts aktualisiert wurde.
     */
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

    /**
     * Löscht eine Site.
     *
     * <p>Pfad: {@code DELETE /sites/{id}}</p>
     *
     * @param id Site-ID.
     * @throws ResponseStatusException 404, wenn kein Datensatz gelöscht wurde.
     */
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
