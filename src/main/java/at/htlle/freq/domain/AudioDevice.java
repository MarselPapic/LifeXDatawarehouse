package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Represents an audio peripheral that belongs to a {@link Clients} endpoint.
 * Audio devices include headsets, speakers, or microphones and are tracked by
 * firmware revision and serial number to support lifecycle management.
 */
public class AudioDevice {
    private UUID audioDeviceID;
    private UUID clientID;
    private String audioDeviceBrand;
    private String deviceSerialNr;
    private String audioDeviceFirmware;
    private String deviceType; // HEADSET / SPEAKER / MIC

    public AudioDevice() {}
    public AudioDevice(UUID audioDeviceID, UUID clientID, String audioDeviceBrand, String deviceSerialNr,
                       String audioDeviceFirmware, String deviceType) {
        this.audioDeviceID = audioDeviceID;
        this.clientID = clientID;
        this.audioDeviceBrand = audioDeviceBrand;
        this.deviceSerialNr = deviceSerialNr;
        this.audioDeviceFirmware = audioDeviceFirmware;
        this.deviceType = deviceType;
    }

    public UUID getAudioDeviceID() { return audioDeviceID; }
    public void setAudioDeviceID(UUID audioDeviceID) { this.audioDeviceID = audioDeviceID; }

    public UUID getClientID() { return clientID; }
    public void setClientID(UUID clientID) { this.clientID = clientID; }

    public String getAudioDeviceBrand() { return audioDeviceBrand; }
    public void setAudioDeviceBrand(String audioDeviceBrand) { this.audioDeviceBrand = audioDeviceBrand; }

    public String getDeviceSerialNr() { return deviceSerialNr; }
    public void setDeviceSerialNr(String deviceSerialNr) { this.deviceSerialNr = deviceSerialNr; }

    public String getAudioDeviceFirmware() { return audioDeviceFirmware; }
    public void setAudioDeviceFirmware(String audioDeviceFirmware) { this.audioDeviceFirmware = audioDeviceFirmware; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
}
