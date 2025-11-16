package at.htlle.freq.web.dto;

import at.htlle.freq.application.dto.SiteSoftwareOverviewEntry;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Combined DTO for site details including software assignments.
 */
public record SiteDetailResponse(
        @JsonProperty("SiteID") UUID siteId,
        @JsonProperty("SiteName") String siteName,
        @JsonProperty("ProjectID") UUID projectId,
        @JsonProperty("AddressID") UUID addressId,
        @JsonProperty("FireZone") String fireZone,
        @JsonProperty("TenantCount") Integer tenantCount,
        List<SiteSoftwareOverviewEntry> softwareAssignments
) { }
