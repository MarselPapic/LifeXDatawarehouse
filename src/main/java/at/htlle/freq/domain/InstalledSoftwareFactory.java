package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class InstalledSoftwareFactory {
    /**
     * Links a {@link Software} package to a {@link Site} with an initial
     * {@link InstalledSoftwareStatus#OFFERED} state. The identifier stays unset
     * for database generation.
     *
     * @param siteID site that hosts the installation
     * @param softwareID software package being installed
     * @return transient installed software relationship
     */
    public InstalledSoftware create(UUID siteID, UUID softwareID) {
        return new InstalledSoftware(null, siteID, softwareID, InstalledSoftwareStatus.OFFERED.dbValue(), null, null, null);
    }
}
