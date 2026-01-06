package at.htlle.freq.web;

import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AudioDeviceControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private AuditLogger audit;
    private AudioDeviceController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        audit = mock(AuditLogger.class);
        controller = new AudioDeviceController(jdbc, audit);
    }

    @Test
    void createUppercasesDeviceTypeBeforePersisting() {
        Map<String, Object> body = new HashMap<>();
        body.put("clientID", "CLIENT-1");
        body.put("audioDeviceBrand", "Acme");
        body.put("deviceSerialNr", "SER-123");
        body.put("audioDeviceFirmware", "1.0");
        body.put("deviceType", "speaker");
        body.put("direction", "input + output");

        controller.create(body);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), paramsCaptor.capture());
        assertEquals("SPEAKER", paramsCaptor.getValue().getValue("DeviceType"));
        assertEquals("Input + Output", paramsCaptor.getValue().getValue("Direction"));
    }

    @Test
    void createRejectsInvalidDeviceType() {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceType", "unknown");
        body.put("direction", "Input");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(body));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("DeviceType"));
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void createRequiresDirection() {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceType", "HEADSET");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(body));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Direction"));
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void updateUppercasesDeviceType() {
        Map<String, Object> body = new HashMap<>();
        body.put("DeviceType", "mic");

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        String deviceId = java.util.UUID.randomUUID().toString();
        controller.update(deviceId, body);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("DeviceType = :DeviceType"));
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertEquals("MIC", params.getValue("DeviceType"));
        assertEquals(java.util.UUID.fromString(deviceId), params.getValue("id"));
    }

    @Test
    void updateRejectsInvalidDeviceType() {
        Map<String, Object> body = new HashMap<>();
        body.put("DeviceType", "boom");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update(java.util.UUID.randomUUID().toString(), body));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
