package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.ProjectSiteAssignmentRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repository abstraction for persisting Jdbc Project Site Assignment data.
 */
@Repository
public class JdbcProjectSiteAssignmentRepository implements ProjectSiteAssignmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a new JdbcProjectSiteAssignmentRepository instance and initializes it with the provided values.
     * @param jdbc jdbc.
     */
    public JdbcProjectSiteAssignmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Finds Project IDs By Site using the supplied criteria and returns the matching data.
     * @param siteId site identifier.
     * @return the matching Project IDs By Site.
     */
    @Override
    public List<UUID> findProjectIdsBySite(UUID siteId) {
        String sql = "SELECT ProjectID FROM ProjectSite WHERE SiteID = :sid ORDER BY ProjectID";
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), (rs, n) -> rs.getObject("ProjectID", UUID.class));
    }

    /**
     * Finds Site IDs By Project using the supplied criteria and returns the matching data.
     * @param projectId project identifier.
     * @return the matching Site IDs By Project.
     */
    @Override
    public List<UUID> findSiteIdsByProject(UUID projectId) {
        String sql = "SELECT SiteID FROM ProjectSite WHERE ProjectID = :pid ORDER BY SiteID";
        return jdbc.query(sql, new MapSqlParameterSource("pid", projectId), (rs, n) -> rs.getObject("SiteID", UUID.class));
    }

    /**
     * Replaces Projects For Site with the supplied collection.
     * @param siteId site identifier.
     * @param projectIds project identifiers.
     */
    @Override
    public void replaceProjectsForSite(UUID siteId, Collection<UUID> projectIds) {
        MapSqlParameterSource params = new MapSqlParameterSource("sid", siteId);
        jdbc.update("DELETE FROM ProjectSite WHERE SiteID = :sid", params);
        bulkInsert(projectIds, params, true);
    }

    /**
     * Replaces Sites For Project with the supplied collection.
     * @param projectId project identifier.
     * @param siteIds site identifiers.
     */
    @Override
    public void replaceSitesForProject(UUID projectId, Collection<UUID> siteIds) {
        MapSqlParameterSource params = new MapSqlParameterSource("pid", projectId);
        jdbc.update("DELETE FROM ProjectSite WHERE ProjectID = :pid", params);
        bulkInsert(siteIds, params, false);
    }

    /**
     * Executes the bulk Insert operation.
     * @param ids identifiers.
     * @param baseParams base params.
     * @param idsRepresentProjects ids represent projects.
     */
    private void bulkInsert(Collection<UUID> ids, MapSqlParameterSource baseParams, boolean idsRepresentProjects) {
        List<UUID> distinct = ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return;
        }

        List<MapSqlParameterSource> batch = distinct.stream()
                .map(id -> new MapSqlParameterSource()
                        .addValues(baseParams.getValues())
                        .addValue(idsRepresentProjects ? "pid" : "sid", id))
                .collect(Collectors.toList());

        String sql = idsRepresentProjects
                ? "INSERT INTO ProjectSite (ProjectID, SiteID) VALUES (:pid, :sid)"
                : "INSERT INTO ProjectSite (ProjectID, SiteID) VALUES (:pid, :sid)";

        jdbc.batchUpdate(sql, batch.toArray(MapSqlParameterSource[]::new));
    }
}
