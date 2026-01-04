package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Factory responsible for creating Clients instances.
 */
@Component
public class ClientsFactory {
    /**
     * Constructs a {@link Clients} client workstation attached to the supplied
     * site. The factory sets the identifier to {@code null} so the persistence
     * tier can generate it.
     *
     * @param siteID hosting site identifier
     * @param clientName descriptive hostname
     * @param clientBrand hardware vendor or model
     * @param clientSerialNr device serial for auditing
     * @param clientOS operating system name
     * @param patchLevel currently installed patch version
     * @param installType distribution type such as local or browser based
     * @return a transient client workstation entity ready to be saved
     */
    public Clients create(UUID siteID, String clientName, String clientBrand, String clientSerialNr,
                          String clientOS, String patchLevel, String installType) {
        return create(siteID, clientName, clientBrand, clientSerialNr, clientOS, patchLevel, installType, null, null);
    }

    /**
     * Creates a new record and persists it.
     * @param siteID site identifier.
     * @param clientName client name.
     * @param clientBrand client brand.
     * @param clientSerialNr client serial nr.
     * @param clientOS client os.
     * @param patchLevel patch level.
     * @param installType install type.
     * @param workingPositionType working position type.
     * @param otherInstalledSoftware other installed software.
     * @return the computed result.
     */
    public Clients create(UUID siteID, String clientName, String clientBrand, String clientSerialNr,
                          String clientOS, String patchLevel, String installType,
                          String workingPositionType, String otherInstalledSoftware) {
        return new Clients(null, siteID, clientName, clientBrand, clientSerialNr, clientOS, patchLevel, installType,
                workingPositionType, otherInstalledSoftware);
    }
}
