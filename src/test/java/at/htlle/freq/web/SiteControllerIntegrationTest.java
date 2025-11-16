package at.htlle.freq.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SiteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    @Transactional
    void createSiteWithSoftwareAssignmentsPersistsSiteAndInstallations() throws Exception {
        UUID projectId = UUID.fromString("c0cb3f12-abdc-4839-bd70-070000000001");
        UUID addressId = UUID.fromString("eec1d383-0eaf-4730-8d3c-030000000001");
        UUID softwareInstalled = UUID.fromString("4da74a93-b659-4247-a8eb-060000000001");
        UUID softwareOffered = UUID.fromString("c1b43cf4-e63e-46c3-b938-060000000002");

        String siteName = "Integration Test Hub " + UUID.randomUUID();

        Map<String, Object> payload = new HashMap<>();
        payload.put("projectID", projectId);
        payload.put("addressID", addressId);
        payload.put("siteName", siteName);
        payload.put("fireZone", "Zulu");
        payload.put("tenantCount", 12);
        payload.put("softwareAssignments", List.of(
                Map.of(
                        "softwareId", softwareInstalled,
                        "status", "Installed",
                        "offeredDate", "2024-01-01",
                        "installedDate", "2024-01-10"
                ),
                Map.of(
                        "softwareId", softwareOffered,
                        "status", "Offered",
                        "offeredDate", "2024-02-05"
                )
        ));

        mockMvc.perform(post("/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        Map<String, Object> siteRow = jdbc.queryForMap("""
                SELECT SiteID   AS id,
                       ProjectID AS project,
                       AddressID AS address,
                       FireZone  AS zone,
                       TenantCount AS tenants
                FROM Site
                WHERE SiteName = :name
                """, new MapSqlParameterSource("name", siteName));

        UUID generatedSiteId = (UUID) siteRow.get("id");
        assertNotNull(generatedSiteId, "SiteID should be generated");
        assertEquals(projectId, siteRow.get("project"));
        assertEquals(addressId, siteRow.get("address"));
        assertEquals("Zulu", siteRow.get("zone"));
        assertEquals(12, siteRow.get("tenants"));

        List<Map<String, Object>> assignments = jdbc.queryForList("""
                SELECT InstalledSoftwareID AS id,
                       SoftwareID          AS sw,
                       Status              AS status,
                       OfferedDate         AS offered,
                       InstalledDate       AS installed,
                       RejectedDate        AS rejected
                FROM InstalledSoftware
                WHERE SiteID = :sid
                ORDER BY SoftwareID
                """, new MapSqlParameterSource("sid", generatedSiteId));

        assertEquals(2, assignments.size(), "Expected two software assignments");

        Map<UUID, Map<String, Object>> assignmentsBySoftware = assignments.stream()
                .collect(Collectors.toMap(row -> (UUID) row.get("sw"), row -> row));

        Map<String, Object> installedRow = assignmentsBySoftware.get(softwareInstalled);
        assertNotNull(installedRow, "Installed software assignment missing");
        assertNotNull(installedRow.get("id"), "Installed software should have an ID");
        assertEquals("Installed", installedRow.get("status"));
        assertEquals("2024-01-01", toIso(installedRow.get("offered")));
        assertEquals("2024-01-10", toIso(installedRow.get("installed")));
        assertNull(installedRow.get("rejected"));

        Map<String, Object> offeredRow = assignmentsBySoftware.get(softwareOffered);
        assertNotNull(offeredRow, "Offered software assignment missing");
        assertNotNull(offeredRow.get("id"), "Offered software should have an ID");
        assertEquals("Offered", offeredRow.get("status"));
        assertEquals("2024-02-05", toIso(offeredRow.get("offered")));
        assertNull(offeredRow.get("installed"));
        assertNull(offeredRow.get("rejected"));
    }

    @Test
    void detailEndpointIncludesAssignments() throws Exception {
        String siteId = "9356ae01-fce4-4d24-84ca-080000000001";

        mockMvc.perform(get("/sites/{id}/detail", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.SiteID").value(siteId))
                .andExpect(jsonPath("$.softwareAssignments").isArray())
                .andExpect(jsonPath("$.softwareAssignments[0].installedSoftwareId").exists());
    }

    private String toIso(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return date.toLocalDate().toString();
        }
        return value.toString();
    }
}
