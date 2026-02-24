package at.htlle.freq.infrastructure.persistence;

import at.htlle.freq.domain.ProjectSiteAssignmentRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        String sql = """
                SELECT ProjectID
                FROM ProjectSite
                WHERE SiteID = :sid
                  AND IsArchived = FALSE
                ORDER BY ProjectID
                """;
        return jdbc.query(sql, new MapSqlParameterSource("sid", siteId), (rs, n) -> rs.getObject("ProjectID", UUID.class));
    }

    /**
     * Finds Site IDs By Project using the supplied criteria and returns the matching data.
     * @param projectId project identifier.
     * @return the matching Site IDs By Project.
     */
    @Override
    public List<UUID> findSiteIdsByProject(UUID projectId) {
        String sql = """
                SELECT SiteID
                FROM ProjectSite
                WHERE ProjectID = :pid
                  AND IsArchived = FALSE
                ORDER BY SiteID
                """;
        return jdbc.query(sql, new MapSqlParameterSource("pid", projectId), (rs, n) -> rs.getObject("SiteID", UUID.class));
    }

    /**
     * Replaces Projects For Site with the supplied collection.
     * @param siteId site identifier.
     * @param projectIds project identifiers.
     */
    @Override
    public void replaceProjectsForSite(UUID siteId, Collection<UUID> projectIds) {
        Set<UUID> target = normalize(projectIds);
        MapSqlParameterSource params = new MapSqlParameterSource("sid", siteId);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT ProjectID, IsArchived
                FROM ProjectSite
                WHERE SiteID = :sid
                """, params);
        reconcile(target, existing, true, params);
    }

    /**
     * Replaces Sites For Project with the supplied collection.
     * @param projectId project identifier.
     * @param siteIds site identifiers.
     */
    @Override
    public void replaceSitesForProject(UUID projectId, Collection<UUID> siteIds) {
        Set<UUID> target = normalize(siteIds);
        MapSqlParameterSource params = new MapSqlParameterSource("pid", projectId);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT SiteID, IsArchived
                FROM ProjectSite
                WHERE ProjectID = :pid
                """, params);
        reconcile(target, existing, false, params);
    }

    /**
     * Executes the bulk Insert operation.
     * @param ids identifiers.
     * @param baseParams base params.
     * @param idsRepresentProjects ids represent projects.
     */
    private void reconcile(Set<UUID> targetIds,
                           List<Map<String, Object>> existingRows,
                           boolean existingIdsRepresentProjects,
                           MapSqlParameterSource baseParams) {
        String column = existingIdsRepresentProjects ? "ProjectID" : "SiteID";
        Set<UUID> existingAll = new LinkedHashSet<>();
        List<UUID> toArchive = new java.util.ArrayList<>();
        List<UUID> toRestore = new java.util.ArrayList<>();

        for (Map<String, Object> row : existingRows) {
            UUID existingId = coerceUuid(row.get(column));
            if (existingId == null) {
                continue;
            }
            existingAll.add(existingId);
            boolean archived = Boolean.TRUE.equals(row.get("IsArchived"));
            if (targetIds.contains(existingId)) {
                if (archived) {
                    toRestore.add(existingId);
                }
            } else if (!archived) {
                toArchive.add(existingId);
            }
        }

        List<UUID> toInsert = targetIds.stream()
                .filter(id -> !existingAll.contains(id))
                .collect(Collectors.toList());

        batchArchive(toArchive, baseParams, existingIdsRepresentProjects);
        batchRestore(toRestore, baseParams, existingIdsRepresentProjects);
        batchInsert(toInsert, baseParams, existingIdsRepresentProjects);
    }

    private Set<UUID> normalize(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void batchArchive(List<UUID> ids, MapSqlParameterSource baseParams, boolean idsRepresentProjects) {
        if (ids.isEmpty()) {
            return;
        }
        String sql = idsRepresentProjects
                ? """
                  UPDATE ProjectSite
                     SET IsArchived = TRUE,
                         ArchivedAt = CURRENT_TIMESTAMP,
                         ArchivedBy = 'system'
                   WHERE SiteID = :sid
                     AND ProjectID = :pid
                     AND IsArchived = FALSE
                  """
                : """
                  UPDATE ProjectSite
                     SET IsArchived = TRUE,
                         ArchivedAt = CURRENT_TIMESTAMP,
                         ArchivedBy = 'system'
                   WHERE ProjectID = :pid
                     AND SiteID = :sid
                     AND IsArchived = FALSE
                  """;
        MapSqlParameterSource[] batch = toBatchParams(ids, baseParams, idsRepresentProjects);
        jdbc.batchUpdate(sql, batch);
    }

    private void batchRestore(List<UUID> ids, MapSqlParameterSource baseParams, boolean idsRepresentProjects) {
        if (ids.isEmpty()) {
            return;
        }
        String sql = idsRepresentProjects
                ? """
                  UPDATE ProjectSite
                     SET IsArchived = FALSE,
                         ArchivedAt = NULL,
                         ArchivedBy = NULL
                   WHERE SiteID = :sid
                     AND ProjectID = :pid
                     AND IsArchived = TRUE
                  """
                : """
                  UPDATE ProjectSite
                     SET IsArchived = FALSE,
                         ArchivedAt = NULL,
                         ArchivedBy = NULL
                   WHERE ProjectID = :pid
                     AND SiteID = :sid
                     AND IsArchived = TRUE
                  """;
        MapSqlParameterSource[] batch = toBatchParams(ids, baseParams, idsRepresentProjects);
        jdbc.batchUpdate(sql, batch);
    }

    private void batchInsert(List<UUID> ids, MapSqlParameterSource baseParams, boolean idsRepresentProjects) {
        if (ids.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO ProjectSite (ProjectID, SiteID) VALUES (:pid, :sid)";
        MapSqlParameterSource[] batch = toBatchParams(ids, baseParams, idsRepresentProjects);
        jdbc.batchUpdate(sql, batch);
    }

    private MapSqlParameterSource[] toBatchParams(List<UUID> ids, MapSqlParameterSource baseParams, boolean idsRepresentProjects) {
        return ids.stream()
                .map(id -> new MapSqlParameterSource()
                        .addValues(baseParams.getValues())
                        .addValue(idsRepresentProjects ? "pid" : "sid", id))
                .toArray(MapSqlParameterSource[]::new);
    }

    private UUID coerceUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
