package at.htlle.freq.domain;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Factory responsible for creating Upgrade Plan instances.
 */
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
     * @param createdAt creation date
     * @return transient upgrade plan entity
     */
    public UpgradePlan create(UUID siteID, UUID softwareID, LocalDate windowStart, LocalDate windowEnd,
                              String status, String createdBy, LocalDate createdAt) {
        return new UpgradePlan(null, siteID, softwareID, windowStart, windowEnd, status, createdAt, createdBy);
    }
}
