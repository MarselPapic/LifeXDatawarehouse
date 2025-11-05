package at.htlle.freq.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

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
    private GenericCrudController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        controller = new GenericCrudController(jdbc);
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
        // Arrange: the row exists for the first call
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(Map.of("AccountID", "1")));
        // Act
        Map<String, Object> row = controller.row("account", "1");
        // Assert
        assertEquals("1", row.get("AccountID"));

        // Arrange: the row is missing so the controller must throw
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        // Act + Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.row("account", "1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void insertBuildsInsertStatementAndDelegates() {
        controller.insert("account", Map.of("AccountName", "Acme", "Country", "AT"));
        verify(jdbc).update(startsWith("INSERT INTO Account"), any(MapSqlParameterSource.class));
    }

    @Test
    void insertRejectsEmptyBody() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.insert("account", Map.of()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateBuildsUpdateStatementAndChecksAffectedRows() {
        // Arrange: pretend the update affects one row
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        // Act
        controller.update("account", "1", Map.of("AccountName", "Acme"));
        // Assert
        verify(jdbc).update(startsWith("UPDATE Account"), any(MapSqlParameterSource.class));

        // Arrange: emulate an update miss
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        // Act + Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update("account", "1", Map.of("AccountName", "Acme")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteBuildsDeleteStatementAndChecksAffectedRows() {
        // Arrange
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        // Act
        controller.delete("account", "1");
        // Assert
        verify(jdbc).update(startsWith("DELETE FROM Account"), any(MapSqlParameterSource.class));

        // Arrange: emulate a delete miss
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        // Act + Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.delete("account", "1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
