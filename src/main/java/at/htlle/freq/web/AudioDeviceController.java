package at.htlle.freq.web;

import at.htlle.freq.application.ArchiveService;
import at.htlle.freq.domain.ArchiveState;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Fully featured CRUD controller for audio devices (peripherals).
 *
 * <p>Data is read and mutated directly via {@link NamedParameterJdbcTemplate}.</p>
 */
@RestController
@RequestMapping("/audio")
public class AudioDeviceController {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogger audit;
    private final ArchiveService archiveService;
    private static final String TABLE = "AudioDevice";
    private static final Set<String> CREATE_COLUMNS = Set.of(
            "ClientID",
            "AudioDeviceBrand",
            "DeviceSerialNr",
            "AudioDeviceFirmware",
            "DeviceType",
            "Direction"
    );
    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "ClientID",
            "DeviceType",
            "Direction"
    );
    private static final Set<String> UPDATE_COLUMNS = CREATE_COLUMNS;
    private static final Set<String> ALLOWED_DEVICE_TYPES = Set.of("HEADSET", "SPEAKER", "MIC");
    private static final Map<String, String> ALLOWED_DIRECTIONS = Map.of(
            "input", "Input",
            "output", "Output",
            "input + output", "Input + Output",
            "input+output", "Input + Output"
    );

    /**
     * Creates a controller backed by a {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc JDBC template used for audio device queries.
     */
    @Autowired
    public AudioDeviceController(NamedParameterJdbcTemplate jdbc, AuditLogger audit, ArchiveService archiveService) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.archiveService = archiveService;
    }

    /**
     * Backwards-compatible constructor for tests that do not provide an {@link ArchiveService}.
     */
    public AudioDeviceController(NamedParameterJdbcTemplate jdbc, AuditLogger audit) {
        this(jdbc, audit, null);
    }

    // READ operations: list all devices or filter by client
    /**
     * Lists audio devices and optionally filters by client.
     *
     * <p>Path: {@code GET /audio}</p>
     * <p>Optional {@code clientId} query parameter narrows the result to a client.</p>
     *
     * @param clientId optional client ID.
     * @return 200 OK with a JSON list of device representations.
     */
    @GetMapping
    public List<Map<String, Object>> findByClient(@RequestParam(required = false) String clientId,
                                                  @RequestParam(required = false, name = "archiveState") String archiveStateRaw) {
        ArchiveState archiveState = parseArchiveState(archiveStateRaw);
        if (clientId != null) {
            UUID clientUuid = parseUuid(clientId, "clientId");
            return jdbc.queryForList("""
                SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr,
                       AudioDeviceFirmware, DeviceType, Direction
                FROM AudioDevice
                WHERE ClientID = :cid
                  AND (:archived = 'ALL'
                       OR (:archived = 'ACTIVE' AND IsArchived = FALSE)
                       OR (:archived = 'ARCHIVED' AND IsArchived = TRUE))
                """, new MapSqlParameterSource("cid", clientUuid)
                    .addValue("archived", archiveState.name()));
        }

        return jdbc.queryForList("""
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr,
                   AudioDeviceFirmware, DeviceType, Direction
            FROM AudioDevice
            WHERE (:archived = 'ALL'
                   OR (:archived = 'ACTIVE' AND IsArchived = FALSE)
                   OR (:archived = 'ARCHIVED' AND IsArchived = TRUE))
            """, new MapSqlParameterSource("archived", archiveState.name()));
    }

    /**
     * Backwards-compatible overload without archive-state parameter.
     */
    public List<Map<String, Object>> findByClient(String clientId) {
        return findByClient(clientId, null);
    }

    /**
     * Retrieves an audio device by its ID.
     *
     * <p>Path: {@code GET /audio/{id}}</p>
     *
     * @param id primary key of the row.
     * @return 200 OK with a map of column values or 404 if the ID is unknown.
     */
    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        UUID deviceId = parseUuid(id, "AudioDeviceID");
        var rows = jdbc.queryForList("""
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr,
                   AudioDeviceFirmware, DeviceType, Direction
            FROM AudioDevice
            WHERE AudioDeviceID = :id
            """, new MapSqlParameterSource("id", deviceId));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AudioDevice not found");
        }
        return rows.get(0);
    }

    // CREATE operations
    /**
     * Creates an audio device.
     *
     * <p>Path: {@code POST /audio}</p>
     * <p>Request body: JSON object with column fields such as {@code clientID} or {@code audioDeviceBrand}.</p>
     *
     * @param body input payload.
     * @throws ResponseStatusException 400 if the body is empty or the device type is invalid.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody Map<String, Object> body) {
        try {
            normalizeDeviceType(body);
            Optional<String> direction = normalizeDirection(body);
            if (direction.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direction is required");
            }

            Map<String, Object> filteredBody = requireAllowedKeys(body, CREATE_COLUMNS);
            requireRequiredKeys(filteredBody, REQUIRED_COLUMNS);

            String columns = String.join(", ", filteredBody.keySet());
            String values = ":" + String.join(", :", filteredBody.keySet());
            String sql = "INSERT INTO AudioDevice (" + columns + ") VALUES (" + values + ")";

            jdbc.update(sql, new MapSqlParameterSource(filteredBody));
            audit.created(TABLE, extractIdentifiers(filteredBody), filteredBody);
        } catch (ResponseStatusException ex) {
            Map<String, Object> identifiers = body == null ? Map.of() : extractIdentifiers(body);
            audit.failed("CREATE", TABLE, identifiers, ex.getReason(), body);
            throw ex;
        } catch (RuntimeException ex) {
            Map<String, Object> identifiers = body == null ? Map.of() : extractIdentifiers(body);
            audit.failed("CREATE", TABLE, identifiers, ex.getMessage(), body);
            throw ex;
        }
    }

    // UPDATE operations
    /**
     * Updates an audio device.
     *
     * <p>Path: {@code PUT /audio/{id}}</p>
     * <p>Request body: JSON object with the columns to update.</p>
     *
     * @param id   primary key.
     * @param body field values for the update.
     * @throws ResponseStatusException 400 if the body is empty, 404 if nothing is updated.
     */
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            UUID deviceId = parseUuid(id, "AudioDeviceID");

            normalizeDeviceType(body);
            normalizeDirection(body);

            Map<String, Object> filteredBody = requireAllowedKeys(body, UPDATE_COLUMNS);

            var setClauses = new ArrayList<String>();
            for (String key : filteredBody.keySet()) {
                setClauses.add(key + " = :" + key);
            }

            String sql = "UPDATE AudioDevice SET " + String.join(", ", setClauses) +
                    " WHERE AudioDeviceID = :id";

            var params = new MapSqlParameterSource(filteredBody).addValue("id", deviceId);
            int updated = jdbc.update(sql, params);

            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no audio device updated");
            }
            audit.updated(TABLE, Map.of("AudioDeviceID", deviceId), filteredBody);
        } catch (ResponseStatusException ex) {
            audit.failed("UPDATE", TABLE, Map.of("AudioDeviceID", id), ex.getReason(), body);
            throw ex;
        } catch (RuntimeException ex) {
            audit.failed("UPDATE", TABLE, Map.of("AudioDeviceID", id), ex.getMessage(), body);
            throw ex;
        }
    }

    // DELETE operations
    /**
     * Deletes an audio device.
     *
     * <p>Path: {@code DELETE /audio/{id}}</p>
     *
     * @param id primary key.
     * @throws ResponseStatusException 404 if no row was deleted.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        try {
            parseUuid(id, "AudioDeviceID");
            String actor = currentActor();
            boolean archived;
            if (archiveService != null) {
                archived = archiveService.archive("audiodevice", id, actor);
            } else {
                archived = jdbc.update("""
                        UPDATE AudioDevice
                           SET IsArchived = TRUE,
                               ArchivedAt = CURRENT_TIMESTAMP,
                               ArchivedBy = :actor
                         WHERE AudioDeviceID = :id
                           AND IsArchived = FALSE
                        """, new MapSqlParameterSource("id", parseUuid(id, "AudioDeviceID"))
                        .addValue("actor", actor)) > 0;
            }
            if (!archived) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no audio device archived");
            }
            audit.archived(TABLE, Map.of("AudioDeviceID", id), Map.of("actor", actor));
            audit.deleted(TABLE, Map.of("AudioDeviceID", id));
        } catch (ResponseStatusException ex) {
            audit.failed("DELETE", TABLE, Map.of("AudioDeviceID", id), ex.getReason(), null);
            throw ex;
        } catch (RuntimeException ex) {
            audit.failed("DELETE", TABLE, Map.of("AudioDeviceID", id), ex.getMessage(), null);
            throw ex;
        }
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

    private Map<String, Object> requireAllowedKeys(Map<String, Object> body, Set<String> allowed) {
        if (body == null || body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }
        Map<String, String> allowedLookup = new HashMap<>();
        for (String column : allowed) {
            allowedLookup.put(column.toLowerCase(Locale.ROOT), column);
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid column: null");
            }
            String canonical = allowedLookup.get(key.toLowerCase(Locale.ROOT));
            if (canonical == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid column: " + key);
            }
            filtered.put(canonical, entry.getValue());
        }
        if (filtered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }
        return filtered;
    }

    private void requireRequiredKeys(Map<String, Object> body, Set<String> required) {
        for (String key : required) {
            Object value = body.get(key);
            if (value == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
            }
            if (value instanceof String str && str.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
            }
        }
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " must be a valid UUID", ex);
        }
    }

    /**
     * Normalizes the {@code DeviceType} value to the expected canonical form.
     *
     * @param body request payload (mutated in place).
     * @return optional normalized value when provided.
     */
    private Optional<String> normalizeDeviceType(Map<String, Object> body) {
        String detectedKey = findKeyIgnoreCase(body, "DeviceType");
        Object rawValue = detectedKey == null ? null : body.get(detectedKey);

        if (detectedKey == null) {
            return Optional.empty();
        }

        if (rawValue == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DeviceType must be one of HEADSET, SPEAKER, MIC");
        }

        String normalized = rawValue.toString().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_DEVICE_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "DeviceType must be one of HEADSET, SPEAKER, MIC");
        }

        body.remove(detectedKey);
        body.put("DeviceType", normalized);
        return Optional.of(normalized);
    }

    /**
     * Normalizes the {@code Direction} value to the expected canonical form.
     *
     * @param body request payload (mutated in place).
     * @return optional normalized value when provided.
     */
    private Optional<String> normalizeDirection(Map<String, Object> body) {
        String detectedKey = findKeyIgnoreCase(body, "Direction");
        Object rawValue = detectedKey == null ? null : body.get(detectedKey);

        if (detectedKey == null) {
            return Optional.empty();
        }

        if (rawValue == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Direction must be one of Input, Output, Input + Output");
        }

        String trimmed = rawValue.toString().trim();
        String collapsed = trimmed.replaceAll("\\s*\\+\\s*", " + ").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normalized = ALLOWED_DIRECTIONS.get(collapsed);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Direction must be one of Input, Output, Input + Output");
        }

        body.remove(detectedKey);
        body.put("Direction", normalized);
        return Optional.of(normalized);
    }

    private String findKeyIgnoreCase(Map<String, Object> body, String expected) {
        for (String key : body.keySet()) {
            if (key != null && key.equalsIgnoreCase(expected)) {
                return key;
            }
        }
        return null;
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }

    private ArchiveState parseArchiveState(String raw) {
        try {
            return ArchiveState.from(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
