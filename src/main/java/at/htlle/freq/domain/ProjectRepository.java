package at.htlle.freq.domain;

import java.util.*;

/**
 * Repository abstraction for {@link Project} deployment projects executed for
 * customer accounts.
 */
public interface ProjectRepository {
    /**
     * Retrieves a project by its technical identifier.
     *
     * @param id primary key
     * @return optional project when found
     */
    Optional<Project> findById(UUID id);

    /**
     * Looks up a project using its SAP identifier.
     *
     * @param sapId external SAP id
     * @return optional project if available
     */
    Optional<Project> findBySapId(String sapId);

    /**
     * Persists the supplied project aggregate.
     *
     * @param project project to store
     * @return managed entity after persistence
     */
    Project save(Project project);

    /**
     * Lists all projects.
     *
     * @return snapshot of every project
     */
    List<Project> findAll();
}
