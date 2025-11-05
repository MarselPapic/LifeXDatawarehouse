package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Software;
import at.htlle.freq.domain.SoftwareRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC-Repository für {@link Software}, das Software-Metadaten in der Tabelle {@code Software}
 * verwaltet und Lebenszyklusinformationen in Domänenobjekte überführt.
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
            rs.getString("EndOfSalesDate"),
            rs.getString("SupportStartDate"),
            rs.getString("SupportEndDate")
    );

    @Override
    public Optional<Software> findById(UUID id) {
        String sql = """
            SELECT SoftwareID, Name, Release, Revision, SupportPhase, LicenseModel,
                   EndOfSalesDate, SupportStartDate, SupportEndDate
            FROM Software WHERE SoftwareID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<Software> findByName(String name) {
        String sql = """
            SELECT SoftwareID, Name, Release, Revision, SupportPhase, LicenseModel,
                   EndOfSalesDate, SupportStartDate, SupportEndDate
            FROM Software WHERE Name = :name
            """;
        return jdbc.query(sql, new MapSqlParameterSource("name", name), mapper);
    }

    @Override
    public List<Software> findAll() {
        String sql = """
            SELECT SoftwareID, Name, Release, Revision, SupportPhase, LicenseModel,
                   EndOfSalesDate, SupportStartDate, SupportEndDate
            FROM Software
            """;
        return jdbc.query(sql, mapper);
    }

    /**
     * Persistiert Softwareeinträge per INSERT oder UPDATE in der Tabelle {@code Software}.
     * <p>
     * Die Insert-Anweisung nutzt {@code RETURNING SoftwareID}, um den Primärschlüssel aus der
     * Datenbank zu übernehmen. Dank benannter Parameter werden auch optionale Zeiträume wie
     * {@code SupportStartDate} eindeutig den Spalten zugeordnet und bleiben mit dem
     * {@link RowMapper} synchron.
     * </p>
     *
     * @param s Softwareobjekt mit Release-/Support-Informationen.
     * @return der persistierte Softwareeintrag mit {@code SoftwareID}.
     */
    @Override
    public Software save(Software s) {
        boolean isNew = s.getSoftwareID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO Software (Name, Release, Revision, SupportPhase, LicenseModel,
                                      EndOfSalesDate, SupportStartDate, SupportEndDate)
                VALUES (:name, :rel, :rev, :phase, :lic, :eos, :ss, :se)
                RETURNING SoftwareID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                            .addValue("name", s.getName())
                            .addValue("rel",  s.getRelease())
                            .addValue("rev",  s.getRevision())
                            .addValue("phase",s.getSupportPhase())
                            .addValue("lic",  s.getLicenseModel())
                            .addValue("eos",  s.getEndOfSalesDate())
                            .addValue("ss",   s.getSupportStartDate())
                            .addValue("se",   s.getSupportEndDate()),
                    UUID.class);
            s.setSoftwareID(id);
        } else {
            String sql = """
                UPDATE Software SET
                    Name = :name, Release = :rel, Revision = :rev, SupportPhase = :phase,
                    LicenseModel = :lic, EndOfSalesDate = :eos, SupportStartDate = :ss, SupportEndDate = :se
                WHERE SoftwareID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", s.getSoftwareID())
                    .addValue("name", s.getName())
                    .addValue("rel",  s.getRelease())
                    .addValue("rev",  s.getRevision())
                    .addValue("phase",s.getSupportPhase())
                    .addValue("lic",  s.getLicenseModel())
                    .addValue("eos",  s.getEndOfSalesDate())
                    .addValue("ss",   s.getSupportStartDate())
                    .addValue("se",   s.getSupportEndDate()));
        }
        return s;
    }
}
