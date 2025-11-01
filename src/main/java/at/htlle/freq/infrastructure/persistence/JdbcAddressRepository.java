package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Address;
import at.htlle.freq.domain.AddressRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class JdbcAddressRepository implements AddressRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAddressRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Address> mapper = (rs, n) -> new Address(
            rs.getObject("AddressID", UUID.class),
            rs.getString("Street"),
            rs.getString("CityID")
    );

    @Override
    public Optional<Address> findById(UUID id) {
        String sql = "SELECT AddressID, Street, CityID FROM Address WHERE AddressID = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<Address> findAll() {
        return jdbc.query("SELECT AddressID, Street, CityID FROM Address", mapper);
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM Address WHERE AddressID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

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
