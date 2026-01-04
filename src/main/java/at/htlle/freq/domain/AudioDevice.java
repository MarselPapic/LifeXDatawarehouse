package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Represents an audio peripheral that belongs to a {@link Clients} client
 * workstation. Audio devices include headsets, speakers, or microphones and
 * are tracked by firmware revision and serial number to support lifecycle
 * management.
 */
public class AudioDevice {
    private UUID audioDeviceID;
    private UUID clientID;
    private String audioDeviceBrand;
    private String deviceSerialNr;
    private String audioDeviceFirmware;
    private String deviceType; // HEADSET / SPEAKER / MIC
    private String direction;  // Input / Output / Input + Output

    /**
     * Creates a new AudioDevice instance.
     */
    public AudioDevice() {}
    /**
     * Creates a new AudioDevice instance and initializes it with the provided values.
     * @param audioDeviceID audio device identifier.
     * @param clientID client identifier.
     * @param audioDeviceBrand audio device brand.
     * @param deviceSerialNr device serial nr.
     * @param audioDeviceFirmware audio device firmware.
     * @param deviceType device type.
     * @param direction direction.
     */
    public AudioDevice(UUID audioDeviceID, UUID clientID, String audioDeviceBrand, String deviceSerialNr,
                       String audioDeviceFirmware, String deviceType, String direction) {
        this.audioDeviceID = audioDeviceID;
        this.clientID = clientID;
        this.audioDeviceBrand = audioDeviceBrand;
        this.deviceSerialNr = deviceSerialNr;
        this.audioDeviceFirmware = audioDeviceFirmware;
        this.deviceType = deviceType;
        this.direction = direction;
    }

    /**
     * Returns the Audio Device ID value held by this instance.
     * @return the Audio Device ID value.
     */
    public UUID getAudioDeviceID() { return audioDeviceID; }
    /**
     * Sets the Audio Device ID value and updates the current state.
     * @param audioDeviceID audio device identifier.
     */
    public void setAudioDeviceID(UUID audioDeviceID) { this.audioDeviceID = audioDeviceID; }

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
     * Returns the Audio Device Brand value held by this instance.
     * @return the Audio Device Brand value.
     */
    public String getAudioDeviceBrand() { return audioDeviceBrand; }
    /**
     * Sets the Audio Device Brand value and updates the current state.
     * @param audioDeviceBrand audio device brand.
     */
    public void setAudioDeviceBrand(String audioDeviceBrand) { this.audioDeviceBrand = audioDeviceBrand; }

    /**
     * Returns the Device Serial Nr value held by this instance.
     * @return the Device Serial Nr value.
     */
    public String getDeviceSerialNr() { return deviceSerialNr; }
    /**
     * Sets the Device Serial Nr value and updates the current state.
     * @param deviceSerialNr device serial nr.
     */
    public void setDeviceSerialNr(String deviceSerialNr) { this.deviceSerialNr = deviceSerialNr; }

    /**
     * Returns the Audio Device Firmware value held by this instance.
     * @return the Audio Device Firmware value.
     */
    public String getAudioDeviceFirmware() { return audioDeviceFirmware; }
    /**
     * Sets the Audio Device Firmware value and updates the current state.
     * @param audioDeviceFirmware audio device firmware.
     */
    public void setAudioDeviceFirmware(String audioDeviceFirmware) { this.audioDeviceFirmware = audioDeviceFirmware; }

    /**
     * Returns the Device Type value held by this instance.
     * @return the Device Type value.
     */
    public String getDeviceType() { return deviceType; }
    /**
     * Sets the Device Type value and updates the current state.
     * @param deviceType device type.
     */
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    /**
     * Returns the Direction value held by this instance.
     * @return the Direction value.
     */
    public String getDirection() { return direction; }
    /**
     * Sets the Direction value and updates the current state.
     * @param direction direction.
     */
    public void setDirection(String direction) { this.direction = direction; }
}
