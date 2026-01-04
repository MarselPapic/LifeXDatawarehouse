package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Describes the telephony integration that is wired to a
 * {@link Site} location. The record keeps track of device model,
 * firmware and supported emergency capabilities to align with
 * regulatory requirements.
 */
public class PhoneIntegration {
    private UUID phoneIntegrationID;
    private UUID siteID;
    private String phoneType;    // Emergency / NonEmergency / Both
    private String phoneBrand;
    private String interfaceName;
    private Integer capacity;
    private String phoneFirmware;

    /**
     * Creates an empty phone integration instance.
     */
    public PhoneIntegration() {}
    /**
     * Creates a phone integration record with all attributes set.
     *
     * @param phoneIntegrationID phone integration identifier.
     * @param siteID site identifier.
     * @param phoneType phone type.
     * @param phoneBrand phone brand.
     * @param interfaceName interface name.
     * @param capacity capacity.
     * @param phoneFirmware phone firmware.
     */
    public PhoneIntegration(UUID phoneIntegrationID, UUID siteID, String phoneType,
                            String phoneBrand, String interfaceName, Integer capacity, String phoneFirmware) {
        this.phoneIntegrationID = phoneIntegrationID;
        this.siteID = siteID;
        this.phoneType = phoneType;
        this.phoneBrand = phoneBrand;
        this.interfaceName = interfaceName;
        this.capacity = capacity;
        this.phoneFirmware = phoneFirmware;
    }

    /**
     * Returns the Phone Integration ID value held by this instance.
     * @return the Phone Integration ID value.
     */
    public UUID getPhoneIntegrationID() { return phoneIntegrationID; }
    /**
     * Sets the Phone Integration ID value and updates the current state.
     * @param phoneIntegrationID phone integration identifier.
     */
    public void setPhoneIntegrationID(UUID phoneIntegrationID) { this.phoneIntegrationID = phoneIntegrationID; }

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
     * Returns the Phone Type value held by this instance.
     * @return the Phone Type value.
     */
    public String getPhoneType() { return phoneType; }
    /**
     * Sets the Phone Type value and updates the current state.
     * @param phoneType phone type.
     */
    public void setPhoneType(String phoneType) { this.phoneType = phoneType; }

    /**
     * Returns the Phone Brand value held by this instance.
     * @return the Phone Brand value.
     */
    public String getPhoneBrand() { return phoneBrand; }
    /**
     * Sets the Phone Brand value and updates the current state.
     * @param phoneBrand phone brand.
     */
    public void setPhoneBrand(String phoneBrand) { this.phoneBrand = phoneBrand; }

    /**
     * Returns the Interface Name value held by this instance.
     * @return the Interface Name value.
     */
    public String getInterfaceName() { return interfaceName; }
    /**
     * Sets the Interface Name value and updates the current state.
     * @param interfaceName interface name.
     */
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }

    /**
     * Returns the Capacity value held by this instance.
     * @return the Capacity value.
     */
    public Integer getCapacity() { return capacity; }
    /**
     * Sets the Capacity value and updates the current state.
     * @param capacity capacity.
     */
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    /**
     * Returns the Phone Firmware value held by this instance.
     * @return the Phone Firmware value.
     */
    public String getPhoneFirmware() { return phoneFirmware; }
    /**
     * Sets the Phone Firmware value and updates the current state.
     * @param phoneFirmware phone firmware.
     */
    public void setPhoneFirmware(String phoneFirmware) { this.phoneFirmware = phoneFirmware; }
}
