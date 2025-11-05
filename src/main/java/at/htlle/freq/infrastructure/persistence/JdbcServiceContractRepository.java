package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.ServiceContract;
import at.htlle.freq.domain.ServiceContractRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC-Repository für {@link ServiceContract}, das Vertragsdaten in der Tabelle
 * {@code ServiceContract} ablegt und Beziehungen zu Account, Projekt und Site verwaltet.
 */
@Repository
public class JdbcServiceContractRepository implements ServiceContractRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcServiceContractRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<ServiceContract> mapper = (rs, n) -> new ServiceContract(
            rs.getObject("ContractID", UUID.class),
            rs.getObject("AccountID", UUID.class),
            rs.getObject("ProjectID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getString("ContractNumber"),
            rs.getString("Status"),
            rs.getString("StartDate"),
            rs.getString("EndDate")
    );

    @Override
    public Optional<ServiceContract> findById(UUID id) {
        String sql = """
            SELECT ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate
            FROM ServiceContract WHERE ContractID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<ServiceContract> findByAccount(UUID accountId) {
        String sql = """
            SELECT ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate
            FROM ServiceContract WHERE AccountID = :aid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("aid", accountId), mapper);
    }

    @Override
    public List<ServiceContract> findAll() {
        return jdbc.query("""
            SELECT ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate
            FROM ServiceContract
            """, mapper);
    }

    /**
     * Persistiert Serviceverträge via INSERT oder UPDATE in der Tabelle {@code ServiceContract}.
     * <p>
     * Über {@code RETURNING ContractID} werden neue Primärschlüssel aus der Datenbank gelesen.
     * Sämtliche Parameter werden namentlich gebunden, um insbesondere die Fremdschlüssel sauber
     * zuzuordnen und das RowMapper-Mapping konsistent zu halten.
     * </p>
     *
     * @param s Vertragsobjekt mit Fremdschlüsseln auf Account, Projekt und Site.
     * @return der persistierte Servicevertrag mit {@code ContractID}.
     */
    @Override
    public ServiceContract save(ServiceContract s) {
        boolean isNew = s.getContractID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO ServiceContract (AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate)
                VALUES (:acc, :proj, :site, :num, :st, :sd, :ed)
                RETURNING ContractID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("acc", s.getAccountID())
                    .addValue("proj", s.getProjectID())
                    .addValue("site", s.getSiteID())
                    .addValue("num", s.getContractNumber())
                    .addValue("st",  s.getStatus())
                    .addValue("sd",  s.getStartDate())
                    .addValue("ed",  s.getEndDate()), UUID.class);
            s.setContractID(id);
        } else {
            String sql = """
                UPDATE ServiceContract SET
                    AccountID = :acc, ProjectID = :proj, SiteID = :site, ContractNumber = :num,
                    Status = :st, StartDate = :sd, EndDate = :ed
                WHERE ContractID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", s.getContractID())
                    .addValue("acc", s.getAccountID())
                    .addValue("proj", s.getProjectID())
                    .addValue("site", s.getSiteID())
                    .addValue("num", s.getContractNumber())
                    .addValue("st",  s.getStatus())
                    .addValue("sd",  s.getStartDate())
                    .addValue("ed",  s.getEndDate()));
        }
        return s;
    }
}
