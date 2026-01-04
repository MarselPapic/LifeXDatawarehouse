package at.htlle.freq.web.dto;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.InstalledSoftwareStatus;
import at.htlle.freq.domain.Site;

import java.util.ArrayList;
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
        List<UUID> projectIds,
        UUID addressID,
        String fireZone,
        Integer tenantCount,
        Integer redundantServers,
        Boolean highAvailability,
        List<SiteSoftwareAssignmentDto> softwareAssignments
) {
    /**
     * Validates required fields for a create request.
     */
    public void validateForCreate() {
        if (isBlank(siteName)) {
            throw new IllegalArgumentException("SiteName is required");
        }
        if (normalizedProjectIds().isEmpty()) {
            throw new IllegalArgumentException("At least one ProjectID is required");
        }
        if (addressID == null) {
            throw new IllegalArgumentException("AddressID is required");
        }
        if (redundantServers == null) {
            throw new IllegalArgumentException("RedundantServers is required");
        }
        if (redundantServers < 0) {
            throw new IllegalArgumentException("RedundantServers must not be negative");
        }
        if (tenantCount != null && tenantCount < 0) {
            throw new IllegalArgumentException("TenantCount must not be negative");
        }
        if (highAvailability == null) {
            throw new IllegalArgumentException("HighAvailability is required");
        }
        validateAssignments();
    }

    /**
     * Validates fields for an update request.
     */
    public void validateForUpdate() {
        if (siteName != null && siteName.trim().isEmpty()) {
            throw new IllegalArgumentException("SiteName must not be blank");
        }
        if (redundantServers != null && redundantServers < 0) {
            throw new IllegalArgumentException("RedundantServers must not be negative");
        }
        if (tenantCount != null && tenantCount < 0) {
            throw new IllegalArgumentException("TenantCount must not be negative");
        }
        validateAssignments();
    }

    /**
     * Validates installed software assignment entries.
     */
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

    /**
     * Maps this request to a {@link Site} instance.
     *
     * @return site entity containing core fields.
     */
    public Site toSite() {
        Site site = new Site();
        site.setSiteName(siteName);
        site.setProjectID(primaryProjectId());
        site.setAddressID(addressID);
        site.setFireZone(fireZone);
        site.setTenantCount(tenantCount);
        site.setRedundantServers(redundantServers);
        site.setHighAvailability(highAvailability);
        return site;
    }

    /**
     * Returns a non-null list of software assignments.
     *
     * @return list of assignment DTOs.
     */
    public List<SiteSoftwareAssignmentDto> normalizedAssignments() {
        return softwareAssignments == null ? List.of() : softwareAssignments;
    }

    /**
     * Normalizes project identifiers into a unique list.
     *
     * @return distinct project IDs in request order.
     */
    public List<UUID> normalizedProjectIds() {
        List<UUID> ids = new ArrayList<>();
        if (projectID != null) ids.add(projectID);
        if (projectIds != null) ids.addAll(projectIds);
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Returns the first project identifier, when available.
     *
     * @return primary project ID or null.
     */
    public UUID primaryProjectId() {
        return normalizedProjectIds().stream().findFirst().orElse(null);
    }

    /**
     * Converts assignment DTOs into {@link InstalledSoftware} entities for a site.
     *
     * @param siteId site identifier.
     * @return list of installed software entities.
     */
    public List<InstalledSoftware> toInstalledSoftware(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return normalizedAssignments().stream()
                .map(dto -> dto.toDomain(siteId))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether a string is null or blank.
     *
     * @param value input string.
     * @return true when the string is null, empty, or whitespace.
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
