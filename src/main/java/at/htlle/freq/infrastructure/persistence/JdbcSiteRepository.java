package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Site;
import at.htlle.freq.domain.SiteRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC-Repository für {@link Site}, das Standortdaten in der Tabelle {@code Site} verwaltet und
 * die Beziehungen zu Projekten sowie Adressen mappt.
 */
@Repository
public class JdbcSiteRepository implements SiteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSiteRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Site> mapper = (rs, n) -> new Site(
            rs.getObject("SiteID", UUID.class),
            rs.getString("SiteName"),
            rs.getObject("ProjectID", UUID.class),
            rs.getObject("AddressID", UUID.class),
            rs.getString("FireZone"),
            (Integer) rs.getObject("TenantCount") // nullable
    );

    @Override
    public Optional<Site> findById(UUID id) {
        String sql = """
            SELECT SiteID, SiteName, ProjectID, AddressID, FireZone, TenantCount
            FROM Site WHERE SiteID = :id
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<Site> findByProject(UUID projectId) {
        String sql = """
            SELECT SiteID, SiteName, ProjectID, AddressID, FireZone, TenantCount
            FROM Site WHERE ProjectID = :pid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("pid", projectId), mapper);
    }

    @Override
    public List<Site> findAll() {
        return jdbc.query("SELECT SiteID, SiteName, ProjectID, AddressID, FireZone, TenantCount FROM Site", mapper);
    }

    /**
     * Persistiert Standorte durch INSERT oder UPDATE in der Tabelle {@code Site}.
     * <p>
     * Die Anweisung bindet sämtliche Spalten einschließlich optionaler Felder wie
     * {@code TenantCount}, um ein konsistentes Mapping mit dem {@link RowMapper} sicherzustellen.
     * Über {@code RETURNING SiteID} wird bei neuen Standorten der Primärschlüssel übernommen.
     * </p>
     *
     * @param s Standortobjekt, dessen Attribute auf die gleichnamigen Spalten gemappt werden.
     * @return der persistierte Standort mit {@code SiteID}.
     */
    @Override
    public Site save(Site s) {
        boolean isNew = s.getSiteID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO Site (SiteName, ProjectID, AddressID, FireZone, TenantCount)
                VALUES (:name, :project, :address, :fz, :tenants)
                RETURNING SiteID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("name", s.getSiteName())
                    .addValue("project", s.getProjectID())
                    .addValue("address", s.getAddressID())
                    .addValue("fz", s.getFireZone())
                    .addValue("tenants", s.getTenantCount()), UUID.class);
            s.setSiteID(id);
        } else {
            String sql = """
                UPDATE Site SET SiteName = :name, ProjectID = :project, AddressID = :address,
                                FireZone = :fz, TenantCount = :tenants
                WHERE SiteID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", s.getSiteID())
                    .addValue("name", s.getSiteName())
                    .addValue("project", s.getProjectID())
                    .addValue("address", s.getAddressID())
                    .addValue("fz", s.getFireZone())
                    .addValue("tenants", s.getTenantCount()));
        }
        return s;
    }
}
