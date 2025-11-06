package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.UpgradePlan;
import at.htlle.freq.domain.UpgradePlanRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link UpgradePlan} that manages maintenance windows in the
 * {@code UpgradePlan} table and maps relationships to sites and software entries.
 */
@Repository
public class JdbcUpgradePlanRepository implements UpgradePlanRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUpgradePlanRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<UpgradePlan> mapper = (rs, n) -> new UpgradePlan(
            rs.getObject("UpgradePlanID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getObject("SoftwareID", UUID.class),
            rs.getString("PlannedWindowStart"),
            rs.getString("PlannedWindowEnd"),
            rs.getString("Status"),
            rs.getString("CreatedAt"),
            rs.getString("CreatedBy")
    );

    @Override
    public Optional<UpgradePlan> findById(UUID id) {
        String sql = """
            SELECT UpgradePlanID, SiteID, SoftwareID, PlannedWindowStart, PlannedWindowEnd,
                   Status, CreatedAt, CreatedBy
            FROM UpgradePlan WHERE UpgradePlanID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<UpgradePlan> findBySite(UUID siteId) {
        String sql = """
            SELECT UpgradePlanID, SiteID, SoftwareID, PlannedWindowStart, PlannedWindowEnd,
                   Status, CreatedAt, CreatedBy
            FROM UpgradePlan WHERE SiteID = :sid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), mapper);
    }

    @Override
    public List<UpgradePlan> findAll() {
        return jdbc.query("""
            SELECT UpgradePlanID, SiteID, SoftwareID, PlannedWindowStart, PlannedWindowEnd,
                   Status, CreatedAt, CreatedBy
            FROM UpgradePlan
            """, mapper);
    }

    /**
     * Persists upgrade plans via INSERT or UPDATE operations on the {@code UpgradePlan} table.
     * <p>
     * The {@code RETURNING UpgradePlanID} clause delivers the generated identifier for inserts,
     * while updates bind every date and status field to prevent inconsistencies between the
     * database and the RowMapper.
     * </p>
     *
     * @param u upgrade plan referencing site and software records.
     * @return the stored plan including its {@code UpgradePlanID}.
     */
    @Override
    public UpgradePlan save(UpgradePlan u) {
        boolean isNew = u.getUpgradePlanID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO UpgradePlan (SiteID, SoftwareID, PlannedWindowStart, PlannedWindowEnd,
                                         Status, CreatedAt, CreatedBy)
                VALUES (:site, :sw, :pws, :pwe, :st, :ca, :cb)
                RETURNING UpgradePlanID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("site", u.getSiteID())
                    .addValue("sw", u.getSoftwareID())
                    .addValue("pws", u.getPlannedWindowStart())
                    .addValue("pwe", u.getPlannedWindowEnd())
                    .addValue("st", u.getStatus())
                    .addValue("ca", u.getCreatedAt())
                    .addValue("cb", u.getCreatedBy()), UUID.class);
            u.setUpgradePlanID(id);
        } else {
            String sql = """
                UPDATE UpgradePlan SET
                    SiteID = :site, SoftwareID = :sw, PlannedWindowStart = :pws, PlannedWindowEnd = :pwe,
                    Status = :st, CreatedAt = :ca, CreatedBy = :cb
                WHERE UpgradePlanID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", u.getUpgradePlanID())
                    .addValue("site", u.getSiteID())
                    .addValue("sw", u.getSoftwareID())
                    .addValue("pws", u.getPlannedWindowStart())
                    .addValue("pwe", u.getPlannedWindowEnd())
                    .addValue("st", u.getStatus())
                    .addValue("ca", u.getCreatedAt())
                    .addValue("cb", u.getCreatedBy()));
        }
        return u;
    }
}
