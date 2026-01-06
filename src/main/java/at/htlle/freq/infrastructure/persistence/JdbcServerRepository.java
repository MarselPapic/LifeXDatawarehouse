package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Server;
import at.htlle.freq.domain.ServerRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link Server} that maintains the {@code Server} table and maps server
 * hardware—including virtualization settings—into domain objects.
*/
@Repository
public class JdbcServerRepository implements ServerRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a new JdbcServerRepository instance and initializes it with the provided values.
     * @param jdbc jdbc.
     */
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
            rs.getString("VirtualVersion")
    );

    /**
     * Finds By ID using the supplied criteria and returns the matching data.
     * @param id identifier.
     * @return the matching By ID.
     */
    @Override
    public Optional<Server> findById(UUID id) {
        String sql = """
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr, ServerOS,
                   PatchLevel, VirtualPlatform, VirtualVersion
            FROM Server WHERE ServerID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (org.springframework.dao.EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    /**
     * Finds By Site using the supplied criteria and returns the matching data.
     * @param siteId site identifier.
     * @return the matching By Site.
     */
    @Override
    public List<Server> findBySite(UUID siteId) {
        String sql = """
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr, ServerOS,
                   PatchLevel, VirtualPlatform, VirtualVersion
            FROM Server WHERE SiteID = :sid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), mapper);
    }

    /**
     * Finds All using the supplied criteria and returns the matching data.
     * @return the matching All.
     */
    @Override
    public List<Server> findAll() {
        String sql = """
            SELECT ServerID, SiteID, ServerName, ServerBrand, ServerSerialNr, ServerOS,
                   PatchLevel, VirtualPlatform, VirtualVersion
            FROM Server
            """;
        return jdbc.query(sql, mapper);
    }

    /**
     * Deletes the By ID from the underlying store.
     * @param id identifier.
     */
    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM Server WHERE ServerID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Stores server entries via INSERT or UPDATE statements against the {@code Server} table.
     * <p>
     * During INSERT operations the ID is retrieved through {@code RETURNING ServerID}. Because of
     * the extensive attribute set, every parameter is bound explicitly so that the RowMapper stays
     * complete and no inconsistent partial updates occur.
     * </p>
     *
     * @param s server entity whose properties map to columns such as {@code SiteID} and
     *          {@code ServerName}.
     * @return the persisted server including its {@code ServerID}.
     */
    @Override
    public Server save(Server s) {
        boolean isNew = s.getServerID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO Server (SiteID, ServerName, ServerBrand, ServerSerialNr, ServerOS,
                                    PatchLevel, VirtualPlatform, VirtualVersion)
                VALUES (:site, :name, :brand, :sn, :os, :pl, :vp, :vv)
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
                            .addValue("vv", s.getVirtualVersion()),
                    UUID.class);
            s.setServerID(id);
        } else {
            String sql = """
                UPDATE Server SET
                    SiteID = :site, ServerName = :name, ServerBrand = :brand, ServerSerialNr = :sn,
                    ServerOS = :os, PatchLevel = :pl, VirtualPlatform = :vp, VirtualVersion = :vv
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
                    .addValue("vv", s.getVirtualVersion()));
        }
        return s;
    }
}
