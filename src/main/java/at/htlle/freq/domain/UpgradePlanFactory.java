package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class UpgradePlanFactory {
    public UpgradePlan create(UUID siteID, UUID softwareID, String windowStart, String windowEnd,
                              String status, String createdBy, String createdAt) {
        return new UpgradePlan(null, siteID, softwareID, windowStart, windowEnd, status, createdAt, createdBy);
    }
}
