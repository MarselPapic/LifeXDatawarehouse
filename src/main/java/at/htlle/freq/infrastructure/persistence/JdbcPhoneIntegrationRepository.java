package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.PhoneIntegration;
import at.htlle.freq.domain.PhoneIntegrationRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link PhoneIntegration} that operates on the {@code PhoneIntegration}
 * table and manages phone integrations for sites.
 */
@Repository
public class JdbcPhoneIntegrationRepository implements PhoneIntegrationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a new JdbcPhoneIntegrationRepository instance and initializes it with the provided values.
     * @param jdbc jdbc.
     */
    public JdbcPhoneIntegrationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<PhoneIntegration> mapper = (rs, n) -> new PhoneIntegration(
            rs.getObject("PhoneIntegrationID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getString("PhoneType"),
            rs.getString("PhoneBrand"),
            rs.getString("InterfaceName"),
            rs.getObject("Capacity", Integer.class),
            rs.getString("PhoneFirmware")
    );

    /**
     * Finds By ID using the supplied criteria and returns the matching data.
     * @param id identifier.
     * @return the matching By ID.
     */
    @Override
    public Optional<PhoneIntegration> findById(UUID id) {
        String sql = """
            SELECT PhoneIntegrationID, SiteID, PhoneType, PhoneBrand, InterfaceName, Capacity, PhoneFirmware
            FROM PhoneIntegration WHERE PhoneIntegrationID = :id
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
    public List<PhoneIntegration> findBySite(UUID siteId) {
        String sql = """
            SELECT PhoneIntegrationID, SiteID, PhoneType, PhoneBrand, InterfaceName, Capacity, PhoneFirmware
            FROM PhoneIntegration WHERE SiteID = :sid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), mapper);
    }

    /**
     * Finds All using the supplied criteria and returns the matching data.
     * @return the matching All.
     */
    @Override
    public List<PhoneIntegration> findAll() {
        return jdbc.query("""
            SELECT PhoneIntegrationID, SiteID, PhoneType, PhoneBrand, InterfaceName, Capacity, PhoneFirmware
            FROM PhoneIntegration
            """, mapper);
    }

    /**
     * Deletes the By ID from the underlying store.
     * @param id identifier.
     */
    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM PhoneIntegration WHERE PhoneIntegrationID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Persists phone integrations via INSERT or UPDATE statements on the {@code PhoneIntegration}
     * table.
     * <p>
     * Newly generated IDs are obtained from the database through {@code RETURNING PhoneIntegrationID}.
     * Every parameter is named explicitly to keep the column mapping and the structure expected by
     * the {@link RowMapper} in sync.
     * </p>
     *
     * @param p phone integration entity whose fields map to the columns of the same name.
     * @return the stored record including its identifier.
     */
    @Override
    public PhoneIntegration save(PhoneIntegration p) {
        boolean isNew = p.getPhoneIntegrationID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO PhoneIntegration (SiteID, PhoneType, PhoneBrand, InterfaceName, Capacity, PhoneFirmware)
                VALUES (:site, :type, :brand, :iface, :cap, :fw)
                RETURNING PhoneIntegrationID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("site", p.getSiteID())
                    .addValue("type", p.getPhoneType())
                    .addValue("brand", p.getPhoneBrand())
                    .addValue("iface", p.getInterfaceName())
                    .addValue("cap", p.getCapacity())
                    .addValue("fw", p.getPhoneFirmware()), UUID.class);
            p.setPhoneIntegrationID(id);
        } else {
            String sql = """
                UPDATE PhoneIntegration SET
                    SiteID = :site, PhoneType = :type, PhoneBrand = :brand,
                    InterfaceName = :iface, Capacity = :cap, PhoneFirmware = :fw
                WHERE PhoneIntegrationID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", p.getPhoneIntegrationID())
                    .addValue("site", p.getSiteID())
                    .addValue("type", p.getPhoneType())
                    .addValue("brand", p.getPhoneBrand())
                    .addValue("iface", p.getInterfaceName())
                    .addValue("cap", p.getCapacity())
                    .addValue("fw", p.getPhoneFirmware()));
        }
        return p;
    }
}
