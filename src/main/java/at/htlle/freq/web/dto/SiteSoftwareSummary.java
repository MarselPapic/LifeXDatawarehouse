package at.htlle.freq.web.dto;

import java.util.UUID;

/**
 * Aggregated view of installed software assignments per site for a given status.
 */
public record SiteSoftwareSummary(UUID siteId, String siteName, int count, String status) {
}
