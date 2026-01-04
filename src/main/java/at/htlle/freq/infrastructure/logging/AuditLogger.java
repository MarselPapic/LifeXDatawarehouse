package at.htlle.freq.infrastructure.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger("at.htlle.freq.audit");
    private final ObjectMapper objectMapper;

    public AuditLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void created(String entity, Map<String, Object> identifiers, Object data) {
        logChange("CREATE", entity, identifiers, data);
    }

    public void updated(String entity, Map<String, Object> identifiers, Object data) {
        logChange("UPDATE", entity, identifiers, data);
    }

    public void deleted(String entity, Map<String, Object> identifiers) {
        logChange("DELETE", entity, identifiers, null);
    }

    public void upserted(String entity, Map<String, Object> identifiers, Object data) {
        logChange("UPSERT", entity, identifiers, data);
    }

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
