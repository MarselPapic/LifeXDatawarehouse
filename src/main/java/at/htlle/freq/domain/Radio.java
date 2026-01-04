package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Represents a radio unit that can be deployed at a {@link Site} and
 * optionally assigned to a {@link Clients} client workstation. Mode and
 * digital standard values are persisted to manage compatibility and
 * interoperability obligations.
 */
public class Radio {
    private UUID radioID;
    private UUID siteID;
    private UUID assignedClientID; // nullable
    private String radioBrand;
    private String radioSerialNr;
    private String mode;            // Analog / Digital
    private String digitalStandard; // Airbus / Motorola / ESN / P25 / Polycom / Teltronics (nullable)

    /**
     * Creates a new Radio instance.
     */
    public Radio() {}
    /**
     * Creates a new Radio instance and initializes it with the provided values.
     * @param radioID radio identifier.
     * @param siteID site identifier.
     * @param assignedClientID assigned client identifier.
     * @param radioBrand radio brand.
     * @param radioSerialNr radio serial nr.
     * @param mode mode.
     * @param digitalStandard digital standard.
     */
    public Radio(UUID radioID, UUID siteID, UUID assignedClientID, String radioBrand, String radioSerialNr,
                 String mode, String digitalStandard) {
        this.radioID = radioID;
        this.siteID = siteID;
        this.assignedClientID = assignedClientID;
        this.radioBrand = radioBrand;
        this.radioSerialNr = radioSerialNr;
        this.mode = mode;
        this.digitalStandard = digitalStandard;
    }

    /**
     * Returns the Radio ID value held by this instance.
     * @return the Radio ID value.
     */
    public UUID getRadioID() { return radioID; }
    /**
     * Sets the Radio ID value and updates the current state.
     * @param radioID radio identifier.
     */
    public void setRadioID(UUID radioID) { this.radioID = radioID; }

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
     * Returns the Assigned Client ID value held by this instance.
     * @return the Assigned Client ID value.
     */
    public UUID getAssignedClientID() { return assignedClientID; }
    /**
     * Sets the Assigned Client ID value and updates the current state.
     * @param assignedClientID assigned client identifier.
     */
    public void setAssignedClientID(UUID assignedClientID) { this.assignedClientID = assignedClientID; }

    /**
     * Returns the Radio Brand value held by this instance.
     * @return the Radio Brand value.
     */
    public String getRadioBrand() { return radioBrand; }
    /**
     * Sets the Radio Brand value and updates the current state.
     * @param radioBrand radio brand.
     */
    public void setRadioBrand(String radioBrand) { this.radioBrand = radioBrand; }

    /**
     * Returns the Radio Serial Nr value held by this instance.
     * @return the Radio Serial Nr value.
     */
    public String getRadioSerialNr() { return radioSerialNr; }
    /**
     * Sets the Radio Serial Nr value and updates the current state.
     * @param radioSerialNr radio serial nr.
     */
    public void setRadioSerialNr(String radioSerialNr) { this.radioSerialNr = radioSerialNr; }

    /**
     * Returns the Mode value held by this instance.
     * @return the Mode value.
     */
    public String getMode() { return mode; }
    /**
     * Sets the Mode value and updates the current state.
     * @param mode mode.
     */
    public void setMode(String mode) { this.mode = mode; }

    /**
     * Returns the Digital Standard value held by this instance.
     * @return the Digital Standard value.
     */
    public String getDigitalStandard() { return digitalStandard; }
    /**
     * Sets the Digital Standard value and updates the current state.
     * @param digitalStandard digital standard.
     */
    public void setDigitalStandard(String digitalStandard) { this.digitalStandard = digitalStandard; }
}
