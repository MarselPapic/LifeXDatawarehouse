package at.htlle.freq.web.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SiteUpsertRequestTest {

    private SiteUpsertRequest validRequest() {
        UUID projectId = UUID.randomUUID();
        return new SiteUpsertRequest(
                "Site",
                projectId,
                List.of(projectId),
                UUID.randomUUID(),
                "Zone",
                1,
                0,
                true,
                List.of()
        );
    }

    @Test
    void validateForCreateAcceptsValidRequest() {
        assertDoesNotThrow(() -> validRequest().validateForCreate());
    }

    @Test
    void validateForCreateRejectsBlankName() {
        UUID projectId = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                " ",
                projectId,
                List.of(projectId),
                UUID.randomUUID(),
                "Zone",
                1,
                0,
                true,
                List.of()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::validateForCreate);
        assertEquals("SiteName is required", ex.getMessage());
    }

    @Test
    void validateForCreateRejectsMissingProjectIds() {
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Site",
                null,
                List.of(),
                UUID.randomUUID(),
                "Zone",
                1,
                0,
                true,
                List.of()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::validateForCreate);
        assertEquals("At least one ProjectID is required", ex.getMessage());
    }

    @Test
    void validateForCreateRejectsInvalidAssignmentStatus() {
        UUID projectId = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Site",
                projectId,
                List.of(projectId),
                UUID.randomUUID(),
                "Zone",
                1,
                0,
                true,
                List.of(new SiteSoftwareAssignmentDto(null, UUID.randomUUID(), "Nope", null, null, null, null))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::validateForCreate);
        assertTrue(ex.getMessage().contains("Unsupported installed software status"));
    }

    @Test
    void validateForUpdateRejectsBlankName() {
        SiteUpsertRequest request = new SiteUpsertRequest(
                " ",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::validateForUpdate);
        assertEquals("SiteName must not be blank", ex.getMessage());
    }

    @Test
    void normalizedProjectIdsDistinct() {
        UUID projectId = UUID.randomUUID();
        UUID extra = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Site",
                projectId,
                java.util.Arrays.asList(projectId, extra, null),
                UUID.randomUUID(),
                null,
                null,
                0,
                true,
                List.of()
        );

        assertEquals(List.of(projectId, extra), request.normalizedProjectIds());
        assertEquals(projectId, request.primaryProjectId());
    }

    @Test
    void toInstalledSoftwareRequiresSiteId() {
        SiteUpsertRequest request = validRequest();
        NullPointerException ex = assertThrows(NullPointerException.class, () -> request.toInstalledSoftware(null));
        assertEquals("siteId must not be null", ex.getMessage());
    }

    @Test
    void toInstalledSoftwareMapsAssignments() {
        UUID projectId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID softwareId = UUID.randomUUID();
        SiteUpsertRequest request = new SiteUpsertRequest(
                "Site",
                projectId,
                List.of(projectId),
                UUID.randomUUID(),
                null,
                null,
                0,
                true,
                List.of(new SiteSoftwareAssignmentDto(null, softwareId, "Installed", "2024-01-01", null, null, null))
        );

        var installed = request.toInstalledSoftware(siteId);
        assertEquals(1, installed.size());
        assertEquals(siteId, installed.get(0).getSiteID());
        assertEquals(softwareId, installed.get(0).getSoftwareID());
        assertEquals("Installed", installed.get(0).getStatus());
        assertEquals("2024-01-01", installed.get(0).getOfferedDate());
    }
}
