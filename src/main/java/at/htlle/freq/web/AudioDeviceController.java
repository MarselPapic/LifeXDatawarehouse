package at.htlle.freq.web;

import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private static final String TABLE = "AudioDevice";
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
    public AudioDeviceController(NamedParameterJdbcTemplate jdbc, AuditLogger audit) {
        this.jdbc = jdbc;
        this.audit = audit;
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
    public List<Map<String, Object>> findByClient(@RequestParam(required = false) String clientId) {
        if (clientId != null) {
            return jdbc.queryForList("""
                SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr,
                       AudioDeviceFirmware, DeviceType, Direction
                FROM AudioDevice
                WHERE ClientID = :cid
                """, new MapSqlParameterSource("cid", clientId));
        }

        return jdbc.queryForList("""
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr,
                   AudioDeviceFirmware, DeviceType, Direction
            FROM AudioDevice
            """, new HashMap<>());
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
        var rows = jdbc.queryForList("""
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr,
                   AudioDeviceFirmware, DeviceType, Direction
            FROM AudioDevice
            WHERE AudioDeviceID = :id
            """, new MapSqlParameterSource("id", id));

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
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        normalizeDeviceType(body).ifPresent(value -> body.put("deviceType", value));
        Optional<String> direction = normalizeDirection(body);
        direction.ifPresent(value -> body.put("direction", value));
        if (direction.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direction is required");
        }

        String sql = """
            INSERT INTO AudioDevice
            (ClientID, AudioDeviceBrand, DeviceSerialNr, AudioDeviceFirmware, DeviceType, Direction)
            VALUES (:clientID, :audioDeviceBrand, :deviceSerialNr, :audioDeviceFirmware, :deviceType, :direction)
            """;

        jdbc.update(sql, new MapSqlParameterSource(body));
        audit.created(TABLE, extractIdentifiers(body), body);
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
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        normalizeDeviceType(body);
        normalizeDirection(body);

        var setClauses = new ArrayList<String>();
        for (String key : body.keySet()) {
            setClauses.add(key + " = :" + key);
        }

        String sql = "UPDATE AudioDevice SET " + String.join(", ", setClauses) +
                " WHERE AudioDeviceID = :id";

        var params = new MapSqlParameterSource(body).addValue("id", id);
        int updated = jdbc.update(sql, params);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no audio device updated");
        }
        audit.updated(TABLE, Map.of("AudioDeviceID", id), body);
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
        int count = jdbc.update("DELETE FROM AudioDevice WHERE AudioDeviceID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no audio device deleted");
        }
        audit.deleted(TABLE, Map.of("AudioDeviceID", id));
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
     * Normalizes the {@code DeviceType} value to the expected canonical form.
     *
     * @param body request payload (mutated in place).
     * @return optional normalized value when provided.
     */
    private Optional<String> normalizeDeviceType(Map<String, Object> body) {
        String[] keys = {"DeviceType", "deviceType"};
        String detectedKey = null;
        Object rawValue = null;

        for (String key : keys) {
            if (body.containsKey(key)) {
                detectedKey = key;
                rawValue = body.get(key);
                break;
            }
        }

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

        for (String key : keys) {
            if (body.containsKey(key)) {
                body.put(key, normalized);
            }
        }
        return Optional.of(normalized);
    }

    /**
     * Normalizes the {@code Direction} value to the expected canonical form.
     *
     * @param body request payload (mutated in place).
     * @return optional normalized value when provided.
     */
    private Optional<String> normalizeDirection(Map<String, Object> body) {
        String[] keys = {"Direction", "direction"};
        String detectedKey = null;
        Object rawValue = null;

        for (String key : keys) {
            if (body.containsKey(key)) {
                detectedKey = key;
                rawValue = body.get(key);
                break;
            }
        }

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

        for (String key : keys) {
            if (body.containsKey(key)) {
                body.put(key, normalized);
            }
        }
        return Optional.of(normalized);
    }
}
