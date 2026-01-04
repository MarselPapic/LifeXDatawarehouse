package at.htlle.freq.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * CRUD-style controller for service contracts with filter capabilities.
 */
@RestController
@RequestMapping("/servicecontracts")
public class ServiceContractController {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a controller backed by a {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc JDBC template used for service contract queries.
     */
    public ServiceContractController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lists service contracts filtered by provided foreign keys.
     *
     * <p>Path: {@code GET /servicecontracts}</p>
     * <p>Optional query params: {@code accountId}, {@code projectId}, {@code siteId}</p>
     *
     * @param accountId optional account ID filter.
     * @param projectId optional project ID filter.
     * @param siteId optional site ID filter.
     * @return 200 OK with matching contracts.
     */
    @GetMapping
    public List<Map<String, Object>> findContracts(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String siteId) {

        StringBuilder sql = new StringBuilder("""
            SELECT ContractID, AccountID, ProjectID, SiteID,
                   ContractNumber, Status, StartDate, EndDate
            FROM ServiceContract
            """);

        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();

        if (accountId != null && !accountId.isBlank()) {
            where.add("AccountID = :accountId");
            params.addValue("accountId", accountId);
        }
        if (projectId != null && !projectId.isBlank()) {
            where.add("ProjectID = :projectId");
            params.addValue("projectId", projectId);
        }
        if (siteId != null && !siteId.isBlank()) {
            where.add("SiteID = :siteId");
            params.addValue("siteId", siteId);
        }

        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }

        return jdbc.queryForList(sql.toString(), params);
    }

    /**
     * Returns a single service contract.
     *
     * <p>Path: {@code GET /servicecontracts/{id}}</p>
     *
     * @param id contract ID.
     * @return 200 OK with the row or 404 if not found.
     */
    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT ContractID, AccountID, ProjectID, SiteID,
                   ContractNumber, Status, StartDate, EndDate
            FROM ServiceContract
            WHERE ContractID = :id
            """, new MapSqlParameterSource("id", id));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service contract not found");
        }
        return rows.get(0);
    }
}
