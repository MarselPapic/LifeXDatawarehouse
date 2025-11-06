package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ProjectFactory {
    /**
     * Builds a new {@link Project} aggregate that links to its customer account,
     * deployment variant and optional headquarters address. The identifier is
     * left {@code null} to be filled by the persistence layer.
     *
     * @param projectSAPID external SAP identifier for the project
     * @param projectName human readable project name
     * @param deploymentVariantID reference to the {@link DeploymentVariant}
     * @param bundleType commercial bundle selection
     * @param createDateTime creation timestamp captured as string
     * @param lifecycleStatus lifecycle position of the project
     * @param accountID owning customer {@link Account} identifier
     * @param addressID headquarters {@link Address} identifier
     * @return transient project entity
     */
    public Project create(String projectSAPID,
                          String projectName,
                          UUID deploymentVariantID,
                          String bundleType,
                          String createDateTime,
                          ProjectLifecycleStatus lifecycleStatus,
                          UUID accountID,
                          UUID addressID) {
        return new Project(null, projectSAPID, projectName, deploymentVariantID, bundleType,
                createDateTime, lifecycleStatus, accountID, addressID);
    }
}
