package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class UpgradePlanFactory {
    /**
     * Creates an {@link UpgradePlan} describing an upgrade window for a site and
     * software combination. Identifiers are generated when persisting.
     *
     * @param siteID target {@link Site} identifier
     * @param softwareID {@link Software} to deploy
     * @param windowStart planned maintenance window start
     * @param windowEnd planned maintenance window end
     * @param status workflow status of the plan
     * @param createdBy user who authored the plan
     * @param createdAt timestamp string of creation
     * @return transient upgrade plan entity
     */
    public UpgradePlan create(UUID siteID, UUID softwareID, String windowStart, String windowEnd,
                              String status, String createdBy, String createdAt) {
        return new UpgradePlan(null, siteID, softwareID, windowStart, windowEnd, status, createdAt, createdBy);
    }
}
