package at.htlle.freq.domain;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ProjectFactory {
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
