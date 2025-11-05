package at.htlle.freq.web;

import at.htlle.freq.domain.ProjectLifecycleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Vollständiger CRUD-Controller für Project
 * (wird im Frontend unter /projects verwendet)
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final NamedParameterJdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);
    private static final String TABLE = "Project";

    public ProjectController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------
    // READ: Alle Projekte oder nach Account filtern
    // ----------------------------

    @GetMapping
    public List<Map<String, Object>> findByAccount(@RequestParam(required = false) String accountId) {
        if (accountId != null) {
            return jdbc.queryForList("""
                SELECT ProjectID, ProjectName, DeploymentVariantID, BundleType, AccountID, AddressID, LifecycleStatus, CreateDateTime
                FROM Project
                WHERE AccountID = :accId
                """, new MapSqlParameterSource("accId", accountId));
        }
        return jdbc.queryForList("""
            SELECT ProjectID, ProjectName, DeploymentVariantID, BundleType, AccountID, AddressID, LifecycleStatus, CreateDateTime
            FROM Project
            """, new HashMap<>());
    }

    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT ProjectID, ProjectName, DeploymentVariantID, BundleType, AccountID, AddressID, LifecycleStatus, CreateDateTime
            FROM Project
            WHERE ProjectID = :id
            """, new MapSqlParameterSource("id", id));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
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
            INSERT INTO Project (ProjectSAPID, ProjectName, DeploymentVariantID, BundleType, CreateDateTime, LifecycleStatus, AccountID, AddressID)
            VALUES (:projectSAPID, :projectName, :deploymentVariantID, :bundleType, CURRENT_DATE, :lifecycleStatus, :accountID, :addressID)
            """;

        Object statusRaw = Optional.ofNullable(body.get("lifecycleStatus"))
                .orElse(Optional.ofNullable(body.get("LifecycleStatus"))
                        .orElse(body.get("lifecycle_status")));

        ProjectLifecycleStatus status;
        if (statusRaw == null) {
            status = ProjectLifecycleStatus.ACTIVE;
        } else {
            try {
                status = ProjectLifecycleStatus.fromString(statusRaw.toString());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        body.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (!"lifecyclestatus".equals(normalized) && !"lifecycle_status".equals(normalized)) {
                params.addValue(key, value);
            }
        });
        params.addValue("lifecycleStatus", status.name());

        jdbc.update(sql, params);
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

        StringBuilder sql = new StringBuilder("UPDATE Project SET ");
        List<String> sets = new ArrayList<>();
        for (String key : body.keySet()) {
            sets.add(key + " = :" + key);
        }
        sql.append(String.join(", ", sets)).append(" WHERE ProjectID = :id");

        MapSqlParameterSource params = new MapSqlParameterSource(body).addValue("id", id);
        int updated = jdbc.update(sql.toString(), params);

        if (updated == 0) {
            log.warn("[{}] update failed: identifiers={}, payloadKeys={}", TABLE, Map.of("ProjectID", id), body.keySet());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no project updated");
        }
        log.info("[{}] update succeeded: identifiers={}, keys={}", TABLE, Map.of("ProjectID", id), body.keySet());
    }

    // ----------------------------
    // DELETE
    // ----------------------------

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        int count = jdbc.update("DELETE FROM Project WHERE ProjectID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            log.warn("[{}] delete failed: identifiers={}", TABLE, Map.of("ProjectID", id));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no project deleted");
        }
        log.info("[{}] delete succeeded: identifiers={}", TABLE, Map.of("ProjectID", id));
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
