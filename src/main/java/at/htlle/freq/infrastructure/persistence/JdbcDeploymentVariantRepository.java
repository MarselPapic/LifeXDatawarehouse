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

    public JdbcDeploymentVariantRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<DeploymentVariant> mapper = (rs, n) -> new DeploymentVariant(
            rs.getObject("VariantID", UUID.class),
            rs.getString("VariantCode"),
            rs.getString("VariantName"),
            rs.getString("Description"),
            rs.getBoolean("IsActive")
    );

    @Override
    public Optional<DeploymentVariant> findById(UUID id) {
        String sql = """
            SELECT VariantID, VariantCode, VariantName, Description, IsActive
            FROM DeploymentVariant WHERE VariantID = :id
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<DeploymentVariant> findByCode(String code) {
        String sql = """
            SELECT VariantID, VariantCode, VariantName, Description, IsActive
            FROM DeploymentVariant WHERE VariantCode = :code
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("code", code), mapper));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<DeploymentVariant> findByName(String name) {
        String sql = """
            SELECT VariantID, VariantCode, VariantName, Description, IsActive
            FROM DeploymentVariant WHERE VariantName = :name
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("name", name), mapper));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<DeploymentVariant> findAll() {
        String sql = "SELECT VariantID, VariantCode, VariantName, Description, IsActive FROM DeploymentVariant";
        return jdbc.query(sql, mapper);
    }

    /**
     * Persists deployment variants via INSERT (with {@code RETURNING VariantID}) or UPDATE.
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
            String sql = """
                INSERT INTO DeploymentVariant (VariantCode, VariantName, Description, IsActive)
                VALUES (:code, :name, :desc, :active)
                RETURNING VariantID
                """;
            var params = new MapSqlParameterSource()
                    .addValue("code", dv.getVariantCode())
                    .addValue("name", dv.getVariantName())
                    .addValue("desc", dv.getDescription())
                    .addValue("active", dv.isActive());
            UUID id = jdbc.queryForObject(sql, params, UUID.class);
            dv.setVariantID(id);
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
                    .addValue("active",dv.isActive());
            jdbc.update(sql, params);
        }
        return dv;
    }
}
