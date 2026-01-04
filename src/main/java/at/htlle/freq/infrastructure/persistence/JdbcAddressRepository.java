package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Address;
import at.htlle.freq.domain.AddressRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link Address} entities that provides CRUD operations for the
 * {@code Address} table and maps the columns {@code AddressID}, {@code Street}, and
 * {@code CityID} to domain objects.
 */
@Repository
public class JdbcAddressRepository implements AddressRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a repository backed by a {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc JDBC template used for address queries.
     */
    public JdbcAddressRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Address> mapper = (rs, n) -> new Address(
            rs.getObject("AddressID", UUID.class),
            rs.getString("Street"),
            rs.getString("CityID")
    );

    /**
     * Loads an address by its primary key.
     *
     * @param id identifier of the address row.
     * @return optional address when found.
     */
    @Override
    public Optional<Address> findById(UUID id) {
        String sql = "SELECT AddressID, Street, CityID FROM Address WHERE AddressID = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper));
        } catch (Exception e) { return Optional.empty(); }
    }

    /**
     * Retrieves all addresses from the {@code Address} table.
     *
     * @return all address rows.
     */
    @Override
    public List<Address> findAll() {
        return jdbc.query("SELECT AddressID, Street, CityID FROM Address", mapper);
    }

    /**
     * Deletes an address row by its primary key.
     *
     * @param id identifier of the address to remove.
     */
    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM Address WHERE AddressID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Persists addresses in the {@code Address} table.
     * <p>
     * New records are inserted with {@code RETURNING AddressID}, allowing the database-generated
     * identifier to be written back into the domain object immediately. Existing entries are
     * updated in full, with parameters mapped to columns based on the field names.
     * </p>
     *
     * @param a address whose attributes are bound via {@link MapSqlParameterSource}.
     * @return the stored address with its {@code AddressID} set.
     */
    @Override
    public Address save(Address a) {
        boolean isNew = a.getAddressID() == null;
        if (isNew) {
            String sql = """
            INSERT INTO Address (Street, CityID)
            VALUES (:street, :city)
            RETURNING AddressID
            """;
            UUID id = jdbc.queryForObject(sql,
                    new MapSqlParameterSource()
                            .addValue("street", a.getStreet())
                            .addValue("city", a.getCityID()),
                    UUID.class);
            a.setAddressID(id);
        } else {
            String sql = """
            UPDATE Address SET Street = :street, CityID = :city
            WHERE AddressID = :id
            """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", a.getAddressID())
                    .addValue("street", a.getStreet())
                    .addValue("city", a.getCityID()));
        }
        return a;
    }

}
