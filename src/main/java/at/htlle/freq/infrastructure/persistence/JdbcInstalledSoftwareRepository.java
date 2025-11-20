package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.InstalledSoftwareRepository;
import at.htlle.freq.domain.SiteSoftwareOverview;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.*;

/**
 * JDBC repository for {@link InstalledSoftware} that targets the {@code InstalledSoftware}
 * table and manages assignments between sites and software versions.
 */
@Repository
public class JdbcInstalledSoftwareRepository implements InstalledSoftwareRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcInstalledSoftwareRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<InstalledSoftware> mapper = (rs, n) -> {
        InstalledSoftware entity = new InstalledSoftware(
                rs.getObject("InstalledSoftwareID", UUID.class),
                rs.getObject("SiteID", UUID.class),
                rs.getObject("SoftwareID", UUID.class),
                rs.getString("Status")
        );
        entity.setOfferedDate(toIso(rs.getObject("OfferedDate", LocalDate.class)));
        entity.setInstalledDate(toIso(rs.getObject("InstalledDate", LocalDate.class)));
        entity.setRejectedDate(toIso(rs.getObject("RejectedDate", LocalDate.class)));
        entity.setOutdatedDate(toIso(rs.getObject("OutdatedDate", LocalDate.class)));
        return entity;
    };

    private final RowMapper<SiteSoftwareOverview> overviewMapper = (rs, n) -> new SiteSoftwareOverview(
            rs.getObject("InstalledSoftwareID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getString("SiteName"),
            rs.getObject("SoftwareID", UUID.class),
            rs.getString("SoftwareName"),
            rs.getString("Release"),
            rs.getString("Revision"),
            rs.getString("Status"),
            toIso(rs.getObject("OfferedDate", LocalDate.class)),
            toIso(rs.getObject("InstalledDate", LocalDate.class)),
            toIso(rs.getObject("RejectedDate", LocalDate.class)),
            toIso(rs.getObject("OutdatedDate", LocalDate.class))
    );

    @Override
    public Optional<InstalledSoftware> findById(UUID id) {
        String sql = """
            SELECT InstalledSoftwareID, SiteID, SoftwareID, Status,
                   OfferedDate, InstalledDate, RejectedDate, OutdatedDate
            FROM InstalledSoftware WHERE InstalledSoftwareID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<InstalledSoftware> findBySite(UUID siteId) {
        String sql = """
            SELECT InstalledSoftwareID, SiteID, SoftwareID, Status,
                   OfferedDate, InstalledDate, RejectedDate, OutdatedDate
            FROM InstalledSoftware WHERE SiteID = :sid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), mapper);
    }

    @Override
    public List<SiteSoftwareOverview> findOverviewBySite(UUID siteId) {
        String sql = """
            SELECT isw.InstalledSoftwareID,
                   isw.SiteID,
                   site.SiteName,
                   isw.SoftwareID,
                   sw.Name AS SoftwareName,
                   sw.Release,
                   sw.Revision,
                   isw.Status,
                   isw.OfferedDate,
                   isw.InstalledDate,
                   isw.RejectedDate,
                   isw.OutdatedDate
            FROM InstalledSoftware isw
            LEFT JOIN Software sw ON sw.SoftwareID = isw.SoftwareID
            LEFT JOIN Site site ON site.SiteID = isw.SiteID
            WHERE isw.SiteID = :sid
            ORDER BY sw.Name, sw.Release, sw.Revision
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), overviewMapper);
    }

    @Override
    public List<InstalledSoftware> findBySoftware(UUID softwareId) {
        String sql = """
            SELECT InstalledSoftwareID, SiteID, SoftwareID, Status,
                   OfferedDate, InstalledDate, RejectedDate, OutdatedDate
            FROM InstalledSoftware WHERE SoftwareID = :sw
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sw", softwareId), mapper);
    }

    @Override
    public List<InstalledSoftware> findAll() {
        return jdbc.query("""
            SELECT InstalledSoftwareID, SiteID, SoftwareID, Status,
                   OfferedDate, InstalledDate, RejectedDate, OutdatedDate
            FROM InstalledSoftware
            """, mapper);
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM InstalledSoftware WHERE InstalledSoftwareID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Persists installation records in the {@code InstalledSoftware} table.
     * <p>
     * INSERT operations retrieve the generated identifier via {@link GeneratedKeyHolder}, while
     * UPDATE statements bind every column to keep the mapping aligned with the {@link RowMapper}.
     * Parameters directly pass through the IDs of the referenced {@code Site} and {@code Software}
     * tables.
     * </p>
     *
     * @param isw installation entity whose properties map to columns of the same name.
     * @return the persisted record.
     */
    @Override
    public InstalledSoftware save(InstalledSoftware isw) {
        boolean isNew = isw.getInstalledSoftwareID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO InstalledSoftware (SiteID, SoftwareID, Status, OfferedDate, InstalledDate, RejectedDate, OutdatedDate)
                VALUES (:site, :sw, :status, :offered, :installed, :rejected, :outdated)
                """;

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("site", isw.getSiteID())
                    .addValue("sw", isw.getSoftwareID())
                    .addValue("status", isw.getStatus())
                    .addValue("offered", parseDate(isw.getOfferedDate()))
                    .addValue("installed", parseDate(isw.getInstalledDate()))
                    .addValue("rejected", parseDate(isw.getRejectedDate()))
                    .addValue("outdated", parseDate(isw.getOutdatedDate()));

            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(sql, params, keyHolder, new String[] { "InstalledSoftwareID" });

            UUID id = Optional.ofNullable(keyHolder.getKeys())
                    .map(keys -> keys.get("InstalledSoftwareID"))
                    .map(this::toUuid)
                    .orElseThrow(() -> new IllegalStateException("Failed to retrieve InstalledSoftwareID"));
            isw.setInstalledSoftwareID(id);
        } else {
            String sql = """
                UPDATE InstalledSoftware SET SiteID = :site, SoftwareID = :sw, Status = :status,
                                            OfferedDate = :offered, InstalledDate = :installed,
                                            RejectedDate = :rejected, OutdatedDate = :outdated
                WHERE InstalledSoftwareID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", isw.getInstalledSoftwareID())
                    .addValue("site", isw.getSiteID())
                    .addValue("sw", isw.getSoftwareID())
                    .addValue("status", isw.getStatus())
                    .addValue("offered", parseDate(isw.getOfferedDate()))
                    .addValue("installed", parseDate(isw.getInstalledDate()))
                    .addValue("rejected", parseDate(isw.getRejectedDate()))
                    .addValue("outdated", parseDate(isw.getOutdatedDate())));
        }
        return isw;
    }

    private String toIso(LocalDate value) {
        return value != null ? value.toString() : null;
    }

    private LocalDate parseDate(String iso) {
        return (iso == null || iso.isBlank()) ? null : LocalDate.parse(iso);
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            return UUID.fromString(text);
        }
        if (value instanceof byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long high = buffer.getLong();
            long low = buffer.getLong();
            return new UUID(high, low);
        }
        throw new IllegalStateException("Unable to convert generated key to UUID: " + value);
    }
}
