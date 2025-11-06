package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class PhoneIntegrationFactory {
    /**
     * Constructs a {@link PhoneIntegration} tied to a {@link Clients}
     * client workstation. The returned instance awaits persistence to obtain
     * its id.
     *
     * @param clientID identifier of the host client workstation
     * @param phoneType designation of supported call scenarios
     * @param brand hardware brand
     * @param serialNr device serial number
     * @param firmware firmware revision
     * @return transient phone integration entity
     */
    public PhoneIntegration create(UUID clientID, String phoneType, String brand, String serialNr, String firmware) {
        return new PhoneIntegration(null, clientID, phoneType, brand, serialNr, firmware);
    }
}
