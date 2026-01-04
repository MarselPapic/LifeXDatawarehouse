// src/main/java/at/htlle/freq/infrastructure/camel/UnifiedIndexingRoutes.java
package at.htlle.freq.infrastructure.camel;

import at.htlle.freq.domain.*;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/*
 * Unified Camel routes for producing Lucene index messages.
 *
 * Data flow:
 *  - Timer sources fire every three minutes, read sequentially from the JPA repositories, and stream individual entities.
 *  - Each source writes its records to the central "seda:lucene-index" queue.
 *  - Direct endpoints allow ad-hoc indexing (for example after CRUD events) and also publish to the queue.
 *
 * Retry / locking considerations:
 *  - The SEDA queue is configured with blockWhenFull=true (see configuration) to avoid backpressure issues.
 *  - Serialization happens downstream (LuceneIndexingHubRoute + LuceneIndexServiceImpl), so no extra locks are required because
 *    Camel preserves ordering within a route.
 *
 * Integration points:
 *  - Obtains all repositories via Spring, enabling the timers to run complete reindex jobs (see the log output in reindexAll()).
 *  - Delivers messages to LuceneIndexingHubRoute, which in turn drives the Lucene API.
 */
/**
 * Component that provides Unified Indexing Routes behavior.
 */
@Component("UnifiedIndexingRoutes")
@ConditionalOnProperty(value = "lifex.lucene.camel.enabled", havingValue = "true", matchIfMissing = true)
public class UnifiedIndexingRoutes extends RouteBuilder {

    // --- Repositories: capture every dependency ---
    private final AccountRepository accountRepo;
    private final AddressRepository addressRepo;
    private final AudioDeviceRepository audioDeviceRepo;
    private final CityRepository cityRepo;
    private final ClientsRepository clientsRepo;
    private final CountryRepository countryRepo;
    private final DeploymentVariantRepository deploymentVariantRepo;
    private final InstalledSoftwareRepository installedSoftwareRepo;
    private final PhoneIntegrationRepository phoneIntegrationRepo;
    private final ProjectRepository projectRepo;
    private final RadioRepository radioRepo;
    private final ServerRepository serverRepo;
    private final ServiceContractRepository serviceContractRepo;
    private final SiteRepository siteRepo;
    private final SoftwareRepository softwareRepo;
    private final UpgradePlanRepository upgradePlanRepo;

    /**
     * Creates a new UnifiedIndexingRoutes instance and initializes it with the provided values.
     * @param accountRepo account repo.
     * @param addressRepo address repo.
     * @param audioDeviceRepo audio device repo.
     * @param cityRepo city repo.
     * @param clientsRepo clients repo.
     * @param countryRepo country repo.
     * @param deploymentVariantRepo deployment variant repo.
     * @param installedSoftwareRepo installed software repo.
     * @param phoneIntegrationRepo phone integration repo.
     * @param projectRepo project repo.
     * @param radioRepo radio repo.
     * @param serverRepo server repo.
     * @param serviceContractRepo service contract repo.
     * @param siteRepo site repo.
     * @param softwareRepo software repo.
     * @param upgradePlanRepo upgrade plan repo.
     */
    public UnifiedIndexingRoutes(AccountRepository accountRepo,
                                 AddressRepository addressRepo,
                                 AudioDeviceRepository audioDeviceRepo,
                                 CityRepository cityRepo,
                                 ClientsRepository clientsRepo,
                                 CountryRepository countryRepo,
                                 DeploymentVariantRepository deploymentVariantRepo,
                                 InstalledSoftwareRepository installedSoftwareRepo,
                                 PhoneIntegrationRepository phoneIntegrationRepo,
                                 ProjectRepository projectRepo,
                                 RadioRepository radioRepo,
                                 ServerRepository serverRepo,
                                 ServiceContractRepository serviceContractRepo,
                                 SiteRepository siteRepo,
                                 SoftwareRepository softwareRepo,
                                 UpgradePlanRepository upgradePlanRepo) {
        this.accountRepo = accountRepo;
        this.addressRepo = addressRepo;
        this.audioDeviceRepo = audioDeviceRepo;
        this.cityRepo = cityRepo;
        this.clientsRepo = clientsRepo;
        this.countryRepo = countryRepo;
        this.deploymentVariantRepo = deploymentVariantRepo;
        this.installedSoftwareRepo = installedSoftwareRepo;
        this.phoneIntegrationRepo = phoneIntegrationRepo;
        this.projectRepo = projectRepo;
        this.radioRepo = radioRepo;
        this.serverRepo = serverRepo;
        this.serviceContractRepo = serviceContractRepo;
        this.siteRepo = siteRepo;
        this.softwareRepo = softwareRepo;
        this.upgradePlanRepo = upgradePlanRepo;
    }

