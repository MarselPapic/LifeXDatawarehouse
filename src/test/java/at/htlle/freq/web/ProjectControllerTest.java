package at.htlle.freq.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
}
