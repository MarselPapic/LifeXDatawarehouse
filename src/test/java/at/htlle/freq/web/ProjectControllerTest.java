package at.htlle.freq.web;

import at.htlle.freq.application.ProjectSiteAssignmentService;
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
import static org.mockito.Mockito.*;

class ProjectControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private ProjectSiteAssignmentService projectSites;
    private AuditLogger audit;
    private ProjectController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        projectSites = mock(ProjectSiteAssignmentService.class);
        audit = mock(AuditLogger.class);
        controller = new ProjectController(jdbc, projectSites, audit);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(0);
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

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), paramsCaptor.capture(), any(), any(String[].class));

        assertEquals("ACTIVE", paramsCaptor.getValue().getValue("LifecycleStatus"));
    }

    @Test
    void createRejectsDuplicateSapId() {
        Map<String, Object> body = baseBody();
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(body));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class), any(), any(String[].class));
    }

    @Test
    void createRejectsMissingSapId() {
        Map<String, Object> body = baseBody();
        body.remove("projectSAPID");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(body));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class), any(), any(String[].class));
    }

    @Test
    void createPassesThroughSpecialNotes() {
        Map<String, Object> body = baseBody();
        body.put("specialNotes", "  On-site radio cabling  ");

        controller.create(body);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), paramsCaptor.capture(), any(), any(String[].class));

        assertEquals("On-site radio cabling", paramsCaptor.getValue().getValue("SpecialNotes"));
    }

    @Test
    void createUsesProvidedLifecycleStatus() {
        Map<String, Object> body = baseBody();
        body.put("LifecycleStatus", "eol");

        controller.create(body);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), paramsCaptor.capture(), any(), any(String[].class));

        assertEquals("EOL", paramsCaptor.getValue().getValue("LifecycleStatus"));
    }

    @Test
    void createTreatsBlankLifecycleStatusAsDefault() {
        Map<String, Object> body = baseBody();
        body.put("lifecycleStatus", "   ");

        controller.create(body);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), paramsCaptor.capture(), any(), any(String[].class));

        assertEquals("ACTIVE", paramsCaptor.getValue().getValue("LifecycleStatus"));
    }

    @Test
    void updateNormalizesLifecycleStatusKeys() {
        Map<String, Object> body = new HashMap<>();
        body.put("lifecycle_status", "maintenance");

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        String projectId = java.util.UUID.randomUUID().toString();
        controller.update(projectId, body);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("LifecycleStatus = :LifecycleStatus"));
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertEquals("MAINTENANCE", params.getValue("LifecycleStatus"));
        assertFalse(params.hasValue("lifecycle_status"));
        assertEquals(java.util.UUID.fromString(projectId), params.getValue("id"));
    }

    @Test
    void updateRejectsInvalidLifecycleStatus() {
        Map<String, Object> body = new HashMap<>();
        body.put("LifecycleStatus", "invalid");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update(java.util.UUID.randomUUID().toString(), body));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
