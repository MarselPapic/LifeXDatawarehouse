// src/main/java/at/htlle/freq/infrastructure/lucene/LuceneIndexService.java
package at.htlle.freq.infrastructure.lucene;

import at.htlle.freq.infrastructure.search.SearchHit;
import org.apache.lucene.search.Query;

import java.nio.file.Path;
import java.util.List;

/**
 * Defines the contract for managing the LifeX Lucene index, including query execution
 * and synchronising domain entities into the search index within the infrastructure layer.
 */
public interface LuceneIndexService {

    String INDEX_PATH = "target/lifex-index";

    Path getIndexPath();

    void setIndexPath(Path indexPath);

    /**
     * Executes a Lucene search based on the provided query text and returns matching hits.
     * Expected to translate user input into a Lucene query and map the results to {@link SearchHit} entries.
     */
    List<SearchHit> search(String queryText);

    /**
     * Executes a Lucene search using a pre-built {@link Query} instance and returns matching hits.
     * Used for advanced scenarios where the caller controls query construction.
     */
    List<SearchHit> search(Query query);

    /**
     * Rebuilds the entire Lucene index from the underlying data sources, ensuring stored documents
     * reflect the latest system state. Implementations should replace outdated entries as needed.
     */
    void reindexAll();

    /**
     * Indexes or updates account information so it becomes searchable via LifeX.
     * Implementations should persist the supplied identifiers and attributes to the Lucene index.
     */
    void indexAccount(String accountId, String accountName, String country, String contactEmail);

    /**
     * Indexes an address document linking it to the respective city reference for search operations.
     */
    void indexAddress(String addressId, String street, String cityId);

    /**
     * Indexes city details, ensuring name and ISO country code are available in the index.
     */
    void indexCity(String cityId, String cityName, String countryCode);

    /**
     * Indexes client metadata together with deployment context details, enabling entity-level lookup.
     */
    void indexClient(String clientId, String siteId, String clientName, String clientBrand, String clientOS, String installType);

    /**
     * Indexes country records for quick lookup via ISO code or display name.
     */
    void indexCountry(String countryCode, String countryName);

    /**
     * Indexes audio device metadata belonging to a client, updating searchable hardware information.
     */
    void indexAudioDevice(String audioDeviceId, String clientId, String brand, String serialNr, String firmware, String deviceType);

    /**
     * Indexes deployment variant information including lifecycle flags for project planning.
     */
    void indexDeploymentVariant(String variantId, String variantCode, String variantName, String description, boolean active);

    /**
     * Indexes installed software references for a site, reflecting current rollout status.
     */
    void indexInstalledSoftware(String installedSoftwareId, String siteId, String softwareId, String status);

    /**
     * Indexes phone integration hardware assigned to clients for operational tracking.
     */
    void indexPhoneIntegration(String phoneIntegrationId, String clientId, String phoneType, String phoneBrand, String phoneSerialNr, String phoneFirmware);

    /**
     * Indexes project metadata and its relations to deployment variants, accounts, and addresses.
     */
    void indexProject(String projectId, String projectSAPId, String projectName, String deploymentVariantId, String bundleType, String lifecycleStatus,
                      String accountId, String addressId);

    /**
     * Indexes radio devices with associated configuration details for operational queries.
     */
    void indexRadio(String radioId, String siteId, String assignedClientId, String radioBrand, String radioSerialNr, String mode, String digitalStandard);

    /**
     * Indexes server hardware records, including virtualisation details and availability flags.
     */
    void indexServer(String serverId, String siteId, String serverName, String serverBrand, String serverSerialNr, String serverOS,
                     String patchLevel, String virtualPlatform, String virtualVersion, boolean highAvailability);

    /**
     * Indexes service contracts to make lifecycle status, durations, and related entities searchable.
     */
    void indexServiceContract(String contractId, String accountId, String projectId, String siteId, String contractNumber, String status,
                              String startDate, String endDate);

    /**
     * Indexes site information with address linkage and optional tenancy details.
     */
    void indexSite(String siteId, String projectId, String addressId, String siteName, String fireZone, Integer tenantCount);

    /**
     * Indexes software catalogue entries, capturing release lifecycle and licensing attributes.
     */
    void indexSoftware(String softwareId, String name, String release, String revision, String supportPhase,
                       String licenseModel, boolean thirdParty, String endOfSalesDate, String supportStartDate, String supportEndDate);

    /**
     * Indexes upgrade plans for sites, preserving scheduling, status, and audit metadata.
     */
    void indexUpgradePlan(String upgradePlanId, String siteId, String softwareId, String plannedWindowStart, String plannedWindowEnd,
                          String status, String createdAt, String createdBy);
}
