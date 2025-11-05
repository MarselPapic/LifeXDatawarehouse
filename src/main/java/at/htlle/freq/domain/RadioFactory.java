package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class RadioFactory {
    /**
     * Creates a {@link Radio} that is deployed at a site and optionally bound to
     * a client. The generated entity expects persistence to supply its
     * identifier.
     *
     * @param siteID hosting site identifier
     * @param assignedClientID optional {@link Clients} assignment
     * @param brand radio manufacturer name
     * @param serialNr device serial number
     * @param mode analog or digital classification
     * @param digitalStandard optional digital standard descriptor
     * @return transient radio entity
     */
    public Radio create(UUID siteID, UUID assignedClientID, String brand, String serialNr,
                        String mode, String digitalStandard) {
        return new Radio(null, siteID, assignedClientID, brand, serialNr, mode, digitalStandard);
    }
}
