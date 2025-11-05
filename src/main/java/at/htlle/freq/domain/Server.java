package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Describes an infrastructure server that supports a {@link Site}. Server
 * instances capture hardware, operating system and virtualization properties
 * and indicate whether the node participates in a high-availability setup.
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
    private boolean highAvailability;

    public Server() {}
    public Server(UUID serverID, UUID siteID, String serverName, String serverBrand, String serverSerialNr,
                  String serverOS, String patchLevel, String virtualPlatform, String virtualVersion,
                  boolean highAvailability) {
        this.serverID = serverID;
        this.siteID = siteID;
        this.serverName = serverName;
        this.serverBrand = serverBrand;
        this.serverSerialNr = serverSerialNr;
        this.serverOS = serverOS;
        this.patchLevel = patchLevel;
        this.virtualPlatform = virtualPlatform;
        this.virtualVersion = virtualVersion;
        this.highAvailability = highAvailability;
    }

    public UUID getServerID() { return serverID; }
    public void setServerID(UUID serverID) { this.serverID = serverID; }

    public UUID getSiteID() { return siteID; }
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public String getServerBrand() { return serverBrand; }
    public void setServerBrand(String serverBrand) { this.serverBrand = serverBrand; }

    public String getServerSerialNr() { return serverSerialNr; }
    public void setServerSerialNr(String serverSerialNr) { this.serverSerialNr = serverSerialNr; }

    public String getServerOS() { return serverOS; }
    public void setServerOS(String serverOS) { this.serverOS = serverOS; }

    public String getPatchLevel() { return patchLevel; }
    public void setPatchLevel(String patchLevel) { this.patchLevel = patchLevel; }

    public String getVirtualPlatform() { return virtualPlatform; }
    public void setVirtualPlatform(String virtualPlatform) { this.virtualPlatform = virtualPlatform; }

    public String getVirtualVersion() { return virtualVersion; }
    public void setVirtualVersion(String virtualVersion) { this.virtualVersion = virtualVersion; }

    public boolean isHighAvailability() { return highAvailability; }
    public void setHighAvailability(boolean highAvailability) { this.highAvailability = highAvailability; }
}
