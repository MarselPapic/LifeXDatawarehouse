package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Clients;
import at.htlle.freq.domain.ClientsRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link Clients} that encapsulates access to the {@code Clients} table and
 * maps all tenant device properties into domain objects.
 */
@Repository
public class JdbcClientsRepository implements ClientsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a new JdbcClientsRepository instance and initializes it with the provided values.
     * @param jdbc jdbc.
     */
    public JdbcClientsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Clients> mapper = (rs, n) -> new Clients(
            rs.getObject("ClientID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getString("ClientName"),
            rs.getString("ClientBrand"),
            rs.getString("ClientSerialNr"),
            rs.getString("ClientOS"),
            rs.getString("PatchLevel"),
            rs.getString("InstallType"),
            rs.getString("WorkingPositionType"),
            rs.getString("OtherInstalledSoftware")
    );

    /**
     * Finds By ID using the supplied criteria and returns the matching data.
     * @param id identifier.
     * @return the matching By ID.
     */
    @Override
    public Optional<Clients> findById(UUID id) {
        String sql = """
            SELECT ClientID, SiteID, ClientName, ClientBrand, ClientSerialNr, ClientOS, PatchLevel, InstallType,
                   WorkingPositionType, OtherInstalledSoftware
            FROM Clients WHERE ClientID = :id
            """;
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)
            );
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Finds By Site using the supplied criteria and returns the matching data.
     * @param siteId site identifier.
     * @return the matching By Site.
     */
    @Override
    public List<Clients> findBySite(UUID siteId) {
        String sql = """
            SELECT ClientID, SiteID, ClientName, ClientBrand, ClientSerialNr, ClientOS, PatchLevel, InstallType,
                   WorkingPositionType, OtherInstalledSoftware
            FROM Clients WHERE SiteID = :sid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), mapper);
    }

    /**
     * Finds All using the supplied criteria and returns the matching data.
     * @return the matching All.
     */
    @Override
    public List<Clients> findAll() {
        return jdbc.query("""
            SELECT ClientID, SiteID, ClientName, ClientBrand, ClientSerialNr, ClientOS, PatchLevel, InstallType,
                   WorkingPositionType, OtherInstalledSoftware
            FROM Clients
            """, mapper);
    }

    /**
     * Stores client devices via INSERT or UPDATE statements on the {@code Clients} table.
     * <p>
     * Because the target database does not support {@code RETURNING}, new records generate their
     * UUID inside the application before executing the INSERT. UPDATE statements bind all columns
     * to avoid inconsistent partial updates and to keep the row mapping deterministic.
     * </p>
     *
     * @param c client entity whose fields are bound to identically named columns via
     *          {@link MapSqlParameterSource}.
     * @return the stored client with its {@code ClientID} populated.
     */
    @Override
    public Clients save(Clients c) {
        boolean isNew = (c.getClientID() == null);

        if (isNew) {
            // Generate the UUID within the application because H2 does not support RETURNING
            UUID newId = UUID.randomUUID();
            c.setClientID(newId);

            String sql = """
                INSERT INTO Clients (ClientID, SiteID, ClientName, ClientBrand,
                                     ClientSerialNr, ClientOS, PatchLevel, InstallType,
                                     WorkingPositionType, OtherInstalledSoftware)
                VALUES (:id, :site, :name, :brand, :sn, :os, :pl, :it, :wpt, :otherSw)
                """;

            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", newId)
                    .addValue("site", c.getSiteID())
                    .addValue("name", c.getClientName())
                    .addValue("brand", c.getClientBrand())
                    .addValue("sn", c.getClientSerialNr())
                    .addValue("os", c.getClientOS())
                    .addValue("pl", c.getPatchLevel())
                    .addValue("it", c.getInstallType())
                    .addValue("wpt", c.getWorkingPositionType())
                    .addValue("otherSw", c.getOtherInstalledSoftware()));
        } else {
            String sql = """
                UPDATE Clients SET
                    SiteID = :site,
                    ClientName = :name,
                    ClientBrand = :brand,
                    ClientSerialNr = :sn,
                    ClientOS = :os,
                    PatchLevel = :pl,
                    InstallType = :it,
                    WorkingPositionType = :wpt,
                    OtherInstalledSoftware = :otherSw
                WHERE ClientID = :id
                """;

            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", c.getClientID())
                    .addValue("site", c.getSiteID())
                    .addValue("name", c.getClientName())
                    .addValue("brand", c.getClientBrand())
                    .addValue("sn", c.getClientSerialNr())
                    .addValue("os", c.getClientOS())
                    .addValue("pl", c.getPatchLevel())
                    .addValue("it", c.getInstallType())
                    .addValue("wpt", c.getWorkingPositionType())
                    .addValue("otherSw", c.getOtherInstalledSoftware()));
        }

        return c;
    }
}
