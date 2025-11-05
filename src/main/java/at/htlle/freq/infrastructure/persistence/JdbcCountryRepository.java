package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Country;
import at.htlle.freq.domain.CountryRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC-gestütztes Repository für {@link Country}, das Lese- und Schreibzugriffe auf die Tabelle
 * {@code Country} bündelt und die Spalten {@code CountryCode} sowie {@code CountryName}
 * in Domänenobjekte überführt.
 */
@Repository
public class JdbcCountryRepository implements CountryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCountryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Country> mapper = (rs, n) -> new Country(
            rs.getString("CountryCode"),
            rs.getString("CountryName")
    );

    @Override
    public Optional<Country> findById(String code) {
        String sql = "SELECT CountryCode, CountryName FROM Country WHERE CountryCode = :code";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("code", code), mapper));
        } catch (Exception e) { return Optional.empty(); }
    }

    /**
     * Führt ein manuelles Upsert für Länder aus.
     * <p>
     * Mittels {@code SELECT COUNT(*)} wird geprüft, ob der Ländercode bereits existiert. Je nach
     * Ergebnis wird ein INSERT oder UPDATE ausgeführt. Die Verwendung von
     * {@link MapSqlParameterSource} stellt sicher, dass Parameter explizit an die Spaltennamen
     * gebunden werden und verhindert SQL-Injection.
     * </p>
     *
     * @param c Land, dessen Code und Name den Spalten {@code CountryCode} bzw. {@code CountryName}
     *          zugeordnet werden.
     * @return das gespeicherte Land.
     */
    @Override
    public Country save(Country c) {
        String existsSql = "SELECT COUNT(*) FROM Country WHERE CountryCode = :code";
        Integer cnt = jdbc.queryForObject(existsSql, new MapSqlParameterSource("code", c.getCountryCode()), Integer.class);
        boolean exists = cnt != null && cnt > 0;

        if (!exists) {
            String ins = "INSERT INTO Country (CountryCode, CountryName) VALUES (:code, :name)";
            jdbc.update(ins, new MapSqlParameterSource()
                    .addValue("code", c.getCountryCode())
                    .addValue("name", c.getCountryName()));
        } else {
            String upd = "UPDATE Country SET CountryName = :name WHERE CountryCode = :code";
            jdbc.update(upd, new MapSqlParameterSource()
                    .addValue("code", c.getCountryCode())
                    .addValue("name", c.getCountryName()));
        }
        return c;
    }

    @Override
    public List<Country> findAll() {
        return jdbc.query("SELECT CountryCode, CountryName FROM Country", mapper);
    }
}
