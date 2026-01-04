package at.htlle.freq.domain;

import org.springframework.stereotype.Component;

/**
 * Factory responsible for creating Software instances.
 */
@Component
public class SoftwareFactory {
    /**
     * Produces a {@link Software} catalogue entry with lifecycle metadata. The
     * identifier remains unset for database generation.
     *
     * @param name software product name
     * @param release major release identifier
     * @param revision revision or patch level
     * @param supportPhase lifecycle phase (preview, production, etc.)
     * @param licenseModel licensing model information
     * @param eos end of sales timestamp
     * @param supportStart support availability start
     * @param supportEnd support availability end
     * @return transient software entity
     */
    public Software create(String name, String release, String revision, String supportPhase,
                           String licenseModel, boolean thirdParty, String eos, String supportStart, String supportEnd) {
        return new Software(null, name, release, revision, supportPhase, licenseModel, thirdParty, eos, supportStart, supportEnd);
    }
}
