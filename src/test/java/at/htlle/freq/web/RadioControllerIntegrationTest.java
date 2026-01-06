package at.htlle.freq.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RadioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private UUID siteId;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        siteId = jdbc.queryForObject("SELECT SiteID FROM Site LIMIT 1", new MapSqlParameterSource(), UUID.class);
        clientId = jdbc.queryForObject("SELECT ClientID FROM Clients LIMIT 1", new MapSqlParameterSource(), UUID.class);
    }

    @Test
    @Transactional
    void createPersistsRadio() throws Exception {
        Map<String, Object> payload = Map.of(
                "siteID", siteId,
                "assignedClientID", clientId,
                "radioBrand", "TestBrand",
                "radioSerialNr", "SER-123",
                "mode", "Analog",
                "digitalStandard", "Motorola"
        );

        mockMvc.perform(post("/radios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT SiteID AS site, AssignedClientID AS client, RadioBrand AS brand
                FROM Radio
                WHERE RadioSerialNr = :sn
                """, new MapSqlParameterSource("sn", "SER-123"));

        org.junit.jupiter.api.Assertions.assertEquals(siteId, stored.get("site"));
        org.junit.jupiter.api.Assertions.assertEquals(clientId, stored.get("client"));
        org.junit.jupiter.api.Assertions.assertEquals("TestBrand", stored.get("brand"));
    }

    @Test
    void createRejectsEmptyPayload() throws Exception {
        mockMvc.perform(post("/radios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUnknownRadioReturns404() throws Exception {
        mockMvc.perform(put("/radios/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Mode\":\"Analog\"}"))
                .andExpect(status().isNotFound());
    }
}
