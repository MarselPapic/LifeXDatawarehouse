package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Software;
import at.htlle.freq.domain.SoftwareRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link Software} that manages software metadata stored in the
 * {@code Software} table and maps lifecycle information into domain objects.
 */
@Repository
public class JdbcSoftwareRepository implements SoftwareRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSoftwareRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Software> mapper = (rs, n) -> new Software(
            rs.getObject("SoftwareID", UUID.class),
            rs.getString("Name"),
            rs.getString("Release"),
            rs.getString("Revision"),
            rs.getString("SupportPhase"),
            rs.getString("LicenseModel"),
            rs.getBoolean("ThirdParty"),
            rs.getString("EndOfSalesDate"),
            rs.getString("SupportStartDate"),
            rs.getString("SupportEndDate")
    );

    @Override
    public Optional<Software> findById(UUID id) {
        String sql = """
            SELECT SoftwareID, Name, Release, Revision, SupportPhase, LicenseModel, ThirdParty,
                   EndOfSalesDate, SupportStartDate, SupportEndDate
            FROM Software WHERE SoftwareID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<Software> findByName(String name) {
        String sql = """
            SELECT SoftwareID, Name, Release, Revision, SupportPhase, LicenseModel, ThirdParty,
                   EndOfSalesDate, SupportStartDate, SupportEndDate
            FROM Software WHERE Name = :name
            """;
        return jdbc.query(sql, new MapSqlParameterSource("name", name), mapper);
    }

    @Override
    public List<Software> findAll() {
        String sql = """
            SELECT SoftwareID, Name, Release, Revision, SupportPhase, LicenseModel, ThirdParty,
                   EndOfSalesDate, SupportStartDate, SupportEndDate
            FROM Software
            """;
        return jdbc.query(sql, mapper);
    }

    /**
     * Persists software entries via INSERT or UPDATE in the {@code Software} table.
     * <p>
     * The INSERT statement uses {@code RETURNING SoftwareID} to obtain the primary key from the
     * database. Named parameters ensure that optional periods such as {@code SupportStartDate}
     * are bound to the correct columns and stay in sync with the {@link RowMapper}.
     * </p>
     *
     * @param s software object containing release and support information.
     * @return the persisted software entry including its {@code SoftwareID}.
     */
    @Override
    public Software save(Software s) {
        boolean isNew = s.getSoftwareID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO Software (Name, Release, Revision, SupportPhase, LicenseModel,
                                      ThirdParty, EndOfSalesDate, SupportStartDate, SupportEndDate)
                VALUES (:name, :rel, :rev, :phase, :lic, :third, :eos, :ss, :se)
                RETURNING SoftwareID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                            .addValue("name", s.getName())
                            .addValue("rel",  s.getRelease())
                            .addValue("rev",  s.getRevision())
                            .addValue("phase",s.getSupportPhase())
                            .addValue("lic",  s.getLicenseModel())
                            .addValue("third", s.isThirdParty())
                            .addValue("eos",  s.getEndOfSalesDate())
                            .addValue("ss",   s.getSupportStartDate())
                            .addValue("se",   s.getSupportEndDate()),
                    UUID.class);
            s.setSoftwareID(id);
        } else {
            String sql = """
                UPDATE Software SET
                    Name = :name, Release = :rel, Revision = :rev, SupportPhase = :phase,
                    LicenseModel = :lic, ThirdParty = :third, EndOfSalesDate = :eos,
                    SupportStartDate = :ss, SupportEndDate = :se
                WHERE SoftwareID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", s.getSoftwareID())
                    .addValue("name", s.getName())
                    .addValue("rel",  s.getRelease())
                    .addValue("rev",  s.getRevision())
                    .addValue("phase",s.getSupportPhase())
                    .addValue("lic",  s.getLicenseModel())
                    .addValue("third", s.isThirdParty())
                    .addValue("eos",  s.getEndOfSalesDate())
                    .addValue("ss",   s.getSupportStartDate())
                    .addValue("se",   s.getSupportEndDate()));
        }
        return s;
    }
}
