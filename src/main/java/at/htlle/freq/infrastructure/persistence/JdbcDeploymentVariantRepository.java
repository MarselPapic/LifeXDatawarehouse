package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.DeploymentVariant;
import at.htlle.freq.domain.DeploymentVariantRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link DeploymentVariant} that centralizes all access to the
 * {@code DeploymentVariant} table and maps the retrieved variant data to domain objects.
 */
@Repository
public class JdbcDeploymentVariantRepository implements DeploymentVariantRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a new JdbcDeploymentVariantRepository instance and initializes it with the provided values.
     * @param jdbc jdbc.
     */
    public JdbcDeploymentVariantRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<DeploymentVariant> mapper = (rs, n) -> new DeploymentVariant(
            rs.getObject("VariantID", UUID.class),
            rs.getString("VariantCode"),
            rs.getString("VariantName"),
            rs.getString("Description"),
            rs.getObject("IsActive", Boolean.class)
    );

    /**
     * Finds By ID using the supplied criteria and returns the matching data.
     * @param id identifier.
     * @return the matching By ID.
     */
    @Override
    public Optional<DeploymentVariant> findById(UUID id) {
        String sql = """
            SELECT VariantID, VariantCode, VariantName, Description, IsActive
            FROM DeploymentVariant WHERE VariantID = :id
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    /**
     * Finds By Code using the supplied criteria and returns the matching data.
     * @param code code.
     * @return the matching By Code.
     */
    @Override
    public Optional<DeploymentVariant> findByCode(String code) {
        String sql = """
            SELECT VariantID, VariantCode, VariantName, Description, IsActive
            FROM DeploymentVariant WHERE VariantCode = :code
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("code", code), mapper));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    /**
     * Finds By Name using the supplied criteria and returns the matching data.
     * @param name name.
     * @return the matching By Name.
     */
    @Override
    public Optional<DeploymentVariant> findByName(String name) {
        String sql = """
            SELECT VariantID, VariantCode, VariantName, Description, IsActive
            FROM DeploymentVariant WHERE VariantName = :name
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("name", name), mapper));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    /**
     * Finds All using the supplied criteria and returns the matching data.
     * @return the matching All.
     */
    @Override
    public List<DeploymentVariant> findAll() {
        String sql = "SELECT VariantID, VariantCode, VariantName, Description, IsActive FROM DeploymentVariant";
        return jdbc.query(sql, mapper);
    }

    /**
     * Deletes the By ID from the underlying store.
     * @param id identifier.
     */
    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM DeploymentVariant WHERE VariantID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Persists deployment variants via INSERT or UPDATE.
     * <p>
     * All relevant fields are bound explicitly as named parameters to guarantee a consistent
     * projection between the SQL result and the {@link RowMapper}, making future extensions
     * easier to apply.
     * </p>
     *
     * @param dv deployment variant whose attributes map directly to the columns of the
     *           {@code DeploymentVariant} table.
     * @return the persisted variant including its primary key.
     */
    @Override
    public DeploymentVariant save(DeploymentVariant dv) {
        boolean isNew = dv.getVariantID() == null;
        if (isNew) {
            UUID id = UUID.randomUUID();
            dv.setVariantID(id);
            String sql = """
                INSERT INTO DeploymentVariant (VariantID, VariantCode, VariantName, Description, IsActive)
                VALUES (:id, :code, :name, :desc, :active)
                """;
            var params = new MapSqlParameterSource()
                    .addValue("id", dv.getVariantID())
                    .addValue("code", dv.getVariantCode())
                    .addValue("name", dv.getVariantName())
                    .addValue("desc", dv.getDescription())
                    .addValue("active", Boolean.TRUE.equals(dv.getActive()));
            jdbc.update(sql, params);
        } else {
            String sql = """
                UPDATE DeploymentVariant SET
                    VariantCode = :code,
                    VariantName = :name,
                    Description = :desc,
                    IsActive = :active
                WHERE VariantID = :id
                """;
            var params = new MapSqlParameterSource()
                    .addValue("id",    dv.getVariantID())
                    .addValue("code",  dv.getVariantCode())
                    .addValue("name",  dv.getVariantName())
                    .addValue("desc",  dv.getDescription())
                    .addValue("active", Boolean.TRUE.equals(dv.getActive()));
            jdbc.update(sql, params);
        }
        return dv;
    }
}
