package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class AudioDeviceFactory {
    /**
     * Creates a new {@link AudioDevice} attached to the given {@link Clients}
     * endpoint. The device identifier stays {@code null} until persistence.
     *
     * @param clientID owning client installation
     * @param brand vendor or model family name
     * @param serialNr hardware serial number for traceability
     * @param firmware firmware revision reported by the device
     * @param deviceType classification such as headset, speaker, or microphone
     * @return a transient audio device instance
     */
    public AudioDevice create(UUID clientID, String brand, String serialNr, String firmware, String deviceType) {
        return new AudioDevice(null, clientID, brand, serialNr, firmware, deviceType);
    }
}
