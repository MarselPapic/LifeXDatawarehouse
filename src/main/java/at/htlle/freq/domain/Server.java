package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Describes an infrastructure server that supports a {@link Site}. Server
 * instances capture hardware, operating system and virtualization properties.
 */
public class Server {
    private UUID serverID;
    private UUID siteID;
    private String serverName;
    private String serverBrand;
    private String serverSerialNr;
    private String serverOS;
    private String patchLevel;
    private String virtualPlatform; // BareMetal / HyperV / vSphere
    private String virtualVersion;  // nullable

    /**
     * Creates a new Server instance.
     */
    public Server() {}
    /**
     * Creates a new Server instance and initializes it with the provided values.
     * @param serverID server identifier.
     * @param siteID site identifier.
     * @param serverName server name.
     * @param serverBrand server brand.
     * @param serverSerialNr server serial nr.
     * @param serverOS server os.
     * @param patchLevel patch level.
     * @param virtualPlatform virtual platform.
     * @param virtualVersion virtual version.
     */
    public Server(UUID serverID, UUID siteID, String serverName, String serverBrand, String serverSerialNr,
                  String serverOS, String patchLevel, String virtualPlatform, String virtualVersion) {
        this.serverID = serverID;
        this.siteID = siteID;
        this.serverName = serverName;
        this.serverBrand = serverBrand;
        this.serverSerialNr = serverSerialNr;
        this.serverOS = serverOS;
        this.patchLevel = patchLevel;
        this.virtualPlatform = virtualPlatform;
        this.virtualVersion = virtualVersion;
    }

    /**
     * Returns the Server ID value held by this instance.
     * @return the Server ID value.
     */
    public UUID getServerID() { return serverID; }
    /**
     * Sets the Server ID value and updates the current state.
     * @param serverID server identifier.
     */
    public void setServerID(UUID serverID) { this.serverID = serverID; }

    /**
     * Returns the Site ID value held by this instance.
     * @return the Site ID value.
     */
    public UUID getSiteID() { return siteID; }
    /**
     * Sets the Site ID value and updates the current state.
     * @param siteID site identifier.
     */
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    /**
     * Returns the Server Name value held by this instance.
     * @return the Server Name value.
     */
    public String getServerName() { return serverName; }
    /**
     * Sets the Server Name value and updates the current state.
     * @param serverName server name.
     */
    public void setServerName(String serverName) { this.serverName = serverName; }

    /**
     * Returns the Server Brand value held by this instance.
     * @return the Server Brand value.
     */
    public String getServerBrand() { return serverBrand; }
    /**
     * Sets the Server Brand value and updates the current state.
     * @param serverBrand server brand.
     */
    public void setServerBrand(String serverBrand) { this.serverBrand = serverBrand; }

    /**
     * Returns the Server Serial Nr value held by this instance.
     * @return the Server Serial Nr value.
     */
    public String getServerSerialNr() { return serverSerialNr; }
    /**
     * Sets the Server Serial Nr value and updates the current state.
     * @param serverSerialNr server serial nr.
     */
    public void setServerSerialNr(String serverSerialNr) { this.serverSerialNr = serverSerialNr; }

    /**
     * Returns the Server OS value held by this instance.
     * @return the Server OS value.
     */
    public String getServerOS() { return serverOS; }
    /**
     * Sets the Server OS value and updates the current state.
     * @param serverOS server os.
     */
    public void setServerOS(String serverOS) { this.serverOS = serverOS; }

    /**
     * Returns the Patch Level value held by this instance.
     * @return the Patch Level value.
     */
    public String getPatchLevel() { return patchLevel; }
    /**
     * Sets the Patch Level value and updates the current state.
     * @param patchLevel patch level.
     */
    public void setPatchLevel(String patchLevel) { this.patchLevel = patchLevel; }

    /**
     * Returns the Virtual Platform value held by this instance.
     * @return the Virtual Platform value.
     */
    public String getVirtualPlatform() { return virtualPlatform; }
    /**
     * Sets the Virtual Platform value and updates the current state.
     * @param virtualPlatform virtual platform.
     */
    public void setVirtualPlatform(String virtualPlatform) { this.virtualPlatform = virtualPlatform; }

    /**
     * Returns the Virtual Version value held by this instance.
     * @return the Virtual Version value.
     */
    public String getVirtualVersion() { return virtualVersion; }
    /**
     * Sets the Virtual Version value and updates the current state.
     * @param virtualVersion virtual version.
     */
    public void setVirtualVersion(String virtualVersion) { this.virtualVersion = virtualVersion; }
}
