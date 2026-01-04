package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.ServiceContract;
import at.htlle.freq.domain.ServiceContractRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * JDBC repository for {@link ServiceContract} that stores contract data in the
 * {@code ServiceContract} table and maintains relationships to accounts, projects, and sites.
 */
@Repository
public class JdbcServiceContractRepository implements ServiceContractRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a new JdbcServiceContractRepository instance and initializes it with the provided values.
     * @param jdbc jdbc.
     */
    public JdbcServiceContractRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<ServiceContract> mapper = (rs, n) -> new ServiceContract(
            rs.getObject("ContractID", UUID.class),
            rs.getObject("AccountID", UUID.class),
            rs.getObject("ProjectID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getString("ContractNumber"),
            rs.getString("Status"),
            rs.getObject("StartDate", LocalDate.class),
            rs.getObject("EndDate", LocalDate.class)
    );

    /**
     * Finds By ID using the supplied criteria and returns the matching data.
     * @param id identifier.
     * @return the matching By ID.
     */
    @Override
    public Optional<ServiceContract> findById(UUID id) {
        String sql = """
            SELECT ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate
            FROM ServiceContract WHERE ContractID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    /**
     * Finds By Account using the supplied criteria and returns the matching data.
     * @param accountId account identifier.
     * @return the matching By Account.
     */
    @Override
    public List<ServiceContract> findByAccount(UUID accountId) {
        String sql = """
            SELECT ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate
            FROM ServiceContract WHERE AccountID = :aid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("aid", accountId), mapper);
    }

    /**
     * Finds All using the supplied criteria and returns the matching data.
     * @return the matching All.
     */
    @Override
    public List<ServiceContract> findAll() {
        return jdbc.query("""
            SELECT ContractID, AccountID, ProjectID, SiteID, ContractNumber, Status, StartDate, EndDate
            FROM ServiceContract
            """, mapper);
    }

    /**
     * Deletes the By ID from the underlying store.
     * @param id identifier.
     */
    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM ServiceContract WHERE ContractID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Persists service contracts via INSERT or UPDATE operations on the {@code ServiceContract}
     * table.
     * <p>
     * The {@code RETURNING ContractID} clause retrieves newly generated primary keys. All
     * parameters are bound by name to keep foreign keys correctly assigned and aligned with the
     * RowMapper configuration.
     * </p>
     *
     * @param s contract entity with foreign keys to account, project, and site records.
     * @return the persisted service contract including its {@code ContractID}.
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
                    .addValue("sd",  toSqlDate(s.getStartDate()))
                    .addValue("ed",  toSqlDate(s.getEndDate())), UUID.class);
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
                    .addValue("sd",  toSqlDate(s.getStartDate()))
                    .addValue("ed",  toSqlDate(s.getEndDate())));
        }
        return s;
    }

    /**
     * Executes the to SQL Date operation.
     * @param date date.
     * @return the computed result.
     */
    private static Date toSqlDate(LocalDate date) {
        return date != null ? Date.valueOf(date) : null;
    }
}
