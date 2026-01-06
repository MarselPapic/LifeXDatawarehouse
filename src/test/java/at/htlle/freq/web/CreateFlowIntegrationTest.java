package at.htlle.freq.web;

import com.fasterxml.jackson.core.type.TypeReference;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreateFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    @Transactional
    void createEntitiesAndVerifyDetailEndpoints() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String countryCode = "ZZ";
        String cityId = "ZZ-CITY-" + suffix;
        String street = "Integration Street " + suffix;
        String softwareName = "LifeX Test " + suffix;

        mockMvc.perform(post("/row/country")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "CountryCode", countryCode,
                                "CountryName", "Zedland"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/row/city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "CityID", cityId,
                                "CityName", "Testopolis",
                                "CountryCode", countryCode
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/row/software")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "Name", softwareName,
                                "Release", "1.0",
                                "Revision", "1",
                                "SupportPhase", "Production",
                                "ThirdParty", false
                        ))))
                .andExpect(status().isCreated());

        UUID softwareId = queryUuid("""
                SELECT SoftwareID
                FROM Software
                WHERE Name = :name AND Release = :release AND Revision = :revision
                """, Map.of("name", softwareName, "release", "1.0", "revision", "1"));

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "street", street,
                                "cityID", cityId
                        ))))
                .andExpect(status().isCreated());

        UUID addressId = queryUuid("""
                SELECT AddressID
                FROM Address
                WHERE Street = :street AND CityID = :city
                """, Map.of("street", street, "city", cityId));

        String accountName = "Account " + suffix;
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountName", accountName,
                                "contactEmail", "integration-" + suffix + "@example.test",
                                "country", "Zedland"
                        ))))
                .andExpect(status().isCreated());

        UUID accountId = queryUuid("""
                SELECT AccountID
                FROM Account
                WHERE AccountName = :name
                """, Map.of("name", accountName));

        String variantCode = "VAR-" + suffix;
        String variantName = "Variant " + suffix;
        mockMvc.perform(post("/deployment-variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "variantCode", variantCode,
                                "variantName", variantName,
                                "description", "Integration variant",
                                "active", true
                        ))))
                .andExpect(status().isCreated());

        UUID variantId = queryUuid("""
                SELECT VariantID
                FROM DeploymentVariant
                WHERE VariantCode = :code
                """, Map.of("code", variantCode));

        String projectSapId = "SAP-" + suffix;
        String projectName = "Project " + suffix;
        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectSAPID", projectSapId,
                                "projectName", projectName,
                                "deploymentVariantID", variantId,
                                "bundleType", "Standard",
                                "lifecycleStatus", "ACTIVE",
                                "accountID", accountId,
                                "addressID", addressId
                        ))))
                .andExpect(status().isCreated());

        UUID projectId = queryUuid("""
                SELECT ProjectID
                FROM Project
                WHERE ProjectSAPID = :sap
                """, Map.of("sap", projectSapId));

        String siteName = "Site " + suffix;
        mockMvc.perform(post("/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "siteName", siteName,
                                "projectIds", List.of(projectId),
                                "addressID", addressId,
                                "fireZone", "Zulu",
                                "tenantCount", 2,
                                "redundantServers", 1,
                                "highAvailability", true,
                                "softwareAssignments", List.of(
                                        Map.of(
                                                "softwareId", softwareId,
                                                "status", "Installed",
                                                "offeredDate", "2024-01-01",
                                                "installedDate", "2024-01-10"
                                        )
                                )
                        ))))
                .andExpect(status().isCreated());

        UUID siteId = queryUuid("""
                SELECT SiteID
                FROM Site
                WHERE SiteName = :name
                """, Map.of("name", siteName));

        String serverName = "srv-" + suffix;
        mockMvc.perform(post("/servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "siteID", siteId,
                                "serverName", serverName,
                                "serverBrand", "TestBrand",
                                "serverSerialNr", "SER-" + suffix,
                                "serverOS", "Linux",
                                "patchLevel", "1.0",
                                "virtualPlatform", "HyperV",
                                "virtualVersion", "2022"
                        ))))
                .andExpect(status().isCreated());

        UUID serverId = queryUuid("""
                SELECT ServerID
                FROM Server
                WHERE ServerName = :name AND SiteID = :site
                """, Map.of("name", serverName, "site", siteId));

        String clientName = "client-" + suffix;
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "siteID", siteId,
                                "clientName", clientName,
                                "installType", "LOCAL"
                        ))))
                .andExpect(status().isOk());

        UUID clientId = queryUuid("""
                SELECT ClientID
                FROM Clients
                WHERE ClientName = :name AND SiteID = :site
                """, Map.of("name", clientName, "site", siteId));

        String radioSerial = "RAD-" + suffix;
        mockMvc.perform(post("/radios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "siteID", siteId,
                                "assignedClientID", clientId,
                                "radioBrand", "TestBrand",
                                "radioSerialNr", radioSerial,
                                "mode", "Digital",
                                "digitalStandard", "Tetra"
                        ))))
                .andExpect(status().isCreated());

        UUID radioId = queryUuid("""
                SELECT RadioID
                FROM Radio
                WHERE RadioSerialNr = :serial AND SiteID = :site
                """, Map.of("serial", radioSerial, "site", siteId));

        String audioSerial = "AUD-" + suffix;
        mockMvc.perform(post("/audio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientID", clientId,
                                "audioDeviceBrand", "Acme",
                                "deviceSerialNr", audioSerial,
                                "audioDeviceFirmware", "1.0",
                                "deviceType", "HEADSET",
                                "direction", "Input"
                        ))))
                .andExpect(status().isCreated());

        UUID audioId = queryUuid("""
                SELECT AudioDeviceID
                FROM AudioDevice
                WHERE DeviceSerialNr = :serial AND ClientID = :client
                """, Map.of("serial", audioSerial, "client", clientId));

        String interfaceName = "SIP-" + suffix;
        mockMvc.perform(post("/phones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "siteID", siteId,
                                "phoneType", "Emergency",
                                "phoneBrand", "Acme",
                                "capacity", 12,
                                "interfaceName", interfaceName,
                                "phoneFirmware", "1.0"
                        ))))
                .andExpect(status().isCreated());

        UUID phoneId = queryUuid("""
                SELECT PhoneIntegrationID
                FROM PhoneIntegration
                WHERE InterfaceName = :name AND SiteID = :site
                """, Map.of("name", interfaceName, "site", siteId));

        String createdBy = "tester-" + suffix;
        mockMvc.perform(post("/row/upgradeplan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "SiteID", siteId,
                                "SoftwareID", softwareId,
                                "PlannedWindowStart", "2024-12-01",
                                "PlannedWindowEnd", "2024-12-05",
                                "Status", "Planned",
                                "CreatedAt", "2024-11-01",
                                "CreatedBy", createdBy
                        ))))
                .andExpect(status().isCreated());

        UUID upgradePlanId = queryUuid("""
                SELECT UpgradePlanID
                FROM UpgradePlan
                WHERE SiteID = :site AND SoftwareID = :software AND CreatedBy = :createdBy
                """, Map.of("site", siteId, "software", softwareId, "createdBy", createdBy));

        String contractNumber = "SC-" + suffix;
        mockMvc.perform(post("/row/servicecontract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "AccountID", accountId,
                                "ProjectID", projectId,
                                "SiteID", siteId,
                                "ContractNumber", contractNumber,
                                "Status", "Planned",
                                "StartDate", "2024-10-01",
                                "EndDate", "2024-12-31"
                        ))))
                .andExpect(status().isCreated());

        UUID contractId = queryUuid("""
                SELECT ContractID
                FROM ServiceContract
                WHERE ContractNumber = :contract
                """, Map.of("contract", contractNumber));

        Map<String, Object> countryRow = readRow("/row/Country/{id}", countryCode);
        assertEquals(countryCode, getStringIgnoreCase(countryRow, "CountryCode"));

        Map<String, Object> cityRow = readRow("/row/City/{id}", cityId);
        assertEquals(cityId, getStringIgnoreCase(cityRow, "CityID"));
        assertEquals(countryCode, getStringIgnoreCase(cityRow, "CountryCode"));

        Map<String, Object> addressRow = readRow("/row/Address/{id}", addressId);
        assertEquals(street, getStringIgnoreCase(addressRow, "Street"));
        assertEquals(cityId, getStringIgnoreCase(addressRow, "CityID"));

        Map<String, Object> softwareRow = readRow("/row/Software/{id}", softwareId);
        assertEquals(softwareName, getStringIgnoreCase(softwareRow, "Name"));

        Map<String, Object> projectRow = readRow("/row/Project/{id}", projectId);
        assertEquals(projectSapId, getStringIgnoreCase(projectRow, "ProjectSAPID"));
        assertEquals(accountId.toString(), getStringIgnoreCase(projectRow, "AccountID"));

        Map<String, Object> siteRow = readRow("/row/Site/{id}", siteId);
        assertEquals(siteName, getStringIgnoreCase(siteRow, "SiteName"));

        Map<String, Object> serverRow = readRow("/row/Server/{id}", serverId);
        assertEquals(serverName, getStringIgnoreCase(serverRow, "ServerName"));

        Map<String, Object> clientRow = readRow("/row/Clients/{id}", clientId);
        assertEquals(clientName, getStringIgnoreCase(clientRow, "ClientName"));

        Map<String, Object> upgradeRow = readRow("/row/UpgradePlan/{id}", upgradePlanId);
        assertEquals(upgradePlanId.toString(), getStringIgnoreCase(upgradeRow, "UpgradePlanID"));

        Map<String, Object> contractRow = readRow("/row/ServiceContract/{id}", contractId);
        assertEquals(contractNumber, getStringIgnoreCase(contractRow, "ContractNumber"));

        mockMvc.perform(get("/sites/{id}/detail", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ProjectIDs[0]").value(projectId.toString()))
                .andExpect(jsonPath("$.softwareAssignments[0].softwareId").value(softwareId.toString()));

        List<Map<String, Object>> servers = readList("/servers", Map.of("siteId", siteId.toString()));
        assertEquals(1, servers.size());
        assertEquals(serverId.toString(), getStringIgnoreCase(servers.get(0), "ServerID"));

        mockMvc.perform(get("/clients").queryParam("siteId", siteId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientID").value(clientId.toString()));

        List<Map<String, Object>> radios = readList("/radios", Map.of("siteId", siteId.toString()));
        assertEquals(1, radios.size());
        assertEquals(radioId.toString(), getStringIgnoreCase(radios.get(0), "RadioID"));

        List<Map<String, Object>> audioDevices = readList("/audio", Map.of("clientId", clientId.toString()));
        assertEquals(1, audioDevices.size());
        assertEquals(audioId.toString(), getStringIgnoreCase(audioDevices.get(0), "AudioDeviceID"));

        List<Map<String, Object>> phones = readList("/phones", Map.of("siteId", siteId.toString()));
        assertEquals(1, phones.size());
        assertEquals(phoneId.toString(), getStringIgnoreCase(phones.get(0), "PhoneIntegrationID"));

        List<Map<String, Object>> contracts = readList("/servicecontracts", Map.of(
                "accountId", accountId.toString(),
                "projectId", projectId.toString(),
                "siteId", siteId.toString()
        ));
        assertEquals(1, contracts.size());
        assertEquals(contractId.toString(), getStringIgnoreCase(contracts.get(0), "ContractID"));
    }

    private UUID queryUuid(String sql, Map<String, Object> params) {
        return jdbc.queryForObject(sql, new MapSqlParameterSource(params), UUID.class);
    }

    private Map<String, Object> readRow(String urlTemplate, Object... vars) throws Exception {
        String body = mockMvc.perform(get(urlTemplate, vars))
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

    private String getStringIgnoreCase(Map<String, Object> row, String key) {
        Object value = getValueIgnoreCase(row, key);
        return value == null ? null : value.toString();
    }

    private Object getValueIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        assertNotNull(row, "Row was null when searching for " + key);
        return null;
    }
}
