package at.htlle.freq.infrastructure.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Writes structured audit log entries for successful and failed data mutations.
 */
@Component
public class AuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger("at.htlle.freq.audit");
    private final ObjectMapper objectMapper;

    /**
     * Creates the audit logger with the JSON serializer used for payload snapshots.
     *
     * @param objectMapper serializer used for structured log payloads.
     */
    public AuditLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Logs a successful create operation.
     *
     * @param entity audited entity name.
     * @param identifiers entity identifiers for correlation.
     * @param data payload written to the log entry.
     */
    public void created(String entity, Map<String, Object> identifiers, Object data) {
        logChange("CREATE", entity, identifiers, data);
    }

    /**
     * Logs a successful update operation.
     *
     * @param entity audited entity name.
     * @param identifiers entity identifiers for correlation.
     * @param data payload written to the log entry.
     */
    public void updated(String entity, Map<String, Object> identifiers, Object data) {
        logChange("UPDATE", entity, identifiers, data);
    }

    /**
     * Logs a successful delete operation.
     *
     * @param entity audited entity name.
     * @param identifiers entity identifiers for correlation.
     */
    public void deleted(String entity, Map<String, Object> identifiers) {
        logChange("DELETE", entity, identifiers, null);
    }

    /**
     * Logs a successful upsert operation.
     *
     * @param entity audited entity name.
     * @param identifiers entity identifiers for correlation.
     * @param data payload written to the log entry.
     */
    public void upserted(String entity, Map<String, Object> identifiers, Object data) {
        logChange("UPSERT", entity, identifiers, data);
    }

    /**
     * Logs a successful archive operation.
     *
     * @param entity audited entity name.
     * @param identifiers entity identifiers for correlation.
     * @param data payload written to the log entry.
     */
    public void archived(String entity, Map<String, Object> identifiers, Object data) {
        logChange("ARCHIVE", entity, identifiers, data);
    }

    /**
     * Logs a successful restore operation.
     *
     * @param entity audited entity name.
     * @param identifiers entity identifiers for correlation.
     * @param data payload written to the log entry.
     */
    public void restored(String entity, Map<String, Object> identifiers, Object data) {
        logChange("RESTORE", entity, identifiers, data);
    }

    /**
     * Logs a failed write-side operation.
     *
     * @param action attempted action such as {@code CREATE} or {@code UPDATE}.
     * @param entity audited entity name.
     * @param identifiers entity identifiers for correlation.
     * @param reason failure reason written to the log entry.
     * @param data payload associated with the failed operation.
     */
    public void failed(String action, String entity, Map<String, Object> identifiers, String reason, Object data) {
        LOG.warn("action={} entity={} identifiers={} result=FAIL reason={} data={}",
                safe(action), safe(entity), safeMap(identifiers), reason, toJsonSafe(data));
    }

    private void logChange(String action, String entity, Map<String, Object> identifiers, Object data) {
        LOG.info("action={} entity={} identifiers={} result=OK data={}",
                safe(action), safe(entity), safeMap(identifiers), toJsonSafe(data));
    }

    private String toJsonSafe(Object value) {
        if (value == null) {
            return "(null)";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String safe(String value) {
        return value == null ? "(null)" : value;
    }

    private Map<String, Object> safeMap(Map<String, Object> identifiers) {
        return identifiers == null ? Map.of() : identifiers;
    }
}
