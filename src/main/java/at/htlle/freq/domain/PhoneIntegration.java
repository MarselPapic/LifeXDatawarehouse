package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Describes the telephony integration that is wired to a {@link Clients}
 * workstation. The record keeps track of device model, firmware and supported
 * emergency capabilities to align with regulatory requirements.
 */
public class PhoneIntegration {
    private UUID phoneIntegrationID;
    private UUID clientID;
    private String phoneType;    // Emergency / NonEmergency / Both
    private String phoneBrand;
    private String phoneSerialNr;
    private String phoneFirmware;

    public PhoneIntegration() {}
    public PhoneIntegration(UUID phoneIntegrationID, UUID clientID, String phoneType,
                            String phoneBrand, String phoneSerialNr, String phoneFirmware) {
        this.phoneIntegrationID = phoneIntegrationID;
        this.clientID = clientID;
        this.phoneType = phoneType;
        this.phoneBrand = phoneBrand;
        this.phoneSerialNr = phoneSerialNr;
        this.phoneFirmware = phoneFirmware;
    }

    public UUID getPhoneIntegrationID() { return phoneIntegrationID; }
    public void setPhoneIntegrationID(UUID phoneIntegrationID) { this.phoneIntegrationID = phoneIntegrationID; }

    public UUID getClientID() { return clientID; }
    public void setClientID(UUID clientID) { this.clientID = clientID; }

    public String getPhoneType() { return phoneType; }
    public void setPhoneType(String phoneType) { this.phoneType = phoneType; }

    public String getPhoneBrand() { return phoneBrand; }
    public void setPhoneBrand(String phoneBrand) { this.phoneBrand = phoneBrand; }

    public String getPhoneSerialNr() { return phoneSerialNr; }
    public void setPhoneSerialNr(String phoneSerialNr) { this.phoneSerialNr = phoneSerialNr; }

    public String getPhoneFirmware() { return phoneFirmware; }
    public void setPhoneFirmware(String phoneFirmware) { this.phoneFirmware = phoneFirmware; }
}
