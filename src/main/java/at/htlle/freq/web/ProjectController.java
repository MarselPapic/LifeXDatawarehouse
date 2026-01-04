package at.htlle.freq.web;

import at.htlle.freq.application.ProjectSiteAssignmentService;
import at.htlle.freq.domain.ProjectLifecycleStatus;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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
    private final ProjectSiteAssignmentService projectSites;
    private final AuditLogger audit;
    private static final String TABLE = "Project";

    /**
     * Creates a controller backed by a {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc JDBC template used for project queries.
     * @param projectSites service that maintains project/site assignments.
     */
    public ProjectController(NamedParameterJdbcTemplate jdbc, ProjectSiteAssignmentService projectSites, AuditLogger audit) {
        this.jdbc = jdbc;
        this.projectSites = projectSites;
        this.audit = audit;
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
                SELECT ProjectID, ProjectName, DeploymentVariantID, BundleType, AccountID, AddressID, LifecycleStatus, CreateDateTime, SpecialNotes
                FROM Project
                WHERE AccountID = :accId
                """, new MapSqlParameterSource("accId", accountId));
        }
        return jdbc.queryForList("""
            SELECT ProjectID, ProjectName, DeploymentVariantID, BundleType, AccountID, AddressID, LifecycleStatus, CreateDateTime, SpecialNotes
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
            SELECT ProjectID, ProjectName, DeploymentVariantID, BundleType, AccountID, AddressID, LifecycleStatus, CreateDateTime, SpecialNotes
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

        String sapId = extractProjectSapId(body);
        if (sapId == null || sapId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ProjectSAPID is required");
        }
        sapId = sapId.trim();

        int duplicates = Optional.ofNullable(jdbc.queryForObject(
                        "SELECT COUNT(1) FROM Project WHERE ProjectSAPID = :sap",
                        new MapSqlParameterSource("sap", sapId), Integer.class))
                .orElse(0);
        if (duplicates > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ProjectSAPID already exists: " + sapId);
        }

        String sql = """
            INSERT INTO Project (ProjectSAPID, ProjectName, DeploymentVariantID, BundleType, CreateDateTime, LifecycleStatus, AccountID, AddressID, SpecialNotes)
            VALUES (:projectSAPID, :projectName, :deploymentVariantID, :bundleType, CURRENT_DATE, :lifecycleStatus, :accountID, :addressID, :specialNotes)
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
            if (status == null) {
                status = ProjectLifecycleStatus.ACTIVE;
            }
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        body.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (!"lifecyclestatus".equals(normalized) && !"lifecycle_status".equals(normalized) && !"specialnotes".equals(normalized)) {
                params.addValue(key, value);
            }
        });
        params.addValue("projectSAPID", sapId);
        params.addValue("lifecycleStatus", status.name());
        params.addValue("specialNotes", extractSpecialNotes(body));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"ProjectID"});
        UUID projectId = fetchProjectId(sapId, keyHolder);
        List<UUID> siteIds = extractUuidList(body, "siteIds");
        if (siteIds != null) {
            projectSites.replaceSitesForProject(projectId, siteIds);
        }
        Map<String, Object> identifiers = new LinkedHashMap<>();
        if (projectId != null) {
            identifiers.put("ProjectID", projectId);
        }
        if (sapId != null) {
            identifiers.put("ProjectSAPID", sapId);
        }
        audit.created(TABLE, identifiers, body);
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
                if ("specialnotes".equals(normalized)) {
                    sets.add("SpecialNotes = :specialNotes");
                    params.addValue("specialNotes", normalizeNotes(value));
                } else {
                    sets.add(key + " = :" + key);
                    params.addValue(key, value);
                }
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no project updated");
        }
        List<UUID> siteIds = extractUuidList(body, "siteIds");
        if (siteIds != null) {
            projectSites.replaceSitesForProject(UUID.fromString(id), siteIds);
        }
        audit.updated(TABLE, Map.of("ProjectID", id), body);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no project deleted");
        }
        audit.deleted(TABLE, Map.of("ProjectID", id));
    }

    /**
     * Extracts the project SAP identifier from the incoming payload.
     *
     * @param body request payload.
     * @return SAP ID value or null when missing.
     */
    private String extractProjectSapId(Map<String, Object> body) {
        return body.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase("projectsapid"))
                .map(e -> e.getValue() != null ? e.getValue().toString() : null)
                .findFirst()
                .orElse(null);
    }

    /**
     * Extracts and normalizes the special notes field from the payload.
     *
     * @param body request payload.
     * @return normalized notes or null when missing.
     */
    private String extractSpecialNotes(Map<String, Object> body) {
        return body.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase("specialnotes"))
                .map(Map.Entry::getValue)
                .map(this::normalizeNotes)
                .findFirst()
                .orElse(null);
    }

    /**
     * Normalizes free-form notes to trimmed text or null when empty.
     *
     * @param value input value.
     * @return trimmed notes or null.
     */
    private String normalizeNotes(Object value) {
        if (value == null) return null;
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Extracts identifier-like entries from the payload for logging.
     *
     * @param body request payload.
     * @return key/value pairs whose names end with {@code id} (case-insensitive).
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

    /**
     * Resolves the project ID from the insert result or by querying the SAP identifier.
     *
     * @param sapId SAP identifier used for fallback lookup.
     * @param keyHolder key holder returned by the insert.
     * @return resolved project identifier.
     */
    private UUID fetchProjectId(String sapId, KeyHolder keyHolder) {
        UUID fromKey = extractUuidFromKeyHolder(keyHolder);
        if (fromKey != null) {
            return fromKey;
        }
        return jdbc.queryForObject("SELECT ProjectID FROM Project WHERE ProjectSAPID = :sap",
                new MapSqlParameterSource("sap", sapId), UUID.class);
    }

    /**
     * Extracts a UUID value from the JDBC {@link KeyHolder}.
     *
     * @param keyHolder key holder returned by the insert.
     * @return extracted UUID or null when unavailable.
     */
    private UUID extractUuidFromKeyHolder(KeyHolder keyHolder) {
        if (keyHolder == null) return null;
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            for (Object value : keys.values()) {
                UUID converted = coerceUuid(value);
                if (converted != null) return converted;
            }
        }
        return coerceUuid(keyHolder.getKey());
    }

    /**
     * Coerces a value into a {@link UUID} when possible.
     *
     * @param value raw value from JDBC.
     * @return UUID value or null when conversion fails.
     */
    private UUID coerceUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Extracts a UUID list from the payload when the given key is present.
     *
     * @param body request payload.
     * @param key key that should map to a UUID array.
     * @return list of UUIDs, an empty list when explicitly empty, or null when absent.
     */
    private List<UUID> extractUuidList(Map<String, Object> body, String key) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                Object raw = entry.getValue();
                if (raw == null) return List.of();
                if (raw instanceof Collection<?> collection) {
                    try {
                        return collection.stream()
                                .filter(Objects::nonNull)
                                .map(Object::toString)
                                .map(UUID::fromString)
                                .toList();
                    } catch (IllegalArgumentException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must contain valid UUIDs", ex);
                    }
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be an array");
            }
        }
        return null;
    }
}
