package at.htlle.freq.web;

import at.htlle.freq.application.ArchiveService;
import at.htlle.freq.application.InstalledSoftwareService;
import at.htlle.freq.application.ProjectSiteAssignmentService;
import at.htlle.freq.application.SiteService;
import at.htlle.freq.application.dto.SiteSoftwareOverviewEntry;
import at.htlle.freq.domain.ArchiveState;
import at.htlle.freq.domain.InstalledSoftwareStatus;
import at.htlle.freq.domain.Site;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import at.htlle.freq.web.dto.InstalledSoftwareStatusUpdateRequest;
import at.htlle.freq.web.dto.SiteDetailResponse;
import at.htlle.freq.web.dto.SiteSoftwareSummary;
import at.htlle.freq.web.dto.SiteUpsertRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Fully featured CRUD controller for sites.
 *
 * <p>Uses {@link NamedParameterJdbcTemplate} for database operations.</p>
 */
@RestController
@RequestMapping("/sites")
public class SiteController {

    private final NamedParameterJdbcTemplate jdbc;
    private final SiteService siteService;
    private final InstalledSoftwareService installedSoftwareService;
    private final ProjectSiteAssignmentService projectSites;
    private final AuditLogger audit;
    private final ArchiveService archiveService;
    private static final String TABLE = "Site";

    /**
     * Creates a controller backed by a {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc JDBC template used for ad-hoc site queries.
     * @param siteService service used for site CRUD operations.
     * @param installedSoftwareService service managing installed software assignments.
     * @param projectSites service that maintains site/project relationships.
     */
    @Autowired
    public SiteController(NamedParameterJdbcTemplate jdbc, SiteService siteService,
                          InstalledSoftwareService installedSoftwareService,
                          ProjectSiteAssignmentService projectSites,
                          AuditLogger audit,
                          ArchiveService archiveService) {
        this.jdbc = jdbc;
        this.siteService = siteService;
        this.installedSoftwareService = installedSoftwareService;
        this.projectSites = projectSites;
        this.audit = audit;
        this.archiveService = archiveService;
    }

    /**
     * Backwards-compatible constructor for tests.
     */
    public SiteController(NamedParameterJdbcTemplate jdbc, SiteService siteService,
                          InstalledSoftwareService installedSoftwareService,
                          ProjectSiteAssignmentService projectSites,
                          AuditLogger audit) {
        this(jdbc, siteService, installedSoftwareService, projectSites, audit, null);
    }

