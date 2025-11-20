package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Projection aggregating an installed software record with its related software metadata.
 */
public record SiteSoftwareOverview(
        UUID installedSoftwareId,
        UUID siteId,
        String siteName,
        UUID softwareId,
        String softwareName,
        String release,
        String revision,
        String status,
        String offeredDate,
        String installedDate,
        String rejectedDate,
        String outdatedDate
) {
}
