package at.htlle.freq.web;

import at.htlle.freq.application.InstalledSoftwareService;
import at.htlle.freq.application.SiteService;
import at.htlle.freq.application.dto.SiteSoftwareOverviewEntry;
import at.htlle.freq.domain.InstalledSoftwareStatus;
import at.htlle.freq.domain.Site;
import at.htlle.freq.web.dto.InstalledSoftwareStatusUpdateRequest;
import at.htlle.freq.web.dto.SiteDetailResponse;
import at.htlle.freq.web.dto.SiteSoftwareSummary;
import at.htlle.freq.web.dto.SiteUpsertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private static final Logger log = LoggerFactory.getLogger(SiteController.class);
    private static final String TABLE = "Site";

    public SiteController(NamedParameterJdbcTemplate jdbc, SiteService siteService,
                          InstalledSoftwareService installedSoftwareService) {
        this.jdbc = jdbc;
        this.siteService = siteService;
        this.installedSoftwareService = installedSoftwareService;
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
            @RequestParam(name = "status", required = false) String status) {
        InstalledSoftwareStatus resolved;
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
            GROUP BY s.SiteID, s.SiteName
            ORDER BY s.SiteName NULLS LAST, s.SiteID
            """;

        return jdbc.query(sql, new MapSqlParameterSource("status", resolved.dbValue()),
                (rs, rowNum) -> new SiteSoftwareSummary(
                        rs.getObject("site_id", UUID.class),
                        rs.getString("site_name"),
                        rs.getInt("status_count"),
                        resolved.dbValue()));
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
    public List<Map<String, Object>> findByProject(@RequestParam(required = false) String projectId) {
        if (projectId != null) {
            return jdbc.queryForList("""
                SELECT SiteID, SiteName, FireZone, TenantCount, AddressID, ProjectID
                FROM Site
                WHERE ProjectID = :pid
                """, new MapSqlParameterSource("pid", projectId));
        }

        return jdbc.queryForList("""
            SELECT SiteID, SiteName, FireZone, TenantCount, AddressID, ProjectID
            FROM Site
            """, new HashMap<>());
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
            SELECT SiteID, SiteName, FireZone, TenantCount, AddressID, ProjectID
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
        List<SiteSoftwareOverviewEntry> assignments = installedSoftwareService.getSiteSoftwareOverview(siteId);

        return new SiteDetailResponse(
                site.getSiteID(),
                site.getSiteName(),
                site.getProjectID(),
                site.getAddressID(),
                site.getFireZone(),
                site.getTenantCount(),
                assignments
        );
    }

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

        try {
            installedSoftwareService.updateStatus(installationUuid, request.normalizedStatus());
            log.info("[{}] status updated: installation={} site={} status={}", TABLE,
                    installationUuid, siteUuid, request.normalizedStatus());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
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
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        try {
            request.validateForCreate();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        Site saved;
        try {
            saved = siteService.createOrUpdateSite(request.toSite());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        persistAssignments(saved.getSiteID(), request);
        log.info("[{}] create succeeded: id={} assignments={}", TABLE, saved.getSiteID(),
                request.normalizedAssignments().size());
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
        UUID siteId = parseUuid(id, "SiteID");
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");
        }

        try {
            request.validateForUpdate();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        Site patch = request.toSite();
        Optional<Site> updated;
        try {
            updated = siteService.updateSite(siteId, patch);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        if (updated.isEmpty()) {
            log.warn("[{}] update failed: identifiers={} ", TABLE, Map.of("SiteID", siteId));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no site updated");
        }

        persistAssignments(siteId, request);
        log.info("[{}] update succeeded: id={} assignments={} keys={}", TABLE, siteId,
                request.normalizedAssignments().size(), summarizeUpdatedFields(patch));
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
        int count = jdbc.update("DELETE FROM Site WHERE SiteID = :id",
                new MapSqlParameterSource("id", id));

        if (count == 0) {
            log.warn("[{}] delete failed: identifiers={}", TABLE, Map.of("SiteID", id));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no site deleted");
        }
        log.info("[{}] delete succeeded: identifiers={}", TABLE, Map.of("SiteID", id));
    }

    private void persistAssignments(UUID siteId, SiteUpsertRequest request) {
        try {
            installedSoftwareService.replaceAssignmentsForSite(siteId, request.toInstalledSoftware(siteId));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " must be a valid UUID", ex);
        }
    }

    private Set<String> summarizeUpdatedFields(Site patch) {
        Set<String> fields = new LinkedHashSet<>();
        if (patch.getSiteName() != null) fields.add("SiteName");
        if (patch.getProjectID() != null) fields.add("ProjectID");
        if (patch.getAddressID() != null) fields.add("AddressID");
        if (patch.getFireZone() != null) fields.add("FireZone");
        if (patch.getTenantCount() != null) fields.add("TenantCount");
        return fields;
    }
}
