package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Clients;
import at.htlle.freq.domain.ClientsRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC-Repository für {@link Clients}, das den Zugriff auf die Tabelle {@code Clients}
 * kapselt und alle Geräteeigenschaften der Mandanten in Domänenobjekte transformiert.
 */
@Repository
public class JdbcClientsRepository implements ClientsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcClientsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Clients> mapper = (rs, n) -> new Clients(
            rs.getObject("ClientID", UUID.class),
            rs.getObject("SiteID", UUID.class),
            rs.getString("ClientName"),
            rs.getString("ClientBrand"),
            rs.getString("ClientSerialNr"),
            rs.getString("ClientOS"),
            rs.getString("PatchLevel"),
            rs.getString("InstallType")
    );

    @Override
    public Optional<Clients> findById(UUID id) {
        String sql = """
            SELECT ClientID, SiteID, ClientName, ClientBrand, ClientSerialNr, ClientOS, PatchLevel, InstallType
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

    @Override
    public List<Clients> findBySite(UUID siteId) {
        String sql = """
            SELECT ClientID, SiteID, ClientName, ClientBrand, ClientSerialNr, ClientOS, PatchLevel, InstallType
            FROM Clients WHERE SiteID = :sid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), mapper);
    }

    @Override
    public List<Clients> findAll() {
        return jdbc.query("""
            SELECT ClientID, SiteID, ClientName, ClientBrand, ClientSerialNr, ClientOS, PatchLevel, InstallType
            FROM Clients
            """, mapper);
    }

    /**
     * Speichert Client-Geräte via INSERT oder UPDATE in der Tabelle {@code Clients}.
     * <p>
     * Da die Zieldatenbank kein {@code RETURNING} unterstützt, wird für neue Datensätze eine
     * UUID im Anwendungscode generiert und anschließend in das Insert übernommen. Updates
     * binden sämtliche Spalten, um inkonsistente Teilupdates zu vermeiden und das Row-Mapping
     * deterministisch zu halten.
     * </p>
     *
     * @param c Client-Objekt, dessen Felder über {@link MapSqlParameterSource} auf gleichnamige
     *          Spalten gemappt werden.
     * @return der gespeicherte Client mit gesetzter {@code ClientID}.
     */
    @Override
    public Clients save(Clients c) {
        boolean isNew = (c.getClientID() == null);

        if (isNew) {
            // UUID selbst erzeugen, da H2 RETURNING nicht kennt
            UUID newId = UUID.randomUUID();
            c.setClientID(newId);

            String sql = """
                INSERT INTO Clients (ClientID, SiteID, ClientName, ClientBrand,
                                     ClientSerialNr, ClientOS, PatchLevel, InstallType)
                VALUES (:id, :site, :name, :brand, :sn, :os, :pl, :it)
                """;

            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", newId)
                    .addValue("site", c.getSiteID())
                    .addValue("name", c.getClientName())
                    .addValue("brand", c.getClientBrand())
                    .addValue("sn", c.getClientSerialNr())
                    .addValue("os", c.getClientOS())
                    .addValue("pl", c.getPatchLevel())
                    .addValue("it", c.getInstallType()));
        } else {
            String sql = """
                UPDATE Clients SET
                    SiteID = :site,
                    ClientName = :name,
                    ClientBrand = :brand,
                    ClientSerialNr = :sn,
                    ClientOS = :os,
                    PatchLevel = :pl,
                    InstallType = :it
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
                    .addValue("it", c.getInstallType()));
        }

        return c;
    }
}