    @Override
    /**
     * Wires timers and direct endpoints to the shared SEDA queue.
     * Each timer fires every 180 seconds and streams entities so even large tables are processed incrementally.
     */
    public void configure() {

        // ===== Timer-based reindex for every entity, routed into the shared SEDA queue =====
        from("timer://idxAccounts?period=180000").routeId("ReindexAccounts")
                .bean(accountRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxAddresses?period=180000").routeId("ReindexAddresses")
                .bean(addressRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxAudioDevices?period=180000").routeId("ReindexAudioDevices")
                .bean(audioDeviceRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxCities?period=180000").routeId("ReindexCities")
                .bean(cityRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxClients?period=180000").routeId("ReindexClients")
                .bean(clientsRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxCountries?period=180000").routeId("ReindexCountries")
                .bean(countryRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxDeploymentVariants?period=180000").routeId("ReindexDeploymentVariants")
                .bean(deploymentVariantRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxInstalledSoftware?period=180000").routeId("ReindexInstalledSoftware")
                .bean(installedSoftwareRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxPhoneIntegrations?period=180000").routeId("ReindexPhoneIntegrations")
                .bean(phoneIntegrationRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxProjects?period=180000").routeId("ReindexProjects")
                .bean(projectRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxRadios?period=180000").routeId("ReindexRadios")
                .bean(radioRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxServers?period=180000").routeId("ReindexServers")
                .bean(serverRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxServiceContracts?period=180000").routeId("ReindexServiceContracts")
                .bean(serviceContractRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxSites?period=180000").routeId("ReindexSites")
                .bean(siteRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxSoftware?period=180000").routeId("ReindexSoftware")
                .bean(softwareRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        from("timer://idxUpgradePlans?period=180000").routeId("ReindexUpgradePlans")
                .bean(upgradePlanRepo, "findAll").split(body()).streaming()
                .to("seda:lucene-index?size=2000&blockWhenFull=true");

        // ===== Single index endpoints, also routed into the queue =====
        from("direct:index-single-account").routeId("IndexSingleAccount").to("seda:lucene-index");
        from("direct:index-single-address").routeId("IndexSingleAddress").to("seda:lucene-index");
        from("direct:index-single-audioDevice").routeId("IndexSingleAudioDevice").to("seda:lucene-index");
        from("direct:index-single-city").routeId("IndexSingleCity").to("seda:lucene-index");
        from("direct:index-single-client").routeId("IndexSingleClient").to("seda:lucene-index");
        from("direct:index-single-country").routeId("IndexSingleCountry").to("seda:lucene-index");
        from("direct:index-single-deploymentVariant").routeId("IndexSingleDeploymentVariant").to("seda:lucene-index");
        from("direct:index-single-installedSoftware").routeId("IndexSingleInstalledSoftware").to("seda:lucene-index");
        from("direct:index-single-phoneIntegration").routeId("IndexSinglePhoneIntegration").to("seda:lucene-index");
        from("direct:index-single-project").routeId("IndexSingleProject").to("seda:lucene-index");
        from("direct:index-single-radio").routeId("IndexSingleRadio").to("seda:lucene-index");
        from("direct:index-single-server").routeId("IndexSingleServer").to("seda:lucene-index");
        from("direct:index-single-serviceContract").routeId("IndexSingleServiceContract").to("seda:lucene-index");
        from("direct:index-single-site").routeId("IndexSingleSite").to("seda:lucene-index");
        from("direct:index-single-software").routeId("IndexSingleSoftware").to("seda:lucene-index");
        from("direct:index-single-upgradePlan").routeId("IndexSingleUpgradePlan").to("seda:lucene-index");
    }
}
