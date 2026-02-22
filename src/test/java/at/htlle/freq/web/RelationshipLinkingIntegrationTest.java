package at.htlle.freq.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RelationshipLinkingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void foreignKeyRelationsHaveNoOrphans() {
        assertZero("SELECT COUNT(*) FROM Project p LEFT JOIN Account a ON a.AccountID = p.AccountID WHERE a.AccountID IS NULL");
        assertZero("SELECT COUNT(*) FROM Project p LEFT JOIN Address a ON a.AddressID = p.AddressID WHERE a.AddressID IS NULL");
        assertZero("SELECT COUNT(*) FROM Site s LEFT JOIN Project p ON p.ProjectID = s.ProjectID WHERE p.ProjectID IS NULL");
        assertZero("SELECT COUNT(*) FROM Site s LEFT JOIN Address a ON a.AddressID = s.AddressID WHERE a.AddressID IS NULL");
        assertZero("SELECT COUNT(*) FROM Server s LEFT JOIN Site si ON si.SiteID = s.SiteID WHERE si.SiteID IS NULL");
        assertZero("SELECT COUNT(*) FROM Clients c LEFT JOIN Site s ON s.SiteID = c.SiteID WHERE s.SiteID IS NULL");
        assertZero("SELECT COUNT(*) FROM Radio r LEFT JOIN Site s ON s.SiteID = r.SiteID WHERE s.SiteID IS NULL");
        assertZero("SELECT COUNT(*) FROM Radio r LEFT JOIN Clients c ON c.ClientID = r.AssignedClientID WHERE r.AssignedClientID IS NOT NULL AND c.ClientID IS NULL");
        assertZero("SELECT COUNT(*) FROM AudioDevice a LEFT JOIN Clients c ON c.ClientID = a.ClientID WHERE c.ClientID IS NULL");
        assertZero("SELECT COUNT(*) FROM PhoneIntegration p LEFT JOIN Site s ON s.SiteID = p.SiteID WHERE s.SiteID IS NULL");
        assertZero("SELECT COUNT(*) FROM InstalledSoftware i LEFT JOIN Site s ON s.SiteID = i.SiteID WHERE s.SiteID IS NULL");
        assertZero("SELECT COUNT(*) FROM InstalledSoftware i LEFT JOIN Software sw ON sw.SoftwareID = i.SoftwareID WHERE sw.SoftwareID IS NULL");
        assertZero("SELECT COUNT(*) FROM UpgradePlan u LEFT JOIN Site s ON s.SiteID = u.SiteID WHERE s.SiteID IS NULL");
        assertZero("SELECT COUNT(*) FROM UpgradePlan u LEFT JOIN Software sw ON sw.SoftwareID = u.SoftwareID WHERE sw.SoftwareID IS NULL");
        assertZero("SELECT COUNT(*) FROM ServiceContract sc LEFT JOIN Account a ON a.AccountID = sc.AccountID WHERE a.AccountID IS NULL");
        assertZero("SELECT COUNT(*) FROM ServiceContract sc LEFT JOIN Project p ON p.ProjectID = sc.ProjectID WHERE p.ProjectID IS NULL");
        assertZero("SELECT COUNT(*) FROM ServiceContract sc LEFT JOIN Site s ON s.SiteID = sc.SiteID WHERE s.SiteID IS NULL");
        assertZero("SELECT COUNT(*) FROM ProjectSite ps LEFT JOIN Project p ON p.ProjectID = ps.ProjectID WHERE p.ProjectID IS NULL");
        assertZero("SELECT COUNT(*) FROM ProjectSite ps LEFT JOIN Site s ON s.SiteID = ps.SiteID WHERE s.SiteID IS NULL");
    }

    @Test
    void softwareRowsExposeVersionAsOwnColumn() throws Exception {
        UUID softwareId = queryUuid("SELECT SoftwareID FROM Software LIMIT 1", Map.of());
        Map<String, Object> row = readMap("/row/Software/{id}", softwareId);
        assertNotNull(getValueIgnoreCase(row, "Version"), "Software row must contain Version");
    }

    @Test
    void serverClientRadioPhoneEndpointsReturnOnlyRequestedSiteRows() throws Exception {
        UUID serverSite = queryUuid("SELECT SiteID FROM Server LIMIT 1", Map.of());
        UUID clientSite = queryUuid("SELECT SiteID FROM Clients LIMIT 1", Map.of());
        UUID radioSite = queryUuid("SELECT SiteID FROM Radio LIMIT 1", Map.of());
        UUID phoneSite = queryUuid("SELECT SiteID FROM PhoneIntegration LIMIT 1", Map.of());

        assertAllRowsMatchSite(readList("/servers", Map.of("siteId", serverSite.toString())), serverSite);
        assertAllRowsMatchSite(readList("/clients", Map.of("siteId", clientSite.toString())), clientSite);
        assertAllRowsMatchSite(readList("/radios", Map.of("siteId", radioSite.toString())), radioSite);
        assertAllRowsMatchSite(readList("/phones", Map.of("siteId", phoneSite.toString())), phoneSite);
    }

    @Test
    void audioEndpointReturnsOnlyRowsForRequestedClient() throws Exception {
        UUID clientId = queryUuid("SELECT ClientID FROM AudioDevice LIMIT 1", Map.of());
        List<Map<String, Object>> rows = readList("/audio", Map.of("clientId", clientId.toString()));
        assertFalse(rows.isEmpty(), "Expected at least one audio device row for the selected client");
        for (Map<String, Object> row : rows) {
            assertEquals(clientId.toString(), getValueIgnoreCase(row, "ClientID").toString());
        }
    }

    @Test
    void serviceContractFilterByAccountProjectSiteReturnsExactLinks() throws Exception {
        Map<String, Object> contract = jdbc.queryForMap("""
                SELECT ContractID, AccountID, ProjectID, SiteID
                FROM ServiceContract
                LIMIT 1
                """, new MapSqlParameterSource());

        String accountId = getValueIgnoreCase(contract, "AccountID").toString();
        String projectId = getValueIgnoreCase(contract, "ProjectID").toString();
        String siteId = getValueIgnoreCase(contract, "SiteID").toString();
        String contractId = getValueIgnoreCase(contract, "ContractID").toString();

        List<Map<String, Object>> rows = readList("/servicecontracts", Map.of(
                "accountId", accountId,
                "projectId", projectId,
                "siteId", siteId
        ));

        assertFalse(rows.isEmpty(), "Expected at least one filtered service contract");
        for (Map<String, Object> row : rows) {
            assertEquals(accountId, getValueIgnoreCase(row, "AccountID").toString());
            assertEquals(projectId, getValueIgnoreCase(row, "ProjectID").toString());
            assertEquals(siteId, getValueIgnoreCase(row, "SiteID").toString());
        }
        boolean containsRequestedContract = rows.stream()
                .anyMatch(row -> contractId.equalsIgnoreCase(getValueIgnoreCase(row, "ContractID").toString()));
        assertEquals(true, containsRequestedContract, "Filtered list must include the selected contract");
    }

    @Test
    void siteDetailContainsProjectAndSoftwareLinkInformation() throws Exception {
        UUID siteId = queryUuid("SELECT SiteID FROM InstalledSoftware LIMIT 1", Map.of());
        Map<String, Object> detail = readMap("/sites/{id}/detail", siteId);

        assertEquals(siteId.toString(), getValueIgnoreCase(detail, "SiteID").toString());

        Object rawProjectIds = getValueIgnoreCase(detail, "ProjectIDs");
        assertNotNull(rawProjectIds, "ProjectIDs must be present on site detail");
        List<?> projectIds = (List<?>) rawProjectIds;
        assertFalse(projectIds.isEmpty(), "ProjectIDs must not be empty");

        Object rawAssignments = detail.get("softwareAssignments");
        assertNotNull(rawAssignments, "softwareAssignments must be present on site detail");
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) rawAssignments;
        assertFalse(assignments.isEmpty(), "softwareAssignments must not be empty");

        for (Map<String, Object> assignment : assignments) {
            assertEquals(siteId.toString(), getValueIgnoreCase(assignment, "siteId").toString());
            assertNotNull(getValueIgnoreCase(assignment, "softwareId"));
        }
    }

    @Test
    void supportReportIncludesDedicatedSoftwareVersionColumn() throws Exception {
        Map<String, Object> payload = readMap("/api/reports/data?preset=next180");
        Object table = getValueIgnoreCase(payload, "table");
        assertNotNull(table, "report payload must contain table");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) getValueIgnoreCase((Map<String, Object>) table, "columns");
        assertNotNull(columns, "report table must contain columns");

        boolean containsVersionColumn = columns.stream()
                .map(col -> getValueIgnoreCase(col, "key"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .anyMatch("version"::equalsIgnoreCase);

        assertEquals(true, containsVersionColumn, "report columns must contain dedicated version key");
    }

    private void assertAllRowsMatchSite(List<Map<String, Object>> rows, UUID expectedSiteId) {
        assertFalse(rows.isEmpty(), "Expected at least one row for site filter " + expectedSiteId);
        for (Map<String, Object> row : rows) {
            assertEquals(expectedSiteId.toString(), getValueIgnoreCase(row, "SiteID").toString());
        }
    }

    private void assertZero(String sql) {
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
        assertEquals(0, count, "Expected no orphan rows for query: " + sql);
    }

    private UUID queryUuid(String sql, Map<String, Object> params) {
        return jdbc.queryForObject(sql, new MapSqlParameterSource(params), UUID.class);
    }

    private Map<String, Object> readMap(String urlTemplate, Object... uriVars) throws Exception {
        String body = mockMvc.perform(get(urlTemplate, uriVars))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(body, new TypeReference<>() {});
    }

    private List<Map<String, Object>> readList(String urlTemplate, Map<String, String> params) throws Exception {
        var request = get(urlTemplate);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            request = request.queryParam(entry.getKey(), entry.getValue());
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(body, new TypeReference<>() {});
    }

    private Object getValueIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
