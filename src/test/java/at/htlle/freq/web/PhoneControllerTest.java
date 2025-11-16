package at.htlle.freq.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PhoneControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private PhoneController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        controller = new PhoneController(jdbc);
    }

    @Test
    void updateRejectsInvalidColumnName() {
        Map<String, Object> body = Map.of("foo; DROP TABLE", "boom");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.update("PHONE-1", body));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("invalid column"));
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void updateAllowsWhitelistedColumns() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PhoneFirmware", "2.0.1");

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        controller.update("PHONE-2", body);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("PhoneFirmware = :PhoneFirmware"));

        MapSqlParameterSource params = paramsCaptor.getValue();
        assertEquals("2.0.1", params.getValue("PhoneFirmware"));
        assertEquals("PHONE-2", params.getValue("id"));
    }
}
