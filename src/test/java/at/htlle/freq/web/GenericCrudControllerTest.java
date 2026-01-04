package at.htlle.freq.web;

import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the {@link GenericCrudController} using a mocked {@link NamedParameterJdbcTemplate}
 * to verify SQL normalization, validation and error handling of the CRUD endpoints.
 */
class GenericCrudControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private AuditLogger audit;
    private GenericCrudController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        audit = mock(AuditLogger.class);
        controller = new GenericCrudController(jdbc, audit);
    }

    @Test
    void listNormalisesTableNamesAndLimitsRows() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of("AccountID", "1")));
        List<Map<String, Object>> rows = controller.list(" ACCOUNT ", 9999);
        assertEquals(1, rows.size());
        verify(jdbc).queryForList(contains("FROM Account"), anyMap());
    }

    @Test
    void listRejectsUnknownTables() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.list("unknown", 10));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void rowFetchesByPrimaryKeyAndHandlesNotFound() {
        // Arrange: configure the JDBC template to return a single row for the first lookup
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(Map.of("AccountID", "1")));
        // Act: load the record through the controller
        Map<String, Object> row = controller.row("account", "1");
        // Assert: confirm the fetched row matches the stubbed response
        assertEquals("1", row.get("AccountID"));

        // Arrange: simulate a missing row on the next lookup so the controller must fail
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        // Act & Assert: expect the controller to convert the miss into a 404 status
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.row("account", "1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void insertBuildsInsertStatementAndDelegates() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("AccountName", "Acme");
        body.put("Country", "AT");

        controller.insert("account", body);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertEquals("INSERT INTO Account (AccountName, Country) VALUES (:AccountName, :Country)", sqlCaptor.getValue());
        assertEquals(body, paramsCaptor.getValue().getValues());
    }

    @Test
    void insertRejectsEmptyBody() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.insert("account", Map.of()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateBuildsUpdateStatementAndChecksAffectedRows() {
        // Arrange: stub the update to report a single affected row
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("AccountName", "Acme");
        controller.update("account", "1", body);
        // Assert: verify the controller forwards a proper UPDATE statement to JDBC
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());
        assertEquals("UPDATE Account SET AccountName = :AccountName WHERE AccountID = :id", sqlCaptor.getValue());
        assertEquals("Acme", paramsCaptor.getValue().getValue("AccountName"));

        // Arrange: now force the update to report zero affected rows
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        // Act & Assert: ensure a 404 is propagated when nothing was updated
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update("account", "1", Map.of("AccountName", "Acme")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteBuildsDeleteStatementAndChecksAffectedRows() {
        // Arrange: allow the delete to acknowledge one removed row
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        // Act: trigger the delete endpoint
        controller.delete("account", "1");
        // Assert: confirm the delete statement was issued once
        verify(jdbc).update(startsWith("DELETE FROM Account"), any(MapSqlParameterSource.class));

        // Arrange: mimic a delete that affects zero rows
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        // Act & Assert: verify the controller responds with a 404 when nothing was deleted
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.delete("account", "1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void insertRejectsDisallowedColumnNames() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.insert("account", Map.of("InvalidColumn", "value")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void insertRejectsColumnNameWithIllegalCharacters() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.insert("account", Map.of("AccountName;DROP", "value")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void updateRejectsDisallowedColumnNamesBeforeIssuingSql() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update("account", "1", Map.of("DROP_TABLE", "value")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(jdbc, never()).update(startsWith("UPDATE Account"), any(MapSqlParameterSource.class));
    }
}
