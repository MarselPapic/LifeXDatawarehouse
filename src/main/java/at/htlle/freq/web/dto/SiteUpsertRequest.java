package at.htlle.freq.web.dto;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.InstalledSoftwareStatus;
import at.htlle.freq.domain.Site;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Request payload for creating or updating {@link at.htlle.freq.domain.Site} entities.
 */
public record SiteUpsertRequest(
        String siteName,
        UUID projectID,
        UUID addressID,
        String fireZone,
        Integer tenantCount,
        List<SiteSoftwareAssignmentDto> softwareAssignments
) {
    public void validateForCreate() {
        if (isBlank(siteName)) {
            throw new IllegalArgumentException("SiteName is required");
        }
        if (projectID == null) {
            throw new IllegalArgumentException("ProjectID is required");
        }
        if (addressID == null) {
            throw new IllegalArgumentException("AddressID is required");
        }
        validateAssignments();
    }

    public void validateForUpdate() {
        if (siteName != null && siteName.trim().isEmpty()) {
            throw new IllegalArgumentException("SiteName must not be blank");
        }
        validateAssignments();
    }

    private void validateAssignments() {
        for (SiteSoftwareAssignmentDto assignment : normalizedAssignments()) {
            if (assignment.softwareId() == null) {
                throw new IllegalArgumentException("SoftwareID is required for installed software assignments");
            }
            if (assignment.status() != null) {
                InstalledSoftwareStatus.from(assignment.status());
            }
        }
    }

    public Site toSite() {
        Site site = new Site();
        site.setSiteName(siteName);
        site.setProjectID(projectID);
        site.setAddressID(addressID);
        site.setFireZone(fireZone);
        site.setTenantCount(tenantCount);
        return site;
    }

    public List<SiteSoftwareAssignmentDto> normalizedAssignments() {
        return softwareAssignments == null ? List.of() : softwareAssignments;
    }

    public List<InstalledSoftware> toInstalledSoftware(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return normalizedAssignments().stream()
                .map(dto -> dto.toDomain(siteId))
                .collect(Collectors.toList());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
