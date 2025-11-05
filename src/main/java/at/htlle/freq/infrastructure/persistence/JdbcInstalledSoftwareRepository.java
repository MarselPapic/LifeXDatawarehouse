package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.InstalledSoftwareRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class JdbcInstalledSoftwareRepository implements InstalledSoftwareRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcInstalledSoftwareRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<InstalledSoftware> mapper = (rs, n) -> new InstalledSoftware(
            rs.getObject("InstalledSoftwareID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getObject("SoftwareID", UUID.class),
            rs.getString("Status")
    );

    @Override
    public Optional<InstalledSoftware> findById(UUID id) {
        String sql = """
            SELECT InstalledSoftwareID, SiteID, SoftwareID, Status
            FROM InstalledSoftware WHERE InstalledSoftwareID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<InstalledSoftware> findBySite(UUID siteId) {
        String sql = """
            SELECT InstalledSoftwareID, SiteID, SoftwareID, Status
            FROM InstalledSoftware WHERE SiteID = :sid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), mapper);
    }

    @Override
    public List<InstalledSoftware> findBySoftware(UUID softwareId) {
        String sql = """
            SELECT InstalledSoftwareID, SiteID, SoftwareID, Status
            FROM InstalledSoftware WHERE SoftwareID = :sw
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sw", softwareId), mapper);
    }

    @Override
    public List<InstalledSoftware> findAll() {
        return jdbc.query("""
            SELECT InstalledSoftwareID, SiteID, SoftwareID, Status
            FROM InstalledSoftware
            """, mapper);
    }

    @Override
    public InstalledSoftware save(InstalledSoftware isw) {
        boolean isNew = isw.getInstalledSoftwareID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO InstalledSoftware (SiteID, SoftwareID, Status)
                VALUES (:site, :sw, :status)
                RETURNING InstalledSoftwareID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("site", isw.getSiteID())
                    .addValue("sw", isw.getSoftwareID())
                    .addValue("status", isw.getStatus()), UUID.class);
            isw.setInstalledSoftwareID(id);
        } else {
            String sql = """
                UPDATE InstalledSoftware SET SiteID = :site, SoftwareID = :sw, Status = :status
                WHERE InstalledSoftwareID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", isw.getInstalledSoftwareID())
                    .addValue("site", isw.getSiteID())
                    .addValue("sw", isw.getSoftwareID())
                    .addValue("status", isw.getStatus()));
        }
        return isw;
    }
}
