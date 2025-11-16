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
 * Fully featured CRUD controller for projects.
 *
 * <p>Data access is performed through {@link NamedParameterJdbcTemplate}.</p>
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

    // READ operations: list all projects or filter by account

    /**
     * Lists projects and optionally filters by account.
     *
     * <p>Path: {@code GET /projects}</p>
     * <p>Optional {@code accountId} query parameter narrows the result to an account.</p>
     *
     * @param accountId optional account foreign key.
     * @return 200 OK with project rows as JSON.
     */
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

    /**
     * Returns a single project.
     *
     * <p>Path: {@code GET /projects/{id}}</p>
     *
     * @param id project ID.
     * @return 200 OK with column values or 404 if the ID is unknown.
     */
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

    // CREATE operations

    /**
     * Creates a new project.
     *
     * <p>Path: {@code POST /projects}</p>
     * <p>Request body: JSON with project data such as {@code projectName} or {@code accountID}.</p>
     *
     * @param body input payload.
     * @throws ResponseStatusException 400 if the body is empty or the lifecycle status is invalid.
     */
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

    // UPDATE operations

    /**
     * Updates a project.
     *
     * <p>Path: {@code PUT /projects/{id}}</p>
     * <p>Request body: JSON with the columns to update.</p>
     *
     * @param id   project ID.
     * @param body field values.
     * @throws ResponseStatusException 400 if the body is empty, 404 if nothing was updated.
     */
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        boolean lifecycleKeyPresent = false;
        Object statusRaw = null;
        for (String candidate : List.of("lifecycleStatus", "LifecycleStatus", "lifecycle_status")) {
            if (body.containsKey(candidate)) {
                lifecycleKeyPresent = true;
                statusRaw = body.get(candidate);
                break;
            }
        }

        ProjectLifecycleStatus status = null;
        if (lifecycleKeyPresent) {
            if (statusRaw == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lifecycle status must not be null");
            }
            try {
                status = ProjectLifecycleStatus.fromString(statusRaw.toString());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
            if (status == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lifecycle status must not be blank");
            }
        }

        StringBuilder sql = new StringBuilder("UPDATE Project SET ");
        List<String> sets = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
        body.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (!"lifecyclestatus".equals(normalized) && !"lifecycle_status".equals(normalized)) {
                sets.add(key + " = :" + key);
                params.addValue(key, value);
            }
        });

        if (status != null) {
            sets.add("LifecycleStatus = :lifecycleStatus");
            params.addValue("lifecycleStatus", status.name());
        }

        if (sets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        sql.append(String.join(", ", sets)).append(" WHERE ProjectID = :id");
        int updated = jdbc.update(sql.toString(), params);

        if (updated == 0) {
            log.warn("[{}] update failed: identifiers={}, payloadKeys={}", TABLE, Map.of("ProjectID", id), body.keySet());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no project updated");
        }
        log.info("[{}] update succeeded: identifiers={}, keys={}", TABLE, Map.of("ProjectID", id), body.keySet());
    }

    // DELETE operations

    /**
     * Deletes a project.
     *
     * <p>Path: {@code DELETE /projects/{id}}</p>
     *
     * @param id project ID.
     * @throws ResponseStatusException 404 if no row was deleted.
     */
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