    /**
     * Returns software aggregation information per site for the requested status.
     *
     * <p>Path: {@code GET /sites/software-summary?status=Installed}</p>
     *
     * @param status optional installed software status (defaults to {@code Installed}).
     * @return list of summary rows.
     */
    @GetMapping("/software-summary")
    public List<SiteSoftwareSummary> getSoftwareSummary(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "archiveState", required = false) String archiveStateRaw) {
        InstalledSoftwareStatus resolved;
        ArchiveState archiveState = parseArchiveState(archiveStateRaw);
        try {
            resolved = (status == null || status.isBlank())
                    ? InstalledSoftwareStatus.INSTALLED
                    : InstalledSoftwareStatus.from(status);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        String sql = """
            SELECT s.SiteID   AS site_id,
                   s.SiteName AS site_name,
                   COUNT(isw.InstalledSoftwareID) AS status_count
            FROM Site s
            LEFT JOIN InstalledSoftware isw
                   ON isw.SiteID = s.SiteID
                  AND LOWER(isw.Status) = LOWER(:status)
                  AND (:archived = 'ALL'
                       OR (:archived = 'ACTIVE' AND isw.IsArchived = FALSE)
                       OR (:archived = 'ARCHIVED' AND isw.IsArchived = TRUE))
            WHERE (:archived = 'ALL'
                   OR (:archived = 'ACTIVE' AND s.IsArchived = FALSE)
                   OR (:archived = 'ARCHIVED' AND s.IsArchived = TRUE))
            GROUP BY s.SiteID, s.SiteName
            ORDER BY s.SiteName NULLS LAST, s.SiteID
            """;

        return jdbc.query(sql, new MapSqlParameterSource("status", resolved.dbValue())
                        .addValue("archived", archiveState.name()),
                (rs, rowNum) -> new SiteSoftwareSummary(
                        rs.getObject("site_id", UUID.class),
                        rs.getString("site_name"),
                        rs.getInt("status_count"),
                        resolved.dbValue()));
    }

    /**
     * Backwards-compatible overload without archive-state parameter.
     */
    public List<SiteSoftwareSummary> getSoftwareSummary(String status) {
        return getSoftwareSummary(status, null);
    }

    // READ operations: list all sites or filter by project

    /**
     * Lists sites, optionally filtered by project.
     *
     * <p>Path: {@code GET /sites}</p>
     * <p>Optional {@code projectId} query parameter narrows the result to a project.</p>
     *
     * @param projectId optional project foreign key.
     * @return 200 OK with sites as JSON.
     */
    @GetMapping
    public List<Map<String, Object>> findByProject(@RequestParam(required = false) String projectId,
                                                   @RequestParam(required = false, name = "accountId") String accountId,
                                                   @RequestParam(required = false, name = "archiveState") String archiveStateRaw) {
        boolean filterByProject = projectId != null && !projectId.isBlank();
        boolean filterByAccount = accountId != null && !accountId.isBlank();
        ArchiveState archiveState = parseArchiveState(archiveStateRaw);

        if (filterByProject || filterByAccount) {
            StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT s.SiteID, s.SiteName, s.FireZone, s.TenantCount, s.RedundantServers, s.HighAvailability, s.AddressID, s.ProjectID
                FROM Site s
                JOIN ProjectSite ps ON ps.SiteID = s.SiteID
                """);

            MapSqlParameterSource params = new MapSqlParameterSource();
            if (filterByAccount) {
                sql.append(" JOIN Project p ON p.ProjectID = ps.ProjectID\n");
                params.addValue("accId", accountId);
            }

            List<String> where = new ArrayList<>();
            appendArchiveWhere(where, "s.IsArchived", archiveState);
            appendArchiveWhere(where, "ps.IsArchived", archiveState);
            if (filterByProject) {
                params.addValue("pid", projectId);
                where.add("ps.ProjectID = :pid");
            }
            if (filterByAccount) {
                where.add("p.AccountID = :accId");
                appendArchiveWhere(where, "p.IsArchived", archiveState);
            }
            if (!where.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", where)).append("\n");
            }
            sql.append(" ORDER BY s.SiteName NULLS LAST, s.SiteID");

            return jdbc.queryForList(sql.toString(), params);
        }

        return jdbc.queryForList("""
            SELECT SiteID, SiteName, FireZone, TenantCount, RedundantServers, HighAvailability, AddressID, ProjectID
            FROM Site
            WHERE (:archived = 'ALL'
                   OR (:archived = 'ACTIVE' AND IsArchived = FALSE)
                   OR (:archived = 'ARCHIVED' AND IsArchived = TRUE))
            ORDER BY SiteName NULLS LAST, SiteID
            """, new MapSqlParameterSource("archived", archiveState.name()));
    }

    /**
     * Backwards-compatible overload without archive-state parameter.
     */
    public List<Map<String, Object>> findByProject(String projectId, String accountId) {
        boolean filterByProject = projectId != null && !projectId.isBlank();
        boolean filterByAccount = accountId != null && !accountId.isBlank();

        if (filterByProject || filterByAccount) {
            StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT s.SiteID, s.SiteName, s.FireZone, s.TenantCount, s.RedundantServers, s.HighAvailability, s.AddressID, s.ProjectID
                FROM Site s
                JOIN ProjectSite ps ON ps.SiteID = s.SiteID
                """);

            MapSqlParameterSource params = new MapSqlParameterSource();
            if (filterByAccount) {
                sql.append(" JOIN Project p ON p.ProjectID = ps.ProjectID\n");
                params.addValue("accId", accountId);
            }
            List<String> where = new ArrayList<>();
            if (filterByProject) {
                where.add("ps.ProjectID = :pid");
                params.addValue("pid", projectId);
            }
            if (filterByAccount) {
                where.add("p.AccountID = :accId");
            }
            if (!where.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", where)).append('\n');
            }
            sql.append(" ORDER BY s.SiteName NULLS LAST, s.SiteID");
            return jdbc.queryForList(sql.toString(), params);
        }

        return jdbc.queryForList("""
            SELECT SiteID, SiteName, FireZone, TenantCount, RedundantServers, HighAvailability, AddressID, ProjectID
            FROM Site
            ORDER BY SiteName NULLS LAST, SiteID
            """, Map.of());
    }

    /**
     * Returns a site by ID.
     *
     * <p>Path: {@code GET /sites/{id}}</p>
     *
     * @param id site ID.
     * @return 200 OK with the field values or 404 if the ID is unknown.
     */
    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT SiteID, SiteName, FireZone, TenantCount, RedundantServers, HighAvailability, AddressID, ProjectID
            FROM Site
            WHERE SiteID = :id
            """, new MapSqlParameterSource("id", id));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found");
        }
        return rows.get(0);
    }

    /**
     * Returns a site with its linked software assignments.
     *
     * <p>Path: {@code GET /sites/{id}/detail}</p>
     *
     * @param id site identifier
     * @return site data and assignments
     */
    @GetMapping("/{id}/detail")
    public SiteDetailResponse findDetail(@PathVariable String id) {
        UUID siteId = parseUuid(id, "SiteID");
        Site site = siteService.getSiteById(siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found"));
        Boolean isArchived = jdbc.queryForObject("""
                SELECT IsArchived
                FROM Site
                WHERE SiteID = :id
                """, new MapSqlParameterSource("id", siteId), Boolean.class);
        List<UUID> projectIds = projectSites.getProjectsForSite(siteId);
        List<SiteSoftwareOverviewEntry> assignments = installedSoftwareService.getSiteSoftwareOverview(siteId);

        return new SiteDetailResponse(
                site.getSiteID(),
                site.getSiteName(),
                site.getProjectID(),
                projectIds,
                site.getAddressID(),
                site.getFireZone(),
                site.getTenantCount(),
                site.getRedundantServers(),
                site.getHighAvailability(),
                Boolean.TRUE.equals(isArchived),
                assignments
        );
    }

    /**
     * Returns installed software assignments for the requested site.
     *
     * @param id site identifier.
     * @return list of software overview entries for the site.
     */
    @GetMapping({"/{id}/software", "/{id}/software/overview"})
    public List<SiteSoftwareOverviewEntry> softwareOverview(@PathVariable String id) {
        UUID siteId = parseUuid(id, "SiteID");
        return installedSoftwareService.getSiteSoftwareOverview(siteId);
    }

    /**
     * Updates the install status for a site/software relationship.
     *
     * <p>Path: {@code PATCH /sites/{siteId}/software/{installationId}/status}</p>
     *
     * @param siteId          site identifier
     * @param installationId  installed software identifier
     * @param request         payload containing the new status
     */
    @PatchMapping("/{siteId}/software/{installationId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSoftwareStatus(@PathVariable String siteId,
                                     @PathVariable String installationId,
                                     @RequestBody InstalledSoftwareStatusUpdateRequest request) {
        try {
            if (request == null || request.normalizedStatus() == null || request.normalizedStatus().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
            }

            UUID siteUuid = parseUuid(siteId, "SiteID");
            UUID installationUuid = parseUuid(installationId, "InstalledSoftwareID");

            var installation = installedSoftwareService.getInstalledSoftwareById(installationUuid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Installed software not found"));

            if (installation.getSiteID() != null && !installation.getSiteID().equals(siteUuid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Installation does not belong to the requested site");
            }

            installedSoftwareService.updateStatus(installationUuid, request.normalizedStatus());
            audit.updated("InstalledSoftware",
                    Map.of("InstalledSoftwareID", installationUuid, "SiteID", siteUuid),
                    Map.of("status", request.normalizedStatus()));
        } catch (ResponseStatusException ex) {
            audit.failed("UPDATE", "InstalledSoftware",
                    Map.of("InstalledSoftwareID", installationId, "SiteID", siteId),
                    ex.getReason(), request);
            throw ex;
        } catch (IllegalArgumentException ex) {
            audit.failed("UPDATE", "InstalledSoftware",
                    Map.of("InstalledSoftwareID", installationId, "SiteID", siteId),
                    ex.getMessage(), request);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            audit.failed("UPDATE", "InstalledSoftware",
                    Map.of("InstalledSoftwareID", installationId, "SiteID", siteId),
                    ex.getMessage(), request);
            throw ex;
        }
    }

    // CREATE operations

    /**
     * Creates a site.
     *
     * <p>Path: {@code POST /sites}</p>
     * <p>Request body: JSON with fields such as {@code siteName} or {@code projectID}.</p>
     *
     * @param body input payload.
     * @throws ResponseStatusException 400 if the body is empty.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody SiteUpsertRequest request) {
        try {
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
            }
            request.validateForCreate();
            Site saved = siteService.createOrUpdateSite(request.toSite(), request.normalizedProjectIds());
            persistAssignments(saved.getSiteID(), request);
            audit.created(TABLE, Map.of("SiteID", saved.getSiteID()), request);
        } catch (ResponseStatusException ex) {
            audit.failed("CREATE", TABLE, Map.of(), ex.getReason(), request);
            throw ex;
        } catch (IllegalArgumentException ex) {
            audit.failed("CREATE", TABLE, Map.of(), ex.getMessage(), request);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            audit.failed("CREATE", TABLE, Map.of(), ex.getMessage(), request);
            throw ex;
        }
    }

    // UPDATE operations

    /**
     * Updates a site.
     *
     * <p>Path: {@code PUT /sites/{id}}</p>
     * <p>Request body: JSON object with the columns to update.</p>
     *
     * @param id   site ID.
     * @param body field values.
     * @throws ResponseStatusException 400 if the body is empty, 404 if nothing was updated.
     */
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody SiteUpsertRequest request) {
        try {
            UUID siteId = parseUuid(id, "SiteID");
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
            }
            request.validateForUpdate();
            Site patch = request.toSite();
            Optional<Site> updated = siteService.updateSite(siteId, patch, request.normalizedProjectIds());
            if (updated.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no site updated");
            }
            persistAssignments(siteId, request);
            audit.updated(TABLE, Map.of("SiteID", siteId), request);
        } catch (ResponseStatusException ex) {
            audit.failed("UPDATE", TABLE, Map.of("SiteID", id), ex.getReason(), request);
            throw ex;
        } catch (IllegalArgumentException ex) {
            audit.failed("UPDATE", TABLE, Map.of("SiteID", id), ex.getMessage(), request);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            audit.failed("UPDATE", TABLE, Map.of("SiteID", id), ex.getMessage(), request);
            throw ex;
        }
    }

    // DELETE operations

    /**
     * Deletes a site.
     *
     * <p>Path: {@code DELETE /sites/{id}}</p>
     *
     * @param id site ID.
     * @throws ResponseStatusException 404 if no row was deleted.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        try {
            UUID siteId = parseUuid(id, "SiteID");
            String actor = currentActor();
            boolean archived;
            if (archiveService != null) {
                archived = archiveService.archive("site", id, actor);
            } else {
                siteService.getSiteById(siteId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found"));
                siteService.deleteSite(siteId);
                archived = true;
            }
            if (!archived) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no site archived");
            }
            audit.archived(TABLE, Map.of("SiteID", id), Map.of("actor", actor));
            audit.deleted(TABLE, Map.of("SiteID", id));
        } catch (ResponseStatusException ex) {
            audit.failed("DELETE", TABLE, Map.of("SiteID", id), ex.getReason(), null);
            throw ex;
        } catch (RuntimeException ex) {
            audit.failed("DELETE", TABLE, Map.of("SiteID", id), ex.getMessage(), null);
            throw ex;
        }
    }

    /**
     * Persists installed software assignments for the site.
     *
     * @param siteId site identifier.
     * @param request request payload with assignments.
     */
    private void persistAssignments(UUID siteId, SiteUpsertRequest request) {
        try {
            installedSoftwareService.replaceAssignmentsForSite(siteId, request.toInstalledSoftware(siteId));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * Parses a UUID and converts parsing errors into a {@link ResponseStatusException}.
     *
     * @param raw raw string value.
     * @param fieldName field label used in the error message.
     * @return parsed UUID.
     */
    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " must be a valid UUID", ex);
        }
    }

    private ArchiveState parseArchiveState(String raw) {
        try {
            return ArchiveState.from(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private void appendArchiveWhere(List<String> where, String column, ArchiveState archiveState) {
        switch (archiveState) {
            case ACTIVE -> where.add(column + " = FALSE");
            case ARCHIVED -> where.add(column + " = TRUE");
            case ALL -> {
                // no-op
            }
        }
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }

}
