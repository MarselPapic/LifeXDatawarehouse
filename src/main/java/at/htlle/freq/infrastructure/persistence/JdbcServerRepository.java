package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Server;
import at.htlle.freq.domain.ServerRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC-Repository für {@link Server}, das die Tabelle {@code Server} verwaltet und Server-Hardware
 * inklusive Virtualisierungs- und Hochverfügbarkeitsmerkmalen in Domänenobjekte überführt.
 */
@Repository
public class JdbcServerRepository implements ServerRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcServerRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Server> mapper = (rs, n) -> new Server(
            rs.getObject("ServerID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getString("ServerName"),
            rs.getString("ServerBrand"),
            rs.getString("ServerSerialNr"),
            rs.getString("ServerOS"),
            rs.getString("PatchLevel"),
            rs.getString("VirtualPlatform"),
            rs.getString("VirtualVersion"),
            rs.getBoolean("HighAvailability")
    );

    @Override
    public Optional<Server> findById(UUID id) {
        String sql = """
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr, ServerOS,
                   PatchLevel, VirtualPlatform, VirtualVersion, HighAvailability
            FROM Server WHERE ServerID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<Server> findBySite(UUID siteId) {
        String sql = """
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr, ServerOS,
                   PatchLevel, VirtualPlatform, VirtualVersion, HighAvailability
            FROM Server WHERE SiteID = :sid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), mapper);
    }

    @Override
    public List<Server> findAll() {
        String sql = """
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr, ServerOS,
                   PatchLevel, VirtualPlatform, VirtualVersion, HighAvailability
            FROM Server
            """;
        return jdbc.query(sql, mapper);
    }

    /**
     * Speichert Servereinträge per INSERT oder UPDATE in der Tabelle {@code Server}.
     * <p>
     * Beim INSERT wird die ID über {@code RETURNING ServerID} bezogen. Aufgrund der vielen
     * Attribute werden alle Parameter explizit gesetzt, sodass das Mapping des RowMappers
     * vollständig bleibt und keine inkonsistenten Teilupdates entstehen.
     * </p>
     *
     * @param s Serverobjekt, dessen Eigenschaften auf die Spalten {@code SiteID}, {@code ServerName}
     *          usw. gemappt werden.
     * @return der persistierte Server mit {@code ServerID}.
     */
    @Override
    public Server save(Server s) {
        boolean isNew = s.getServerID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO Server (SiteID, ServerName, ServerBrand, ServerSerialNr, ServerOS,
                                    PatchLevel, VirtualPlatform, VirtualVersion, HighAvailability)
                VALUES (:site, :name, :brand, :sn, :os, :pl, :vp, :vv, :ha)
                RETURNING ServerID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                            .addValue("site", s.getSiteID())
                            .addValue("name", s.getServerName())
                            .addValue("brand", s.getServerBrand())
                            .addValue("sn", s.getServerSerialNr())
                            .addValue("os", s.getServerOS())
                            .addValue("pl", s.getPatchLevel())
                            .addValue("vp", s.getVirtualPlatform())
                            .addValue("vv", s.getVirtualVersion())
                            .addValue("ha", s.isHighAvailability()),
                    UUID.class);
            s.setServerID(id);
        } else {
            String sql = """
                UPDATE Server SET
                    SiteID = :site, ServerName = :name, ServerBrand = :brand, ServerSerialNr = :sn,
                    ServerOS = :os, PatchLevel = :pl, VirtualPlatform = :vp, VirtualVersion = :vv, HighAvailability = :ha
                WHERE ServerID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", s.getServerID())
                    .addValue("site", s.getSiteID())
                    .addValue("name", s.getServerName())
                    .addValue("brand", s.getServerBrand())
                    .addValue("sn", s.getServerSerialNr())
                    .addValue("os", s.getServerOS())
                    .addValue("pl", s.getPatchLevel())
                    .addValue("vp", s.getVirtualPlatform())
                    .addValue("vv", s.getVirtualVersion())
                    .addValue("ha", s.isHighAvailability()));
        }
        return s;
    }
}
