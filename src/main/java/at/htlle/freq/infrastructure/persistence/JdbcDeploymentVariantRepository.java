package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.DeploymentVariant;
import at.htlle.freq.domain.DeploymentVariantRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC-Repository für {@link DeploymentVariant}, das sämtliche Zugriffe auf die Tabelle
 * {@code DeploymentVariant} bündelt und Variantendaten in Domänenobjekte mappt.
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
     * Persistiert Deployment-Varianten via INSERT (mit {@code RETURNING VariantID}) oder UPDATE.
     * <p>
     * Alle relevanten Felder werden explizit als benannte Parameter gebunden, um eine konsistente
     * Projektion zwischen SQL-Ergebnis und {@link RowMapper} sicherzustellen und spätere
     * Erweiterungen einfacher zu gestalten.
     * </p>
     *
     * @param dv Deployment-Variante, deren Attribute direkt auf die Spalten der Tabelle
     *           {@code DeploymentVariant} gemappt werden.
     * @return die persistierte Variante samt Primärschlüssel.
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
