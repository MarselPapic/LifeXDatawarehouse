// src/main/java/at/htlle/freq/infrastructure/lucene/LuceneIndexService.java
package at.htlle.freq.infrastructure.lucene;

import at.htlle.freq.infrastructure.search.SearchHit;
import org.apache.lucene.search.Query;

import java.nio.file.Path;
import java.util.List;

public interface LuceneIndexService {

    String INDEX_PATH = "target/lifex-index";

    Path getIndexPath();

    void setIndexPath(Path indexPath);

    // ========= Suche =========
    List<SearchHit> search(String queryText);
    List<SearchHit> search(Query query);

    // ========= Verwaltung =========
    void reindexAll();

    // ========= Index-APIs =========
    void indexAccount(String accountId, String accountName, String country, String contactEmail);
    void indexAddress(String addressId, String street, String cityId);
    void indexCity(String cityId, String cityName, String countryCode);
    void indexClient(String clientId, String siteId, String clientName, String clientBrand, String clientOS, String installType);
    void indexCountry(String countryCode, String countryName);
    void indexAudioDevice(String audioDeviceId, String clientId, String brand, String serialNr, String firmware, String deviceType);
    void indexDeploymentVariant(String variantId, String variantCode, String variantName, String description, boolean active);
    void indexInstalledSoftware(String installedSoftwareId, String siteId, String softwareId);
    void indexPhoneIntegration(String phoneIntegrationId, String clientId, String phoneType, String phoneBrand, String phoneSerialNr, String phoneFirmware);
    void indexProject(String projectId, String projectSAPId, String projectName, String deploymentVariantId, String bundleType, boolean stillActive,
                      String accountId, String addressId);
    void indexRadio(String radioId, String siteId, String assignedClientId, String radioBrand, String radioSerialNr, String mode, String digitalStandard);
    void indexServer(String serverId, String siteId, String serverName, String serverBrand, String serverSerialNr, String serverOS,
                     String patchLevel, String virtualPlatform, String virtualVersion, boolean highAvailability);
    void indexServiceContract(String contractId, String accountId, String projectId, String siteId, String contractNumber, String status,
                              String startDate, String endDate);
    void indexSite(String siteId, String projectId, String addressId, String siteName, String fireZone, Integer tenantCount);
    void indexSoftware(String softwareId, String name, String release, String revision, String supportPhase,
                       String licenseModel, String endOfSalesDate, String supportStartDate, String supportEndDate);
    void indexUpgradePlan(String upgradePlanId, String siteId, String softwareId, String plannedWindowStart, String plannedWindowEnd,
                          String status, String createdAt, String createdBy);
}
