package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Factory responsible for creating Site instances.
 */
@Component
public class SiteFactory {
    /**
     * Creates a {@link Site} bound to a project and address. The site identifier
     * remains {@code null} until persisted.
     *
     * @param siteName descriptive name of the location
     * @param projectID parent {@link Project} identifier
     * @param addressID reference to the {@link Address}
     * @param fireZone fire zone classification
     * @param tenantCount number of tenants residing at the site
     * @param redundantServers count of redundant servers installed on-site
     * @param highAvailability whether the site operates in an HA configuration
     * @return transient site entity
     */
    public Site create(String siteName, UUID projectID, UUID addressID, String fireZone, Integer tenantCount,
                       Integer redundantServers, boolean highAvailability) {
        return new Site(null, siteName, projectID, addressID, fireZone, tenantCount, redundantServers, highAvailability);
    }
}
