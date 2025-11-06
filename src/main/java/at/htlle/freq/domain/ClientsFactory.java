package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

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
        return new Clients(null, siteID, clientName, clientBrand, clientSerialNr, clientOS, patchLevel, installType);
    }
}
