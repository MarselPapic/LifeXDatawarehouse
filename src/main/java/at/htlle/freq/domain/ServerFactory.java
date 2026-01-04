package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Factory responsible for creating Server instances.
 */
@Component
public class ServerFactory {
    /**
     * Creates a {@link Server} installation for a site. The identifier stays
     * unset to allow the database to allocate it.
     *
     * @param siteID hosting site identifier
     * @param serverName host name
     * @param serverBrand hardware brand
     * @param serverSerialNr serial number for audits
     * @param serverOS installed operating system
     * @param patchLevel applied patch level
     * @param virtualPlatform virtualization platform or bare metal marker
     * @param virtualVersion optional version of the virtualization platform
     * @return transient server entity
     */
    public Server create(UUID siteID, String serverName, String serverBrand, String serverSerialNr,
                         String serverOS, String patchLevel, String virtualPlatform,
                         String virtualVersion) {
        return new Server(null, siteID, serverName, serverBrand, serverSerialNr,
                serverOS, patchLevel, virtualPlatform, virtualVersion);
    }
}
