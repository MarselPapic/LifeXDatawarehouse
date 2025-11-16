package at.htlle.freq.web;

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
import static org.mockito.Mockito.*;

class ProjectControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private ProjectController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        controller = new ProjectController(jdbc);
    }

    private Map<String, Object> baseBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("projectSAPID", "SAP-01");
        body.put("projectName", "Test Project");
        body.put("deploymentVariantID", "VAR-01");
        body.put("bundleType", "Standard");
        body.put("accountID", "ACC-01");
        body.put("addressID", "ADDR-01");
        return body;
    }

    @Test
    void createDefaultsLifecycleStatusToActive() {
        Map<String, Object> body = baseBody();

        controller.create(body);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains(":lifecycleStatus"));
        assertEquals("ACTIVE", paramsCaptor.getValue().getValue("lifecycleStatus"));
    }

    @Test
    void createUsesProvidedLifecycleStatus() {
        Map<String, Object> body = baseBody();
        body.put("LifecycleStatus", "retired");

        controller.create(body);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), paramsCaptor.capture());

        assertEquals("RETIRED", paramsCaptor.getValue().getValue("lifecycleStatus"));
    }

    @Test
    void updateNormalizesLifecycleStatusKeys() {
        Map<String, Object> body = new HashMap<>();
        body.put("lifecycle_status", "maintenance");

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        controller.update("P-100", body);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("LifecycleStatus = :lifecycleStatus"));
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertEquals("MAINTENANCE", params.getValue("lifecycleStatus"));
        assertFalse(params.hasValue("lifecycle_status"));
        assertEquals("P-100", params.getValue("id"));
    }

    @Test
    void updateRejectsInvalidLifecycleStatus() {
        Map<String, Object> body = new HashMap<>();
        body.put("LifecycleStatus", "invalid");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.update("P-101", body));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
