package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.Project;
import at.htlle.freq.domain.ProjectLifecycleStatus;
import at.htlle.freq.domain.ProjectRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * JDBC repository for {@link Project} that manages all project data in the {@code Project} table
 * and maps relationships to deployment variants, accounts, and addresses.
 */
@Repository
public class JdbcProjectRepository implements ProjectRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a repository backed by a {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc JDBC template used for all project queries.
     */
    public JdbcProjectRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<Project> mapper = (rs, n) -> new Project(
            rs.getObject("ProjectID", UUID.class),
            rs.getString("ProjectSAPID"),
            rs.getString("ProjectName"),
            rs.getObject("DeploymentVariantID", UUID.class),
            rs.getString("BundleType"),
            rs.getString("CreateDateTime"),
            ProjectLifecycleStatus.fromString(rs.getString("LifecycleStatus")),
            rs.getObject("AccountID", UUID.class),
            rs.getObject("AddressID", UUID.class),
            rs.getString("SpecialNotes")
    );

    /**
     * Loads a project by its primary key.
     *
     * @param id identifier of the project row.
     * @return optional project when found.
     */
    @Override
    public Optional<Project> findById(UUID id) {
        String sql = """
            SELECT ProjectID, ProjectSAPID, ProjectName, DeploymentVariantID, BundleType,
                   CreateDateTime, LifecycleStatus, AccountID, AddressID, SpecialNotes
            FROM Project WHERE ProjectID = :id
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    /**
     * Loads a project by its SAP identifier.
     *
     * @param sapId SAP identifier stored in {@code ProjectSAPID}.
     * @return optional project when found.
     */
    @Override
    public Optional<Project> findBySapId(String sapId) {
        String sql = """
            SELECT ProjectID, ProjectSAPID, ProjectName, DeploymentVariantID, BundleType,
                   CreateDateTime, LifecycleStatus, AccountID, AddressID, SpecialNotes
            FROM Project WHERE ProjectSAPID = :sap
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("sap", sapId), mapper));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    /**
     * Retrieves every project row.
     *
     * @return all projects from the {@code Project} table.
     */
    @Override
    public List<Project> findAll() {
        String sql = """
            SELECT ProjectID, ProjectSAPID, ProjectName, DeploymentVariantID, BundleType,
                   CreateDateTime, LifecycleStatus, AccountID, AddressID, SpecialNotes
            FROM Project
            """;
        return jdbc.query(sql, mapper);
    }

    /**
     * Deletes a project row by its primary key.
     *
     * @param id identifier of the project to remove.
     */
    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM Project WHERE ProjectID = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Persists projects while setting every column explicitly.
     * <p>
     * The INSERT statement leverages {@code RETURNING ProjectID} to capture the database-generated
     * primary key. UPDATE statements bind all attributes, including optional
     * {@link ProjectLifecycleStatus} values, to keep the {@link RowMapper} mapping aligned and to
     * synchronize dependent foreign keys.
     * </p>
     *
     * @param p project entity with referenced IDs that are mapped to the columns of the
     *          {@code Project} table via named parameters.
     * @return the persisted project including its {@code ProjectID}.
     */
    @Override
    public Project save(Project p) {
        boolean isNew = p.getProjectID() == null;
        if (isNew) {
            String sql = """
                INSERT INTO Project (ProjectSAPID, ProjectName, DeploymentVariantID, BundleType,
                                     CreateDateTime, LifecycleStatus, AccountID, AddressID, SpecialNotes)
                VALUES (:sap, :name, :dv, :bundle, COALESCE(:created, CURRENT_DATE), :status, :account, :address, :notes)
                RETURNING ProjectID
                """;
            var params = new MapSqlParameterSource()
                    .addValue("sap",     p.getProjectSAPID())
                    .addValue("name",    p.getProjectName())
                    .addValue("dv",      p.getDeploymentVariantID())
                    .addValue("bundle",  p.getBundleType())
                    .addValue("created", p.getCreateDateTime())
                    .addValue("status",  p.getLifecycleStatus() != null ? p.getLifecycleStatus().name() : null)
                    .addValue("account", p.getAccountID())
                    .addValue("address", p.getAddressID())
                    .addValue("notes",   p.getSpecialNotes());
            UUID id = jdbc.queryForObject(sql, params, UUID.class);
            p.setProjectID(id);
        } else {
            String sql = """
                UPDATE Project SET
                    ProjectSAPID = :sap,
                    ProjectName = :name,
                    DeploymentVariantID = :dv,
                    BundleType = :bundle,
                    CreateDateTime = COALESCE(:created, CURRENT_DATE),
                    LifecycleStatus = :status,
                    AccountID = :account,
                    AddressID = :address,
                    SpecialNotes = :notes
                WHERE ProjectID = :id
                """;
            var params = new MapSqlParameterSource()
                    .addValue("id",      p.getProjectID())
                    .addValue("sap",     p.getProjectSAPID())
                    .addValue("name",    p.getProjectName())
                    .addValue("dv",      p.getDeploymentVariantID())
                    .addValue("bundle",  p.getBundleType())
                    .addValue("created", p.getCreateDateTime())
                    .addValue("status",  p.getLifecycleStatus() != null ? p.getLifecycleStatus().name() : null)
                    .addValue("account", p.getAccountID())
                    .addValue("address", p.getAddressID())
                    .addValue("notes",   p.getSpecialNotes());
            jdbc.update(sql, params);
        }
        return p;
    }
}
