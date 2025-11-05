package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class InstalledSoftwareFactory {
    public InstalledSoftware create(UUID siteID, UUID softwareID) {
        return new InstalledSoftware(null, siteID, softwareID, InstalledSoftwareStatus.ACTIVE.dbValue());
    }
}
