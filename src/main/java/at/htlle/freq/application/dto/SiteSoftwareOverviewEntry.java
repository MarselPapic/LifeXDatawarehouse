package at.htlle.freq.application.dto;

import java.util.UUID;

/**
 * DTO representing an installed software assignment enriched with software metadata.
 */
public record SiteSoftwareOverviewEntry(
        UUID installedSoftwareId,
        UUID siteId,
        String siteName,
        UUID softwareId,
        String softwareName,
        String release,
        String revision,
        String status,
        String statusLabel,
        String offeredAt,
        String installedAt,
        String rejectedAt
) {
}
