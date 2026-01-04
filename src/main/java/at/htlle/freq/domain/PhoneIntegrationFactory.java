package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Factory responsible for creating Phone Integration instances.
 */
@Component
public class PhoneIntegrationFactory {
    /**
     * Constructs a {@link PhoneIntegration} tied to a {@link Site} location.
     * The returned instance awaits persistence to obtain its id.
     *
     * @param siteID identifier of the host site
     * @param phoneType designation of supported call scenarios
     * @param brand hardware brand
     * @param interfaceName telephony interface identifier
     * @param capacity number of supported lines
     * @param firmware firmware revision
     * @return transient phone integration entity
     */
    public PhoneIntegration create(UUID siteID, String phoneType, String brand, String interfaceName, Integer capacity, String firmware) {
        return new PhoneIntegration(null, siteID, phoneType, brand, interfaceName, capacity, firmware);
    }
}
