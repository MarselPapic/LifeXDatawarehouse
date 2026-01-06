package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Site;
import at.htlle.freq.domain.SiteRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link Site} that manages location data in the {@code Site} table and maps
 * relations to projects and addresses.
 */
@Repository
public class JdbcSiteRepository implements SiteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a new JdbcSiteRepository instance and initializes it with the provided values.
     * @param jdbc jdbc.
     */
    public JdbcSiteRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Site> mapper = (rs, n) -> new Site(
            rs.getObject("SiteID", UUID.class),
            rs.getString("SiteName"),
            rs.getObject("ProjectID", UUID.class),
            rs.getObject("AddressID", UUID.class),
            rs.getString("FireZone"),
            (Integer) rs.getObject("TenantCount"), // nullable column
            rs.getInt("RedundantServers"),
            rs.getObject("HighAvailability", Boolean.class)
    );

    /**
     * Finds By ID using the supplied criteria and returns the matching data.
     * @param id identifier.
     * @return the matching By ID.
     */
    @Override
    public Optional<Site> findById(UUID id) {
        String sql = """
            SELECT SiteID, SiteName, ProjectID, AddressID, FireZone, TenantCount, RedundantServers, HighAvailability
            FROM Site WHERE SiteID = :id
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    /**
     * Finds By Project using the supplied criteria and returns the matching data.
     * @param projectId project identifier.
     * @return the matching By Project.
     */
    @Override
    public List<Site> findByProject(UUID projectId) {
        String sql = """
            SELECT s.SiteID, s.SiteName, s.ProjectID, s.AddressID, s.FireZone, s.TenantCount, s.RedundantServers, s.HighAvailability
            FROM Site s
            JOIN ProjectSite ps ON ps.SiteID = s.SiteID
            WHERE ps.ProjectID = :pid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("pid", projectId), mapper);
    }

    /**
     * Finds All using the supplied criteria and returns the matching data.
     * @return the matching All.
     */
    @Override
    public List<Site> findAll() {
        return jdbc.query("SELECT SiteID, SiteName, ProjectID, AddressID, FireZone, TenantCount, RedundantServers, HighAvailability FROM Site", mapper);
    }

    /**
     * Deletes the By ID from the underlying store.
     * @param id identifier.
     */
    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM Site WHERE SiteID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Persists sites through INSERT or UPDATE statements executed on the {@code Site} table.
     * <p>
     * Every column, including optional fields such as {@code TenantCount}, is bound explicitly to
     * maintain a consistent mapping with the {@link RowMapper}. Generated identifiers are obtained
     * via a {@link KeyHolder} to keep compatibility with databases that do not support a
     * {@code RETURNING} clause.
     * </p>
     *
     * @param s site entity whose attributes map directly to the table columns of the same name.
     * @return the persisted site including its {@code SiteID}.
     */
    @Override
    public Site save(Site s) {
        boolean isNew = s.getSiteID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO Site (SiteName, ProjectID, AddressID, FireZone, TenantCount, RedundantServers, HighAvailability)
                VALUES (:name, :project, :address, :fz, :tenants, :redundant, :ha)
                """;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(sql, new MapSqlParameterSource()
                            .addValue("name", s.getSiteName())
                            .addValue("project", s.getProjectID())
                            .addValue("address", s.getAddressID())
                            .addValue("fz", s.getFireZone())
                            .addValue("tenants", s.getTenantCount())
                            .addValue("redundant", s.getRedundantServers())
                            .addValue("ha", Boolean.TRUE.equals(s.getHighAvailability())),
                    keyHolder, new String[]{"SiteID"});

            UUID id = extractGeneratedSiteId(keyHolder);
            if (id == null) {
                throw new IllegalStateException("Failed to retrieve generated SiteID after insert");
            }
            s.setSiteID(id);
        } else {
            String sql = """
                UPDATE Site SET SiteName = :name, ProjectID = :project, AddressID = :address,
                                FireZone = :fz, TenantCount = :tenants, RedundantServers = :redundant, HighAvailability = :ha
                WHERE SiteID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", s.getSiteID())
                    .addValue("name", s.getSiteName())
                    .addValue("project", s.getProjectID())
                    .addValue("address", s.getAddressID())
                    .addValue("fz", s.getFireZone())
                    .addValue("tenants", s.getTenantCount())
                    .addValue("redundant", s.getRedundantServers())
                    .addValue("ha", Boolean.TRUE.equals(s.getHighAvailability())));
        }
        return s;
    }

    /**
     * Extracts the Generated Site ID from the supplied input.
     * @param keyHolder key holder.
     * @return the computed result.
     */
    private UUID extractGeneratedSiteId(KeyHolder keyHolder) {
        if (keyHolder == null) {
            return null;
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            UUID id = extractFromMap(keys);
            if (id != null) {
                return id;
            }
        }

        List<Map<String, Object>> keyList = keyHolder.getKeyList();
        if (keyList != null) {
            for (Map<String, Object> map : keyList) {
                UUID id = extractFromMap(map);
                if (id != null) {
                    return id;
                }
                if (map.size() == 1) {
                    UUID sole = coerceToUuid(map.values().iterator().next());
                    if (sole != null) {
                        return sole;
                    }
                }
            }
        }

        Object key = keyHolder.getKey();
        return coerceToUuid(key);
    }

    /**
     * Extracts the From Map from the supplied input.
     * @param map map.
     * @return the computed result.
     */
    private UUID extractFromMap(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if ("siteid".equalsIgnoreCase(entry.getKey())) {
                UUID id = coerceToUuid(entry.getValue());
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    /**
     * Coerces the To UUID into the expected type.
     * @param value value.
     * @return the computed result.
     */
    private UUID coerceToUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof CharSequence sequence) {
            return parseUuid(sequence.toString());
        }
        if (value != null) {
            return parseUuid(value.toString());
        }
        return null;
    }

    /**
     * Parses the UUID from the supplied input.
     * @param raw raw.
     * @return the computed result.
     */
    private UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Generated SiteID is not a valid UUID: " + raw, ex);
        }
    }
}
