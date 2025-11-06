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

    public Radio() {}
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

    public UUID getRadioID() { return radioID; }
    public void setRadioID(UUID radioID) { this.radioID = radioID; }

    public UUID getSiteID() { return siteID; }
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    public UUID getAssignedClientID() { return assignedClientID; }
    public void setAssignedClientID(UUID assignedClientID) { this.assignedClientID = assignedClientID; }

    public String getRadioBrand() { return radioBrand; }
    public void setRadioBrand(String radioBrand) { this.radioBrand = radioBrand; }

    public String getRadioSerialNr() { return radioSerialNr; }
    public void setRadioSerialNr(String radioSerialNr) { this.radioSerialNr = radioSerialNr; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getDigitalStandard() { return digitalStandard; }
    public void setDigitalStandard(String digitalStandard) { this.digitalStandard = digitalStandard; }
}
