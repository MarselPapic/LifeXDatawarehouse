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
    private String workingPositionType;
    private String otherInstalledSoftware;

    /**
     * Creates a new Clients instance.
     */
    public Clients() {}
    /**
     * Creates a new Clients instance and initializes it with the provided values.
     * @param clientID client identifier.
     * @param siteID site identifier.
     * @param clientName client name.
     * @param clientBrand client brand.
     * @param clientSerialNr client serial nr.
     * @param clientOS client os.
     * @param patchLevel patch level.
     * @param installType install type.
     */
    public Clients(UUID clientID, UUID siteID, String clientName, String clientBrand, String clientSerialNr,
                   String clientOS, String patchLevel, String installType) {
        this(clientID, siteID, clientName, clientBrand, clientSerialNr, clientOS, patchLevel, installType, null, null);
    }
    /**
     * Creates a new Clients instance and initializes it with the provided values.
     * @param clientID client identifier.
     * @param siteID site identifier.
     * @param clientName client name.
     * @param clientBrand client brand.
     * @param clientSerialNr client serial nr.
     * @param clientOS client os.
     * @param patchLevel patch level.
     * @param installType install type.
     * @param workingPositionType working position type.
     * @param otherInstalledSoftware other installed software.
     */
    public Clients(UUID clientID, UUID siteID, String clientName, String clientBrand, String clientSerialNr,
                   String clientOS, String patchLevel, String installType,
                   String workingPositionType, String otherInstalledSoftware) {
        this.clientID = clientID;
        this.siteID = siteID;
        this.clientName = clientName;
        this.clientBrand = clientBrand;
        this.clientSerialNr = clientSerialNr;
        this.clientOS = clientOS;
        this.patchLevel = patchLevel;
        this.installType = installType;
        this.workingPositionType = workingPositionType;
        this.otherInstalledSoftware = otherInstalledSoftware;
    }

    /**
     * Returns the Client ID value held by this instance.
     * @return the Client ID value.
     */
    public UUID getClientID() { return clientID; }
    /**
     * Sets the Client ID value and updates the current state.
     * @param clientID client identifier.
     */
    public void setClientID(UUID clientID) { this.clientID = clientID; }

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
     * Returns the Client Name value held by this instance.
     * @return the Client Name value.
     */
    public String getClientName() { return clientName; }
    /**
     * Sets the Client Name value and updates the current state.
     * @param clientName client name.
     */
    public void setClientName(String clientName) { this.clientName = clientName; }

    /**
     * Returns the Client Brand value held by this instance.
     * @return the Client Brand value.
     */
    public String getClientBrand() { return clientBrand; }
    /**
     * Sets the Client Brand value and updates the current state.
     * @param clientBrand client brand.
     */
    public void setClientBrand(String clientBrand) { this.clientBrand = clientBrand; }

    /**
     * Returns the Client Serial Nr value held by this instance.
     * @return the Client Serial Nr value.
     */
    public String getClientSerialNr() { return clientSerialNr; }
    /**
     * Sets the Client Serial Nr value and updates the current state.
     * @param clientSerialNr client serial nr.
     */
    public void setClientSerialNr(String clientSerialNr) { this.clientSerialNr = clientSerialNr; }

    /**
     * Returns the Client OS value held by this instance.
     * @return the Client OS value.
     */
    public String getClientOS() { return clientOS; }
    /**
     * Sets the Client OS value and updates the current state.
     * @param clientOS client os.
     */
    public void setClientOS(String clientOS) { this.clientOS = clientOS; }

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
     * Returns the Install Type value held by this instance.
     * @return the Install Type value.
     */
    public String getInstallType() { return installType; }
    /**
     * Sets the Install Type value and updates the current state.
     * @param installType install type.
     */
    public void setInstallType(String installType) { this.installType = installType; }

    /**
     * Returns the Working Position Type value held by this instance.
     * @return the Working Position Type value.
     */
    public String getWorkingPositionType() { return workingPositionType; }
    /**
     * Sets the Working Position Type value and updates the current state.
     * @param workingPositionType working position type.
     */
    public void setWorkingPositionType(String workingPositionType) { this.workingPositionType = workingPositionType; }

    /**
     * Returns the Other Installed Software value held by this instance.
     * @return the Other Installed Software value.
     */
    public String getOtherInstalledSoftware() { return otherInstalledSoftware; }
    /**
     * Sets the Other Installed Software value and updates the current state.
     * @param otherInstalledSoftware other installed software.
     */
    public void setOtherInstalledSoftware(String otherInstalledSoftware) { this.otherInstalledSoftware = otherInstalledSoftware; }
}
