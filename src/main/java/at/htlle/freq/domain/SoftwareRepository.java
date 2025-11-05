package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository abstraction for {@link Software} catalogue entries.
 */
public interface SoftwareRepository {
    /**
     * Retrieves software by its identifier.
     *
     * @param id primary key
     * @return optional software entity
     */
    Optional<Software> findById(UUID id);

    /**
     * Finds software packages that share the provided name.
     *
     * @param name software name filter
     * @return list of matching software entries
     */
    List<Software> findByName(String name);

    /**
     * Persists the provided software entity.
     *
     * @param software software to store
     * @return managed entity after persistence
     */
    Software save(Software software);

    /**
     * Lists all software entries.
     *
     * @return snapshot of software catalogue
     */
    List<Software> findAll();
}
