package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Country;
import at.htlle.freq.domain.CountryRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC-backed repository for {@link Country} that bundles read and write operations on the
 * {@code Country} table and maps the columns {@code CountryCode} and {@code CountryName} to
 * domain objects.
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
     * Performs a manual upsert for country entries.
     * <p>
     * A {@code SELECT COUNT(*)} determines whether the country code already exists. Depending on
     * the outcome, the method runs an INSERT or an UPDATE. Using {@link MapSqlParameterSource}
     * ensures parameters are bound explicitly to the column names and protects against SQL
     * injection.
     * </p>
     *
     * @param c country entity whose code and name map to the {@code CountryCode} and
     *          {@code CountryName} columns.
     * @return the stored country.
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

    @Override
    public void deleteById(String code) {
        String sql = "DELETE FROM Country WHERE CountryCode = :code";
        jdbc.update(sql, new MapSqlParameterSource("code", code));
    }
}
