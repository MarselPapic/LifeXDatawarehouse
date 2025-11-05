package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository contract for persisting {@link AudioDevice} peripherals.
 */
public interface AudioDeviceRepository {
    /**
     * Looks up an audio device by its identifier.
     *
     * @param id primary key of the device
     * @return the device or {@link Optional#empty()} if it is unknown
     */
    Optional<AudioDevice> findById(UUID id);

    /**
     * Fetches all audio devices assigned to the given client.
     *
     * @param clientId identifier of the owning {@link Clients} installation
     * @return list of matching audio devices
     */
    List<AudioDevice> findByClient(UUID clientId);

    /**
     * Saves the provided audio device, either creating a new record or updating
     * an existing one.
     *
     * @param device peripheral to persist
     * @return the managed entity after persistence
     */
    AudioDevice save(AudioDevice device);

    /**
     * Returns all known audio devices.
     *
     * @return snapshot of every audio device entity
     */
    List<AudioDevice> findAll();
}
