package at.htlle.freq.web;

import at.htlle.freq.application.InstalledSoftwareService;
import at.htlle.freq.application.ProjectSiteAssignmentService;
import at.htlle.freq.application.SiteService;
import at.htlle.freq.application.dto.SiteSoftwareOverviewEntry;
import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.Site;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import at.htlle.freq.web.dto.InstalledSoftwareStatusUpdateRequest;
import at.htlle.freq.web.dto.SiteDetailResponse;
import at.htlle.freq.web.dto.SiteSoftwareAssignmentDto;
import at.htlle.freq.web.dto.SiteSoftwareSummary;
import at.htlle.freq.web.dto.SiteUpsertRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SiteControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private SiteService siteService;
    private InstalledSoftwareService installedSoftwareService;
    private ProjectSiteAssignmentService projectSites;
    private AuditLogger audit;
    private SiteController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        siteService = mock(SiteService.class);
        installedSoftwareService = mock(InstalledSoftwareService.class);
        projectSites = mock(ProjectSiteAssignmentService.class);
        audit = mock(AuditLogger.class);
        controller = new SiteController(jdbc, siteService, installedSoftwareService, projectSites, audit);
    }

    private SiteUpsertRequest emptyRequest() {
        return new SiteUpsertRequest(null, null, null, null, null, null, null, null, List.of());
    }

    @Test
    void createDelegatesToServices() {
        UUID project = UUID.randomUUID();
        UUID address = UUID.randomUUID();
        UUID software = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Test Site",
                project,
                List.of(project),
                address,
                "Zone",
                5,
                1,
                true,
                List.of(new SiteSoftwareAssignmentDto(null, software, "Offered", "2024-01-01", null, null, null))
        );

        Site saved = new Site();
        UUID siteId = UUID.randomUUID();
        saved.setSiteID(siteId);
        when(siteService.createOrUpdateSite(any(Site.class), anyList())).thenReturn(saved);

        controller.create(request);

        verify(siteService).createOrUpdateSite(argThat(site ->
                "Test Site".equals(site.getSiteName()) &&
                        project.equals(site.getProjectID()) &&
                        address.equals(site.getAddressID())), eq(List.of(project)));
        verify(installedSoftwareService).replaceAssignmentsForSite(eq(siteId),
                argThat(list -> list.size() == 1 && software.equals(list.get(0).getSoftwareID())));
    }

    @Test
    void createRejectsNullBody() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(siteService, installedSoftwareService);
    }

    @Test
    void createRejectsInvalidRequest() {
        SiteUpsertRequest request = new SiteUpsertRequest(
                "   ",
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(siteService, installedSoftwareService);
    }

    @Test
    void createWrapsDomainErrors() {
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Test Site",
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                UUID.randomUUID(),
                null,
                null,
                1,
                true,
                List.of()
        );
        when(siteService.createOrUpdateSite(any(Site.class), anyList()))
                .thenThrow(new IllegalArgumentException("invalid"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(installedSoftwareService);
    }

    @Test
    void createWrapsAssignmentErrors() {
        UUID siteId = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Test Site",
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                UUID.randomUUID(),
                "Zone",
                1,
                1,
                true,
                List.of(new SiteSoftwareAssignmentDto(null, UUID.randomUUID(), "Installed", null, null, null, null))
        );
        Site saved = new Site();
        saved.setSiteID(siteId);
        when(siteService.createOrUpdateSite(any(Site.class), anyList())).thenReturn(saved);
        doThrow(new IllegalArgumentException("bad assignment"))
                .when(installedSoftwareService)
                .replaceAssignmentsForSite(eq(siteId), anyList());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateReturnsNotFoundWhenSiteMissing() {
        UUID siteId = UUID.randomUUID();
        when(siteService.updateSite(eq(siteId), any(Site.class), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update(siteId.toString(), emptyRequest()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(installedSoftwareService, never()).replaceAssignmentsForSite(any(), any());
    }

    @Test
    void updateRejectsInvalidUuid() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update("not-a-uuid", emptyRequest()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateRejectsNullBody() {
        UUID siteId = UUID.randomUUID();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update(siteId.toString(), null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(siteService, installedSoftwareService);
    }

    @Test
    void updateRejectsInvalidRequest() {
        UUID siteId = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                " ",
                null,
                null,
                null,
                null,
                null,
                -1,
                null,
                List.of()
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update(siteId.toString(), request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(siteService, installedSoftwareService);
    }

    @Test
    void updateDelegatesToInstalledSoftwareService() {
        UUID siteId = UUID.randomUUID();
        UUID software = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Updated Site",
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                UUID.randomUUID(),
                "Zone",
                20,
                2,
                true,
                List.of(new SiteSoftwareAssignmentDto(UUID.randomUUID(), software, "Installed", null, "2024-02-02", null, null))
        );
        when(siteService.updateSite(eq(siteId), any(Site.class), any())).thenReturn(Optional.of(new Site()));

        controller.update(siteId.toString(), request);

        verify(installedSoftwareService).replaceAssignmentsForSite(eq(siteId),
                argThat(list -> list.size() == 1 && software.equals(list.get(0).getSoftwareID())));
    }

    @Test
    void updateWrapsDomainErrors() {
        UUID siteId = UUID.randomUUID();
        SiteUpsertRequest request = emptyRequest();
        when(siteService.updateSite(eq(siteId), any(Site.class), any()))
                .thenThrow(new IllegalArgumentException("bad update"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update(siteId.toString(), request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(installedSoftwareService);
    }

    @Test
    void updateWrapsAssignmentErrors() {
        UUID siteId = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Updated Site",
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                UUID.randomUUID(),
                "Zone",
                1,
                1,
                true,
                List.of(new SiteSoftwareAssignmentDto(null, UUID.randomUUID(), "Installed", null, null, null, null))
        );
        when(siteService.updateSite(eq(siteId), any(Site.class), any()))
                .thenReturn(Optional.of(new Site()));
        doThrow(new IllegalArgumentException("bad assignment"))
                .when(installedSoftwareService)
                .replaceAssignmentsForSite(eq(siteId), anyList());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.update(siteId.toString(), request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void detailEndpointReturnsSiteDataAndAssignments() {
        UUID siteId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Site site = new Site();
        site.setSiteID(siteId);
        site.setSiteName("Detail Site");
        site.setProjectID(projectId);
        site.setAddressID(addressId);
        site.setFireZone("Blue");
        site.setTenantCount(3);
        site.setRedundantServers(2);
        site.setHighAvailability(true);
        when(projectSites.getProjectsForSite(siteId)).thenReturn(List.of(projectId));

        List<SiteSoftwareOverviewEntry> assignments = List.of(new SiteSoftwareOverviewEntry(
                UUID.randomUUID(),
                siteId,
                "Site",
                UUID.randomUUID(),
                "Core",
                "1",
                "0",
                "Installed",
                "Installed",
                "2024-01-01",
                "2024-02-02",
                null,
                null
        ));

        when(siteService.getSiteById(siteId)).thenReturn(Optional.of(site));
        when(installedSoftwareService.getSiteSoftwareOverview(siteId)).thenReturn(assignments);

        SiteDetailResponse response = controller.findDetail(siteId.toString());

        assertEquals(siteId, response.siteId());
        assertEquals("Detail Site", response.siteName());
        assertEquals(projectId, response.projectId());
        assertEquals(List.of(projectId), response.projectIds());
        assertEquals(addressId, response.addressId());
        assertEquals(2, response.redundantServers());
        assertEquals(1, response.softwareAssignments().size());
        assertEquals(assignments, response.softwareAssignments());
    }

    @Test
    void softwareOverviewDelegatesToService() {
        UUID siteId = UUID.randomUUID();
        List<SiteSoftwareOverviewEntry> entries = List.of(new SiteSoftwareOverviewEntry(
                UUID.randomUUID(),
                siteId,
                "Site",
                UUID.randomUUID(),
                "CRM",
                "1.0",
                "rev1",
                "Installed",
                "Installed",
                "2024-01-01",
                "2024-02-02",
                null,
                null
        ));
        when(installedSoftwareService.getSiteSoftwareOverview(siteId)).thenReturn(entries);

        List<SiteSoftwareOverviewEntry> result = controller.softwareOverview(siteId.toString());
        assertSame(entries, result);
        verify(installedSoftwareService).getSiteSoftwareOverview(siteId);
    }

    @Test
    void softwareOverviewRejectsInvalidId() {
        assertThrows(ResponseStatusException.class, () -> controller.softwareOverview("invalid"));
        verifyNoInteractions(installedSoftwareService);
    }

    @Test
    void findDetailRejectsInvalidIdWithMessage() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.findDetail("not-a-uuid"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("SiteID must be a valid UUID", ex.getReason());
    }

    @Test
    void getSoftwareSummaryDefaultsToInstalled() {
        List<SiteSoftwareSummary> summaries = List.of(
                new SiteSoftwareSummary(UUID.randomUUID(), "Site A", 2, "Installed")
        );
        doReturn(summaries)
                .when(jdbc)
                .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

        List<SiteSoftwareSummary> result = controller.getSoftwareSummary(null);

        assertSame(summaries, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("InstalledSoftware"));
        assertEquals("Installed", paramsCaptor.getValue().getValue("status"));
    }

    @Test
    void getSoftwareSummaryDefaultsToInstalledWhenBlank() {
        List<SiteSoftwareSummary> summaries = List.of(
                new SiteSoftwareSummary(UUID.randomUUID(), "Site A", 2, "Installed")
        );
        doReturn(summaries)
                .when(jdbc)
                .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

        List<SiteSoftwareSummary> result = controller.getSoftwareSummary("   ");

        assertSame(summaries, result);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));
        assertEquals("Installed", paramsCaptor.getValue().getValue("status"));
    }

    @Test
    void getSoftwareSummaryRejectsUnknownStatus() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getSoftwareSummary("NotAStatus"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void findByProjectWithoutFiltersRunsDefaultQuery() {
        List<Map<String, Object>> rows = List.of(Map.of("SiteID", UUID.randomUUID()));
        doReturn(rows).when(jdbc).queryForList(anyString(), anyMap());

        List<Map<String, Object>> result = controller.findByProject(null, null);

        assertSame(rows, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("FROM Site"));
        assertFalse(sqlCaptor.getValue().contains("ProjectSite"));
        assertTrue(paramsCaptor.getValue().isEmpty());
    }

    @Test
    void findByProjectFiltersByProject() {
        List<Map<String, Object>> rows = List.of(Map.of("SiteID", UUID.randomUUID()));
        doReturn(rows).when(jdbc).queryForList(anyString(), any(MapSqlParameterSource.class));
        String projectId = UUID.randomUUID().toString();

        List<Map<String, Object>> result = controller.findByProject(projectId, null);

        assertSame(rows, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("ProjectSite"));
        assertTrue(sqlCaptor.getValue().contains("ps.ProjectID = :pid"));
        assertEquals(projectId, paramsCaptor.getValue().getValue("pid"));
    }

    @Test
    void findByProjectFiltersByAccount() {
        List<Map<String, Object>> rows = List.of(Map.of("SiteID", UUID.randomUUID()));
        doReturn(rows).when(jdbc).queryForList(anyString(), any(MapSqlParameterSource.class));
        String accountId = UUID.randomUUID().toString();

        List<Map<String, Object>> result = controller.findByProject(null, accountId);

        assertSame(rows, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("JOIN Project p"));
        assertTrue(sqlCaptor.getValue().contains("p.AccountID = :accId"));
        assertEquals(accountId, paramsCaptor.getValue().getValue("accId"));
    }

    @Test
    void findByProjectFiltersByProjectAndAccount() {
        List<Map<String, Object>> rows = List.of(Map.of("SiteID", UUID.randomUUID()));
        doReturn(rows).when(jdbc).queryForList(anyString(), any(MapSqlParameterSource.class));
        String projectId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();

        List<Map<String, Object>> result = controller.findByProject(projectId, accountId);

        assertSame(rows, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("ProjectSite"));
        assertTrue(sqlCaptor.getValue().contains("JOIN Project p"));
        assertTrue(sqlCaptor.getValue().contains("ps.ProjectID = :pid"));
        assertTrue(sqlCaptor.getValue().contains("p.AccountID = :accId"));
        assertEquals(projectId, paramsCaptor.getValue().getValue("pid"));
        assertEquals(accountId, paramsCaptor.getValue().getValue("accId"));
    }

    @Test
    void findByIdReturnsRow() {
        UUID siteId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("SiteID", siteId);
        doReturn(List.of(row)).when(jdbc).queryForList(anyString(), any(MapSqlParameterSource.class));

        Map<String, Object> result = controller.findById(siteId.toString());

        assertSame(row, result);
    }

    @Test
    void findByIdReturnsNotFound() {
        doReturn(List.of()).when(jdbc).queryForList(anyString(), any(MapSqlParameterSource.class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.findById(UUID.randomUUID().toString()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateSoftwareStatusRejectsMissingStatus() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.updateSoftwareStatus(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("status is required", ex.getReason());
    }

    @Test
    void updateSoftwareStatusRejectsInvalidSiteId() {
        InstalledSoftwareStatusUpdateRequest request = new InstalledSoftwareStatusUpdateRequest("Installed");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.updateSoftwareStatus("bad-id", UUID.randomUUID().toString(), request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateSoftwareStatusRejectsInvalidInstallationId() {
        InstalledSoftwareStatusUpdateRequest request = new InstalledSoftwareStatusUpdateRequest("Installed");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.updateSoftwareStatus(UUID.randomUUID().toString(), "bad-id", request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateSoftwareStatusReturnsNotFoundWhenMissingInstallation() {
        UUID siteId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        InstalledSoftwareStatusUpdateRequest request = new InstalledSoftwareStatusUpdateRequest("Installed");
        when(installedSoftwareService.getInstalledSoftwareById(installationId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.updateSoftwareStatus(siteId.toString(), installationId.toString(), request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateSoftwareStatusRejectsMismatchedSite() {
        UUID siteId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        InstalledSoftware installation = new InstalledSoftware();
        installation.setSiteID(UUID.randomUUID());
        when(installedSoftwareService.getInstalledSoftwareById(installationId)).thenReturn(Optional.of(installation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.updateSoftwareStatus(siteId.toString(), installationId.toString(),
                        new InstalledSoftwareStatusUpdateRequest("Installed")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateSoftwareStatusWrapsDomainErrors() {
        UUID siteId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        InstalledSoftware installation = new InstalledSoftware();
        installation.setSiteID(siteId);
        when(installedSoftwareService.getInstalledSoftwareById(installationId)).thenReturn(Optional.of(installation));
        doThrow(new IllegalArgumentException("bad"))
                .when(installedSoftwareService)
                .updateStatus(installationId, "Installed");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.updateSoftwareStatus(siteId.toString(), installationId.toString(),
                        new InstalledSoftwareStatusUpdateRequest("Installed")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateSoftwareStatusSucceeds() {
        UUID siteId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        InstalledSoftware installation = new InstalledSoftware();
        installation.setSiteID(siteId);
        when(installedSoftwareService.getInstalledSoftwareById(installationId)).thenReturn(Optional.of(installation));

        controller.updateSoftwareStatus(siteId.toString(), installationId.toString(),
                new InstalledSoftwareStatusUpdateRequest("Installed"));

        verify(installedSoftwareService).updateStatus(installationId, "Installed");
    }

    @Test
    void deleteRejectsMissingSite() {
        UUID siteId = UUID.randomUUID();
        when(siteService.getSiteById(siteId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.delete(siteId.toString()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(siteService, never()).deleteSite(any());
    }

    @Test
    void deleteRejectsInvalidIdWithMessage() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.delete("bad-id"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("SiteID must be a valid UUID", ex.getReason());
        verifyNoInteractions(siteService);
    }

    @Test
    void deleteDelegatesToService() {
        UUID siteId = UUID.randomUUID();
        when(siteService.getSiteById(siteId)).thenReturn(Optional.of(new Site()));

        controller.delete(siteId.toString());

        verify(siteService).deleteSite(siteId);
    }
}
