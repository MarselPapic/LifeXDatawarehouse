package at.htlle.freq.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceContractControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    @Transactional
    void findContractsHonorsFilters() throws Exception {
        UUID accountId = jdbc.queryForObject("SELECT AccountID FROM Account LIMIT 1",
                new MapSqlParameterSource(), UUID.class);
        UUID projectId = jdbc.queryForObject("SELECT ProjectID FROM Project LIMIT 1",
                new MapSqlParameterSource(), UUID.class);
        UUID siteId = jdbc.queryForObject("SELECT SiteID FROM Site LIMIT 1",
                new MapSqlParameterSource(), UUID.class);

        String contractA = UUID.randomUUID().toString();
        UUID altAccountId = jdbc.queryForObject("SELECT AccountID FROM Account WHERE AccountID <> :id LIMIT 1",
                new MapSqlParameterSource("id", accountId), UUID.class);
        UUID altProjectId = jdbc.queryForObject("SELECT ProjectID FROM Project WHERE ProjectID <> :id LIMIT 1",
                new MapSqlParameterSource("id", projectId), UUID.class);
        UUID altSiteId = jdbc.queryForObject("SELECT SiteID FROM Site WHERE SiteID <> :id LIMIT 1",
                new MapSqlParameterSource("id", siteId), UUID.class);

        jdbc.update("""
                INSERT INTO ServiceContract (ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate)
                VALUES (:id, :account, :project, :site, 'CNT-A', 'Planned', :start, :end)
                """, new MapSqlParameterSource(Map.of(
                "id", contractA,
                "account", accountId,
                "project", projectId,
                "site", siteId,
                "start", LocalDate.parse("2024-01-01"),
                "end", LocalDate.parse("2024-12-31")
        )));

        jdbc.update("""
                INSERT INTO ServiceContract (ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate)
                VALUES (:id, :account, :project, :site, 'CNT-B', 'Canceled', :start, :end)
                """, new MapSqlParameterSource(Map.of(
                "id", UUID.randomUUID(),
                "account", altAccountId,
                "project", altProjectId,
                "site", altSiteId,
                "start", LocalDate.parse("2023-01-01"),
                "end", LocalDate.parse("2023-12-31")
        )));

        mockMvc.perform(get("/servicecontracts")
                        .param("accountId", accountId.toString())
                        .param("projectId", projectId.toString())
                        .param("siteId", siteId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].CONTRACTNUMBER", is("CNT-A")));
    }

    @Test
    @Transactional
    void findByIdReturns404WhenMissing() throws Exception {
        String missingId = UUID.randomUUID().toString();

        mockMvc.perform(get("/servicecontracts/{id}", missingId))
                .andExpect(status().isNotFound());
    }
}
