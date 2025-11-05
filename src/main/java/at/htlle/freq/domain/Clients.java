package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Models an installed client workstation at a {@link Site}. The entity tracks
 * hardware details, operating system, and installation type, while the
 * {@code clientID} is used as the parent reference for attached peripherals
 * such as {@link AudioDevice} or {@link PhoneIntegration} instances.
 */
public class Clients {
    private UUID clientID;
    private UUID siteID;
    private String clientName;
    private String clientBrand;
    private String clientSerialNr;
    private String clientOS;
    private String patchLevel;
    private String installType; // LOCAL / BROWSER

    public Clients() {}
    public Clients(UUID clientID, UUID siteID, String clientName, String clientBrand, String clientSerialNr,
                   String clientOS, String patchLevel, String installType) {
        this.clientID = clientID;
        this.siteID = siteID;
        this.clientName = clientName;
        this.clientBrand = clientBrand;
        this.clientSerialNr = clientSerialNr;
        this.clientOS = clientOS;
        this.patchLevel = patchLevel;
        this.installType = installType;
    }

    public UUID getClientID() { return clientID; }
    public void setClientID(UUID clientID) { this.clientID = clientID; }

    public UUID getSiteID() { return siteID; }
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientBrand() { return clientBrand; }
    public void setClientBrand(String clientBrand) { this.clientBrand = clientBrand; }

    public String getClientSerialNr() { return clientSerialNr; }
    public void setClientSerialNr(String clientSerialNr) { this.clientSerialNr = clientSerialNr; }

    public String getClientOS() { return clientOS; }
    public void setClientOS(String clientOS) { this.clientOS = clientOS; }

    public String getPatchLevel() { return patchLevel; }
    public void setPatchLevel(String patchLevel) { this.patchLevel = patchLevel; }

    public String getInstallType() { return installType; }
    public void setInstallType(String installType) { this.installType = installType; }
}
