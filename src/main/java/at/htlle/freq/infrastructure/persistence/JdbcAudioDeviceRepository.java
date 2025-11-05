package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.AudioDevice;
import at.htlle.freq.domain.AudioDeviceRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Repository für {@link AudioDevice}-Entitäten, das direkte JDBC-Zugriffe auf die Tabelle
 * {@code AudioDevice} kapselt und alle relevanten Spalten in Domänenobjekte abbildet.
 */
@Repository
public class JdbcAudioDeviceRepository implements AudioDeviceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAudioDeviceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<AudioDevice> mapper = (rs, n) -> new AudioDevice(
            rs.getObject("AudioDeviceID", UUID.class),
            rs.getObject("ClientID", UUID.class),
            rs.getString("AudioDeviceBrand"),
            rs.getString("DeviceSerialNr"),
            rs.getString("AudioDeviceFirmware"),
            rs.getString("DeviceType")
    );

    @Override
    public Optional<AudioDevice> findById(UUID id) {
        String sql = """
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr, AudioDeviceFirmware, DeviceType
            FROM AudioDevice WHERE AudioDeviceID = :id
            """;
        try { return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper)); }
        catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<AudioDevice> findByClient(UUID clientId) {
        String sql = """
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr, AudioDeviceFirmware, DeviceType
            FROM AudioDevice WHERE ClientID = :cid
            """;
        return jdbc.query(sql, new MapSqlParameterSource("cid", clientId), mapper);
    }

    @Override
    public List<AudioDevice> findAll() {
        return jdbc.query("""
            SELECT AudioDeviceID, ClientID, AudioDeviceBrand, DeviceSerialNr, AudioDeviceFirmware, DeviceType
            FROM AudioDevice
            """, mapper);
    }

    /**
     * Führt INSERT- oder UPDATE-Operationen auf der Tabelle {@code AudioDevice} aus.
     * <p>
     * Bei neuen Datensätzen wird die primäre ID durch die Datenbank mittels {@code RETURNING}
     * erzeugt und anschließend in das Domänenobjekt übernommen. Updates binden sämtliche Felder,
     * um die Konsistenz mit dem {@link RowMapper} sicherzustellen und partielle Updates zu
     * vermeiden.
     * </p>
     *
     * @param d Audiogerät mit Eigenschaften, die über benannte Parameter in die SQL-Anweisung
     *          eingetragen werden.
     * @return das persistierte Audiogerät mit gesetzter {@code AudioDeviceID}.
     */
    @Override
    public AudioDevice save(AudioDevice d) {
        boolean isNew = d.getAudioDeviceID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO AudioDevice (ClientID, AudioDeviceBrand, DeviceSerialNr, AudioDeviceFirmware, DeviceType)
                VALUES (:client, :brand, :sn, :fw, :type)
                RETURNING AudioDeviceID
                """;
            UUID id = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("client", d.getClientID())
                    .addValue("brand", d.getAudioDeviceBrand())
                    .addValue("sn", d.getDeviceSerialNr())
                    .addValue("fw", d.getAudioDeviceFirmware())
                    .addValue("type", d.getDeviceType()), UUID.class);
            d.setAudioDeviceID(id);
        } else {
            String sql = """
                UPDATE AudioDevice SET
                    ClientID = :client, AudioDeviceBrand = :brand, DeviceSerialNr = :sn,
                    AudioDeviceFirmware = :fw, DeviceType = :type
                WHERE AudioDeviceID = :id
                """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", d.getAudioDeviceID())
                    .addValue("client", d.getClientID())
                    .addValue("brand", d.getAudioDeviceBrand())
                    .addValue("sn", d.getDeviceSerialNr())
                    .addValue("fw", d.getAudioDeviceFirmware())
                    .addValue("type", d.getDeviceType()));
        }
        return d;
    }
}
