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
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        String contractA = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO ServiceContract (ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate)
                VALUES (:id, :account, :project, :site, 'CNT-A', 'Active', :start, :end)
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
                VALUES (:id, :account, :project, :site, 'CNT-B', 'Expired', :start, :end)
                """, new MapSqlParameterSource(Map.of(
                "id", UUID.randomUUID(),
                "account", UUID.randomUUID(),
                "project", UUID.randomUUID(),
                "site", UUID.randomUUID(),
                "start", LocalDate.parse("2023-01-01"),
                "end", LocalDate.parse("2023-12-31")
        )));

        mockMvc.perform(get("/servicecontracts")
                        .param("accountId", accountId.toString())
                        .param("projectId", projectId.toString())
                        .param("siteId", siteId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ContractNumber", is("CNT-A")));
    }

    @Test
    @Transactional
    void findByIdReturns404WhenMissing() throws Exception {
        String missingId = UUID.randomUUID().toString();

        mockMvc.perform(get("/servicecontracts/{id}", missingId))
                .andExpect(status().isNotFound());
    }
}
