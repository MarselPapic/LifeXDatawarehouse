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
class ServerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private UUID siteId;

    @BeforeEach
    void setUp() {
        siteId = jdbc.queryForObject("SELECT SiteID FROM Site LIMIT 1", new MapSqlParameterSource(), UUID.class);
    }

    @Test
    @Transactional
    void createPersistsServer() throws Exception {
        Map<String, Object> payload = Map.of(
                "siteID", siteId,
                "serverName", "TestServer",
                "serverBrand", "Brand",
                "serverSerialNr", "SER-987",
                "serverOS", "Linux",
                "patchLevel", "1.0",
                "virtualPlatform", "vSphere",
                "virtualVersion", "7",
                "highAvailability", true
        );

        mockMvc.perform(post("/servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Server WHERE ServerSerialNr = :sn AND ServerName = 'TestServer'
                """, new MapSqlParameterSource("sn", "SER-987"), Integer.class);

        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void createRejectsEmptyPayload() throws Exception {
        mockMvc.perform(post("/servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteMissingServerReturns404() throws Exception {
        mockMvc.perform(delete("/servers/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
