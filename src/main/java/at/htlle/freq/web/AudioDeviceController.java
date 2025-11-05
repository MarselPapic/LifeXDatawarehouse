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
 * Vollständiger CRUD-Controller für AudioDevices (Audio-Peripherie).
 *
 * <p>Die Daten werden direkt über den {@link NamedParameterJdbcTemplate} abgefragt
 * und manipuliert.</p>
 */
@RestController
@RequestMapping("/audio")
public class AudioDeviceController {

    private final NamedParameterJdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(AudioDeviceController.class);
    private static final String TABLE = "AudioDevice";
    private static final Set<String> ALLOWED_DEVICE_TYPES = Set.of("HEADSET", "SPEAKER", "MIC");

    public AudioDeviceController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------
    // READ: Alle oder nach Client filtern
    // ----------------------------
    /**
     * Listet Audio-Geräte auf und kann optional nach Client filtern.
     *
     * <p>Pfad: {@code GET /audio}</p>
     * <p>Query-Parameter: {@code clientId} (optional, String) für die Filterung nach Client.</p>
     *
     * @param clientId optionale Client-ID.
     * @return 200 OK mit einer JSON-Liste der Geräterepräsentationen.
     */
    @GetMapping
    public List<Map<String, Object>> findByClient(@RequestParam(required = false) String clientId) {
        if (clientId != null) {
            return jdbc.queryForList("""
                SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr,
                       AudioDeviceFirmware, DeviceType
                FROM AudioDevice
                WHERE ClientID = :cid
                """, new MapSqlParameterSource("cid", clientId));
        }

        return jdbc.queryForList("""
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr, 
                   AudioDeviceFirmware, DeviceType
            FROM AudioDevice
            """, new HashMap<>());
    }

    /**
     * Holt ein Audio-Gerät anhand der ID.
     *
     * <p>Pfad: {@code GET /audio/{id}}</p>
     *
     * @param id Primärschlüssel der Zeile.
     * @return 200 OK mit einer Map der Spaltenwerte oder 404 bei unbekannter ID.
     */
    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr, 
                   AudioDeviceFirmware, DeviceType
            FROM AudioDevice
            WHERE AudioDeviceID = :id
            """, new MapSqlParameterSource("id", id));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AudioDevice not found");
        }
        return rows.get(0);
    }

    // ----------------------------
    // CREATE
    // ----------------------------
    /**
     * Legt ein Audio-Gerät an.
     *
     * <p>Pfad: {@code POST /audio}</p>
     * <p>Request-Body: JSON-Objekt mit Spaltenfeldern (z.B. {@code clientID}, {@code audioDeviceBrand}).</p>
     *
     * @param body Eingabedaten.
     * @throws ResponseStatusException 400 bei leerem Body oder ungültigem DeviceType.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody Map<String, Object> body) {
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        normalizeDeviceType(body).ifPresent(value -> body.put("deviceType", value));

        String sql = """
            INSERT INTO AudioDevice
            (ClientID, AudioDeviceBrand, DeviceSerialNr, AudioDeviceFirmware, DeviceType)
            VALUES (:clientID, :audioDeviceBrand, :deviceSerialNr, :audioDeviceFirmware, :deviceType)
            """;

        jdbc.update(sql, new MapSqlParameterSource(body));
        log.info("[{}] create succeeded: identifiers={}, keys={}", TABLE, extractIdentifiers(body), body.keySet());
    }

    // ----------------------------
    // UPDATE
    // ----------------------------
    /**
     * Aktualisiert ein Audio-Gerät.
     *
     * <p>Pfad: {@code PUT /audio/{id}}</p>
     * <p>Request-Body: JSON-Objekt mit zu überschreibenden Spaltenwerten.</p>
     *
     * @param id   Primärschlüssel.
     * @param body Feldwerte für das Update.
     * @throws ResponseStatusException 400 bei leerem Body, 404 wenn nichts aktualisiert wird.
     */
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        normalizeDeviceType(body);

        var setClauses = new ArrayList<String>();
        for (String key : body.keySet()) {
            setClauses.add(key + " = :" + key);
        }

        String sql = "UPDATE AudioDevice SET " + String.join(", ", setClauses) +
                " WHERE AudioDeviceID = :id";

        var params = new MapSqlParameterSource(body).addValue("id", id);
        int updated = jdbc.update(sql, params);

        if (updated == 0) {
            log.warn("[{}] update failed: identifiers={}, payloadKeys={}", TABLE, Map.of("AudioDeviceID", id), body.keySet());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no audio device updated");
        }
        log.info("[{}] update succeeded: identifiers={}, keys={}", TABLE, Map.of("AudioDeviceID", id), body.keySet());
    }

    // ----------------------------
    // DELETE
    // ----------------------------
    /**
     * Löscht ein Audio-Gerät.
     *
     * <p>Pfad: {@code DELETE /audio/{id}}</p>
     *
     * @param id Primärschlüssel.
     * @throws ResponseStatusException 404, wenn keine Zeile gelöscht wurde.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        int count = jdbc.update("DELETE FROM AudioDevice WHERE AudioDeviceID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            log.warn("[{}] delete failed: identifiers={}", TABLE, Map.of("AudioDeviceID", id));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no audio device deleted");
        }
        log.info("[{}] delete succeeded: identifiers={}", TABLE, Map.of("AudioDeviceID", id));
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
}
