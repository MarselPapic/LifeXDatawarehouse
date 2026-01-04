package at.htlle.freq.infrastructure.lucene;

import at.htlle.freq.domain.*;
import at.htlle.freq.infrastructure.search.SearchHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockObtainFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Central Lucene write service.
 *
 * Data flow:
 *  - Camel routes (timers & direct endpoints) push domain entities into the "seda:lucene-index" queue.
 *  - LuceneIndexingHubRoute unwraps the entities and invokes the matching indexXxx() methods.
 *  - Each indexXxx() method consolidates fields, forwards them to indexDocument(), and persists them in Lucene.
 *
 * Retry & locking strategy:
 *  - The IndexWriter is serialized via withWriter() and a ReentrantLock so that concurrent access from multiple threads (e.g.,
 *    timer plus on-demand) is queued.
 *  - If Lucene fails with a write.lock, the service attempts to clean up the lock once via obtainLock()—see the warnings in
 *    clearStaleLock(). Afterwards the error is logged and propagated.
 *
 * Integration points:
 *  - Repositories provide the data for reindexAll(); schedulers/timers trigger this path as configured in UnifiedIndexingRoutes.
 *  - The JSON license fragments stay in sync so REST controllers and the index expose the same information.
 */
/**
 * Component that provides Lucene Index Service Impl behavior.
 */
@Service
public class LuceneIndexServiceImpl implements LuceneIndexService {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexServiceImpl.class);
    private static final int PROGRESS_LOG_INTERVAL = 250;
    private static final String TYPE_ACCOUNT = "account";
    private static final String TYPE_ADDRESS = "address";
    private static final String TYPE_AUDIO_DEVICE = "audioDevice";
    private static final String TYPE_CITY = "city";
    private static final String TYPE_CLIENT = "client";
    private static final String TYPE_COUNTRY = "country";
    private static final String TYPE_DEPLOYMENT_VARIANT = "deploymentVariant";
    private static final String TYPE_INSTALLED_SOFTWARE = "installedSoftware";
    private static final String TYPE_PHONE_INTEGRATION = "phoneIntegration";
    private static final String TYPE_PROJECT = "project";
    private static final String TYPE_RADIO = "radio";
    private static final String TYPE_SERVER = "server";
    private static final String TYPE_SERVICE_CONTRACT = "serviceContract";
    private static final String TYPE_SITE = "site";
    private static final String TYPE_SOFTWARE = "software";
    private static final String TYPE_UPGRADE_PLAN = "upgradePlan";

    private final AccountRepository accountRepository;
    private final AddressRepository addressRepository;
    private final AudioDeviceRepository audioDeviceRepository;
    private final CityRepository cityRepository;
    private final ClientsRepository clientsRepository;
    private final CountryRepository countryRepository;
    private final DeploymentVariantRepository deploymentVariantRepository;
    private final InstalledSoftwareRepository installedSoftwareRepository;
    private final PhoneIntegrationRepository phoneIntegrationRepository;
    private final ProjectRepository projectRepository;
    private final RadioRepository radioRepository;
    private final ServerRepository serverRepository;
    private final ServiceContractRepository serviceContractRepository;
    private final SiteRepository siteRepository;
    private final SoftwareRepository softwareRepository;
    private final UpgradePlanRepository upgradePlanRepository;

    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final ReentrantLock writerLock = new ReentrantLock();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile Path indexDir;
    private volatile Path storedLicenseJsonPath;

    /**
     * Test constructor—initializes every repository reference with {@code null}.
     * The index path is configured via {@link #setIndexPath(Path)}, which also resolves the license files.
     */
    public LuceneIndexServiceImpl() {
        this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Production constructor: all repository instances are injected.
     * The index path is derived from {@link LuceneIndexService#INDEX_PATH} (see the logging hint for failures in clearIndex()).
     */
    @Autowired
    public LuceneIndexServiceImpl(AccountRepository accountRepository,
                                  AddressRepository addressRepository,
                                  AudioDeviceRepository audioDeviceRepository,
                                  CityRepository cityRepository,
                                  ClientsRepository clientsRepository,
                                  CountryRepository countryRepository,
                                  DeploymentVariantRepository deploymentVariantRepository,
                                  InstalledSoftwareRepository installedSoftwareRepository,
                                  PhoneIntegrationRepository phoneIntegrationRepository,
                                  ProjectRepository projectRepository,
                                  RadioRepository radioRepository,
                                  ServerRepository serverRepository,
                                  ServiceContractRepository serviceContractRepository,
                                  SiteRepository siteRepository,
                                  SoftwareRepository softwareRepository,
                                  UpgradePlanRepository upgradePlanRepository) {
        this.accountRepository = accountRepository;
        this.addressRepository = addressRepository;
        this.audioDeviceRepository = audioDeviceRepository;
        this.cityRepository = cityRepository;
        this.clientsRepository = clientsRepository;
        this.countryRepository = countryRepository;
        this.deploymentVariantRepository = deploymentVariantRepository;
        this.installedSoftwareRepository = installedSoftwareRepository;
        this.phoneIntegrationRepository = phoneIntegrationRepository;
        this.projectRepository = projectRepository;
        this.radioRepository = radioRepository;
        this.serverRepository = serverRepository;
        this.serviceContractRepository = serviceContractRepository;
        this.siteRepository = siteRepository;
        this.softwareRepository = softwareRepository;
        this.upgradePlanRepository = upgradePlanRepository;
        setIndexPath(Paths.get(LuceneIndexService.INDEX_PATH));
    }

    /**
     * Allows alternative index paths (e.g., tests or temporary builds).
     * Important for parallel test runs so locks never collide.
     */
    public LuceneIndexServiceImpl(Path indexPath) {
        this();
        setIndexPath(indexPath);
    }

    /**
     * Opens an IndexWriter with serialized access (mirrors the "Could not close Lucene directory" warning emitted on
     * close failures).
     *
     * Scheduling & parallelism: used indirectly by all indexXxx() methods so the ReentrantLock serializes access to the index.
     * Camel delivers messages in parallel, but the lock enforces FIFO processing here.
     *
     * Retry strategy: on {@link LockObtainFailedException} the service invokes clearStaleLock() once and then retries opening.
     * Only afterwards is the exception propagated (see the log.warn/log.error entries in clearStaleLock()).
     */
    private void withWriter(WriterCallback callback) throws IOException {
        writerLock.lock();
        try {
            Files.createDirectories(indexDir);

            for (int attempt = 0; attempt < 2; attempt++) {
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

                FSDirectory dir = null;
                try {
                    dir = FSDirectory.open(indexDir);
                    try (IndexWriter writer = new IndexWriter(dir, config)) {
                        callback.execute(writer);
                        return;
                    }
                } catch (LockObtainFailedException e) {
                    if (attempt == 0 && clearStaleLock(dir)) {
                        continue;
                    }
                    throw e;
                } finally {
                    if (dir != null) {
                        try {
                            dir.close();
                        } catch (IOException closeEx) {
                            log.warn("Could not close Lucene directory", closeEx);
                        }
                    }
                }
            }
        } finally {
            writerLock.unlock();
        }
    }

    /**
     * Attempts to clean up orphaned write.lock files so a reindex does not stall.
     *
     * Side effects: removes the lock file from disk when possible and emits the warnings
     * {@code "Lucene lock on {} was released via obtainLock()."} and {@code "Removed orphaned Lucene write.lock ({})"}
     * before retrying. Invoked only by withWriter() after an initial open attempt failed.
     */
    private boolean clearStaleLock(FSDirectory dir) {
        boolean cleared = false;

        if (dir != null) {
            boolean lockAcquired = false;
            try (Lock luceneLock = dir.obtainLock(IndexWriter.WRITE_LOCK_NAME)) {
                log.warn("Lucene lock on {} was released via obtainLock().", indexDir.toAbsolutePath());
                lockAcquired = true;
            } catch (LockObtainFailedException e) {
                log.debug("Lucene lock on {} is still active and could not be acquired.", indexDir.toAbsolutePath());
            } catch (IOException e) {
                log.error("Could not release Lucene lock: {}", indexDir.toAbsolutePath(), e);
            }

            if (lockAcquired) {
                Path lockFile = indexDir.resolve("write.lock");
                try {
                    if (Files.deleteIfExists(lockFile)) {
                        log.warn("Removed orphaned Lucene write.lock ({}).", lockFile.toAbsolutePath());
                    }
                } catch (IOException e) {
                    log.error("Could not delete orphaned Lucene write.lock: {}", lockFile.toAbsolutePath(), e);
                }

                cleared = true;
            }
        }

        return cleared;
    }

    /**
     * Reads the persisted license-fragments.json so the index uses the same license fragments as REST.
     * Throws a runtime exception with logging context if I/O fails.
     */
    private JsonNode readStoredLicenseJson() {
        if (!Files.exists(storedLicenseJsonPath)) {
            return JsonNodeFactory.instance.objectNode();
        }

        try (InputStream in = Files.newInputStream(storedLicenseJsonPath)) {
            JsonNode parsed = objectMapper.readTree(in);
            return parsed != null ? parsed : JsonNodeFactory.instance.objectNode();
        } catch (IOException e) {
            throw new LicenseReadingException("Could not read stored license-fragments.json", e);
        }
    }

    /**
     * Persists license information atomically (via Files.newOutputStream). Side effect: creates directories as needed.
     */
    private void writeStoredLicenseJson(ObjectNode node) {
        try {
            Files.createDirectories(storedLicenseJsonPath.getParent());
            try (OutputStream out = Files.newOutputStream(storedLicenseJsonPath)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, node);
            }
        } catch (IOException e) {
            throw new LicenseReadingException("Could not store license-fragments.json", e);
        }
    }

    /**
     * Opens a reader context for ad-hoc searches. Returns {@code null} when no index exists yet.
     */
    private DirectoryReader openReader() throws IOException {
        if (!Files.isDirectory(indexDir)) {
            return null;
        }
        FSDirectory dir = FSDirectory.open(indexDir);
        if (!DirectoryReader.indexExists(dir)) {
            dir.close();
            return null;
        }
        return DirectoryReader.open(dir);
    }

    @Override
    /**
     * Returns the currently configured index path. Synchronized because timers/REST may reconfigure it.
     */
    public synchronized Path getIndexPath() {
        return Objects.requireNonNull(indexDir, "indexPath is not configured");
    }

    @Override
    /**
     * Configures a new index path and updates the location of the license fragments.
     * Side effect: subsequent indexXxx() calls write to the new directory.
     */
    public synchronized void setIndexPath(Path indexPath) {
        Path normalized = Objects.requireNonNull(indexPath, "indexPath must not be null");
        this.indexDir = normalized;
        this.storedLicenseJsonPath = this.indexDir.resolve("license-fragments.json");
    }

    // =================== Search ===================

    @Override
    /**
     * Parses a query (StandardAnalyzer) and delegates to {@link #search(Query)}. Errors are logged and answered with an empty
     * list so REST endpoints do not return exceptions (see the log.error entry).
     */
    public List<SearchHit> search(String queryText) {
        try {
            QueryParser parser = new QueryParser("content", analyzer);
            parser.setAllowLeadingWildcard(true);
            Query query = parser.parse(queryText);
            return search(query);
        } catch (Exception e) {
            log.error("Failed to parse search query: {}", queryText, e);
            return List.of();
        }
    }

    @Override
    /**
     * Executes a Lucene search limited to 50 hits. Ensures readers are closed (see the "Could not close Lucene reader"
     * logger message). Thread-safe because readers are opened per invocation.
     */
    public List<SearchHit> search(Query query) {
        List<SearchHit> results = new ArrayList<>();
        DirectoryReader reader = null;
        try {
            reader = openReader();
            if (reader == null) {
                return results;
            }
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(query, 50);

            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                results.add(mapToHit(doc));
            }
        } catch (Exception e) {
            log.error("Search execution failed", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException closeEx) {
                    log.warn("Could not close Lucene reader", closeEx);
                }
            }
        }
        return results;
    }

    // =================== Reindex ===================

    @Override
    /**
     * Aggregates all domain records from the repositories and indexes them sequentially.
     *
     * Scheduling & parallelism: typically invoked by Camel timers (UnifiedIndexingRoutes) or admin triggers. Progress is written
     * to {@link IndexProgress} so REST/monitoring endpoints can consume it. Side effects: clears the existing index (see
     * clearIndex()) and emits detailed log output.
     */
    public void reindexAll() {
        List<Account> accounts = accountRepository != null ? safeList(accountRepository.findAll()) : List.of();
        List<Address> addresses = addressRepository != null ? safeList(addressRepository.findAll()) : List.of();
        List<AudioDevice> audioDevices = audioDeviceRepository != null ? safeList(audioDeviceRepository.findAll()) : List.of();
        List<City> cities = cityRepository != null ? safeList(cityRepository.findAll()) : List.of();
        List<Clients> clients = clientsRepository != null ? safeList(clientsRepository.findAll()) : List.of();
        List<Country> countries = countryRepository != null ? safeList(countryRepository.findAll()) : List.of();
        List<DeploymentVariant> deploymentVariants = deploymentVariantRepository != null ? safeList(deploymentVariantRepository.findAll()) : List.of();
        List<InstalledSoftware> installedSoftware = installedSoftwareRepository != null ? safeList(installedSoftwareRepository.findAll()) : List.of();
        List<PhoneIntegration> phoneIntegrations = phoneIntegrationRepository != null ? safeList(phoneIntegrationRepository.findAll()) : List.of();
        List<Project> projects = projectRepository != null ? safeList(projectRepository.findAll()) : List.of();
        List<Radio> radios = radioRepository != null ? safeList(radioRepository.findAll()) : List.of();
        List<Server> servers = serverRepository != null ? safeList(serverRepository.findAll()) : List.of();
        List<ServiceContract> serviceContracts = serviceContractRepository != null ? safeList(serviceContractRepository.findAll()) : List.of();
        List<Site> sites = siteRepository != null ? safeList(siteRepository.findAll()) : List.of();
        List<Software> softwareList = softwareRepository != null ? safeList(softwareRepository.findAll()) : List.of();
        List<UpgradePlan> upgradePlans = upgradePlanRepository != null ? safeList(upgradePlanRepository.findAll()) : List.of();

        Map<String, Integer> totals = new LinkedHashMap<>();
        totals.put(progressKey(TYPE_ACCOUNT), accounts.size());
        totals.put(progressKey(TYPE_ADDRESS), addresses.size());
        totals.put(progressKey(TYPE_AUDIO_DEVICE), audioDevices.size());
        totals.put(progressKey(TYPE_CITY), cities.size());
        totals.put(progressKey(TYPE_CLIENT), clients.size());
        totals.put(progressKey(TYPE_COUNTRY), countries.size());
        totals.put(progressKey(TYPE_DEPLOYMENT_VARIANT), deploymentVariants.size());
        totals.put(progressKey(TYPE_INSTALLED_SOFTWARE), installedSoftware.size());
        totals.put(progressKey(TYPE_PHONE_INTEGRATION), phoneIntegrations.size());
        totals.put(progressKey(TYPE_PROJECT), projects.size());
        totals.put(progressKey(TYPE_RADIO), radios.size());
        totals.put(progressKey(TYPE_SERVER), servers.size());
        totals.put(progressKey(TYPE_SERVICE_CONTRACT), serviceContracts.size());
        totals.put(progressKey(TYPE_SITE), sites.size());
        totals.put(progressKey(TYPE_SOFTWARE), softwareList.size());
        totals.put(progressKey(TYPE_UPGRADE_PLAN), upgradePlans.size());

        try {
            clearIndex();
        } catch (IOException e) {
            log.error("Failed to delete Lucene index before reindexing", e);
            return;
        }

        IndexProgress progress = IndexProgress.get();
        boolean started = false;
        try {
            progress.start(totals);
            started = true;

            int totalRecords = 0;
            for (Integer value : totals.values()) {
                totalRecords += (value == null ? 0 : value);
            }
            log.debug("Starting full Lucene reindex with {} records.", totalRecords);

            for (Account account : accounts) {
                indexAccount(
                        toStringOrNull(account.getAccountID()),
                        account.getAccountName(),
                        account.getCountry(),
                        account.getContactEmail()
                );
            }
            for (Address address : addresses) {
                indexAddress(
                        toStringOrNull(address.getAddressID()),
                        address.getStreet(),
                        address.getCityID()
                );
            }
            for (AudioDevice audioDevice : audioDevices) {
                indexAudioDevice(
                        toStringOrNull(audioDevice.getAudioDeviceID()),
                        toStringOrNull(audioDevice.getClientID()),
                        audioDevice.getAudioDeviceBrand(),
                        audioDevice.getDeviceSerialNr(),
                        audioDevice.getAudioDeviceFirmware(),
                        audioDevice.getDeviceType(),
                        audioDevice.getDirection()
                );
            }
            for (City city : cities) {
                indexCity(city.getCityID(), city.getCityName(), city.getCountryCode());
            }
            for (Clients client : clients) {
                indexClient(
                        toStringOrNull(client.getClientID()),
                        toStringOrNull(client.getSiteID()),
                        client.getClientName(),
                        client.getClientBrand(),
                        client.getClientOS(),
                        client.getInstallType(),
                        client.getWorkingPositionType(),
                        client.getOtherInstalledSoftware()
                );
            }
            for (Country country : countries) {
                indexCountry(country.getCountryCode(), country.getCountryName());
            }
            for (DeploymentVariant variant : deploymentVariants) {
                indexDeploymentVariant(
                        toStringOrNull(variant.getVariantID()),
                        variant.getVariantCode(),
                        variant.getVariantName(),
                        variant.getDescription(),
                        variant.isActive()
                );
            }
            for (InstalledSoftware item : installedSoftware) {
                indexInstalledSoftware(
                        toStringOrNull(item.getInstalledSoftwareID()),
                        toStringOrNull(item.getSiteID()),
                        toStringOrNull(item.getSoftwareID()),
                        item.getStatus(),
                        item.getOfferedDate(),
                        item.getInstalledDate(),
                        item.getRejectedDate(),
                        item.getOutdatedDate()
                );
            }
            for (PhoneIntegration integration : phoneIntegrations) {
                indexPhoneIntegration(
                        toStringOrNull(integration.getPhoneIntegrationID()),
                        toStringOrNull(integration.getSiteID()),
                        integration.getPhoneType(),
                        integration.getPhoneBrand(),
                        integration.getInterfaceName(),
                        integration.getCapacity(),
                        integration.getPhoneFirmware()
                );
            }
            for (Project project : projects) {
                indexProject(
                        toStringOrNull(project.getProjectID()),
                        project.getProjectSAPID(),
                        project.getProjectName(),
                        toStringOrNull(project.getDeploymentVariantID()),
                        project.getBundleType(),
                        project.getLifecycleStatus() != null ? project.getLifecycleStatus().name() : null,
                        toStringOrNull(project.getAccountID()),
                        toStringOrNull(project.getAddressID()),
                        project.getSpecialNotes()
                );
            }
            for (Radio radio : radios) {
                indexRadio(
                        toStringOrNull(radio.getRadioID()),
                        toStringOrNull(radio.getSiteID()),
                        toStringOrNull(radio.getAssignedClientID()),
                        radio.getRadioBrand(),
                        radio.getRadioSerialNr(),
                        radio.getMode(),
                        radio.getDigitalStandard()
                );
            }
            for (Server server : servers) {
                indexServer(
                        toStringOrNull(server.getServerID()),
                        toStringOrNull(server.getSiteID()),
                        server.getServerName(),
                        server.getServerBrand(),
                        server.getServerSerialNr(),
                        server.getServerOS(),
                        server.getPatchLevel(),
                        server.getVirtualPlatform(),
                        server.getVirtualVersion()
                );
            }
            for (ServiceContract contract : serviceContracts) {
                indexServiceContract(
                        toStringOrNull(contract.getContractID()),
                        toStringOrNull(contract.getAccountID()),
                        toStringOrNull(contract.getProjectID()),
                        toStringOrNull(contract.getSiteID()),
                        contract.getContractNumber(),
                        contract.getStatus(),
                        toStringOrNull(contract.getStartDate()),
                        toStringOrNull(contract.getEndDate())
                );
            }
            for (Site site : sites) {
                indexSite(
                        toStringOrNull(site.getSiteID()),
                        site.getProjectID() != null ? List.of(site.getProjectID().toString()) : List.of(),
                        toStringOrNull(site.getAddressID()),
                        site.getSiteName(),
                        site.getFireZone(),
                        site.getTenantCount(),
                        site.getRedundantServers(),
                        site.isHighAvailability()
                );
            }
            for (Software software : softwareList) {
                indexSoftware(
                        toStringOrNull(software.getSoftwareID()),
                        software.getName(),
                        software.getRelease(),
                        software.getRevision(),
                        software.getSupportPhase(),
                        software.getLicenseModel(),
                        software.isThirdParty(),
                        software.getEndOfSalesDate(),
                        software.getSupportStartDate(),
                        software.getSupportEndDate()
                );
            }
            for (UpgradePlan plan : upgradePlans) {
                indexUpgradePlan(
                        toStringOrNull(plan.getUpgradePlanID()),
                        toStringOrNull(plan.getSiteID()),
                        toStringOrNull(plan.getSoftwareID()),
                        toStringOrNull(plan.getPlannedWindowStart()),
                        toStringOrNull(plan.getPlannedWindowEnd()),
                        plan.getStatus(),
                        toStringOrNull(plan.getCreatedAt()),
                        plan.getCreatedBy()
                );
            }

            log.debug("Lucene reindex finished. {} documents processed.", progress.totalDone());
        } catch (Exception e) {
            log.error("Reindexing failed", e);
        } finally {
            if (started) {
                progress.finish();
            }
        }
    }

    // =================== Helper ===================

    /**
     * Clears the entire Lucene index and commits the deletion immediately.
     * Called only from reindexAll() and mirrors the log entry "Lucene index cleared (ready for reindex)".
     */
    void clearIndex() throws IOException {
        withWriter(writer -> {
            writer.deleteAll();
            writer.commit();
        });
        log.debug("Lucene index cleared (ready for reindex) at {}", indexDir.toAbsolutePath());
    }

    /**
     * Executes the progress Key operation.
     * @param type type.
     * @return the computed result.
     */
    private String progressKey(String type) {
        if (type == null || type.isBlank()) {
            return "Unknown";
        }
        StringBuilder sb = new StringBuilder(type.length() + 4);
        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                sb.append(' ').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Executes the to String Or Null operation.
     * @param value value.
     * @return the computed result.
     */
    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Executes the safe List operation.
     * @param items items.
     * @return the computed result.
     */
    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    /**
     * Core indexing routine: writes or updates a document and advances the progress tracker.
     *
     * Side effects: commits the writer immediately (see writer.commit()), emits log messages, and updates {@link IndexProgress}.
     * Camel invokes the method serially (SEDA) and potentially in parallel via REST, but the internal lock in withWriter()
     * guarantees deterministic updates.
     */
    private void indexDocument(String id, String type, String... fields) {
        try {
            withWriter(writer -> {
                Document doc = new Document();
                String typeValue = safe(type);
                String typeKey = typeValue.toLowerCase(Locale.ROOT);
                String safeId = safe(id);

                doc.add(new StringField("id", safeId, Field.Store.YES));
                doc.add(new StringField("type", typeKey, Field.Store.YES));
                if (!typeValue.isEmpty()) {
                    doc.add(new StoredField("typeDisplay", typeValue));
                }

                StringBuilder content = new StringBuilder();
                if (!typeKey.isEmpty()) {
                    content.append(typeKey).append(' ');
                }
                for (String f : fields) {
                    content.append(safe(f)).append(' ');
                }
                String aggregated = content.toString().trim();
                doc.add(new TextField("content", aggregated, Field.Store.YES));
                doc.add(new StoredField("display", determineDisplay(type, id, fields)));

                writer.updateDocument(new Term("id", safeId), doc);
                writer.commit();
            });

            IndexProgress progress = IndexProgress.get();
            if (progress.isActive()) {
                progress.inc(progressKey(type));
                logProgress(progress);
            }
            if (log.isDebugEnabled()) {
                log.debug("Indexed {}: {}", type, id);
            }
        } catch (Exception e) {
            log.error("Failed to index {}", type, e);
        }
    }

    /**
     * Deletes the Document from the underlying store.
     * @param id identifier.
     */
    @Override
    public void deleteDocument(String id) {
        String safeId = safe(id);
        if (safeId.isEmpty()) {
            log.warn("Ignoring Lucene delete for empty id");
            return;
        }
        try {
            withWriter(writer -> {
                writer.deleteDocuments(new Term("id", safeId));
                writer.commit();
            });
            if (log.isDebugEnabled()) {
                log.debug("Deleted document from Lucene index: {}", safeId);
            }
        } catch (Exception e) {
            log.error("Failed to delete document {} from Lucene", safeId, e);
        }
    }

    /**
     * Executes the log Progress operation.
     * @param progress progress.
     */
    private void logProgress(IndexProgress progress) {
        int processed = progress.totalDone();
        int total = progress.grandTotal();
        if (processed == 0 || PROGRESS_LOG_INTERVAL == 0) {
            return;
        }
        if (processed % PROGRESS_LOG_INTERVAL == 0 || processed == total) {
            int percent = total == 0 ? 100 : Math.min(100, (processed * 100) / total);
            log.debug("Lucene reindex progress: {}/{} documents ({}%)", processed, total, percent);
        }
    }

    /**
     * Component that provides Writer Callback behavior.
     */
    @FunctionalInterface
    private interface WriterCallback {
        void execute(IndexWriter writer) throws IOException;
    }

    /**
     * Executes the safe operation.
     * @param s s.
     * @return the computed result.
     */
    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Normalizes the Token to a canonical form.
     * @param value value.
     * @return the computed result.
     */
    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\p{Alnum}]+", "").toLowerCase();
    }

    /**
     * Executes the token With Prefix operation.
     * @param prefix prefix.
     * @param value value.
     * @return the computed result.
     */
    private String tokenWithPrefix(String prefix, String value) {
        String normalized = normalizeToken(value);
        if (normalized.isEmpty()) {
            return "";
        }
        return prefix + normalized;
    }

    /**
     * Maps the supplied input into a To Hit representation.
     * @param doc doc.
     * @return the computed result.
     */
    private SearchHit mapToHit(Document doc) {
        String id = doc.get("id");
        String typeKey = doc.get("type");
        String typeDisplay = doc.get("typeDisplay");
        String display = doc.get("display");
        String content = doc.get("content");

        String type = firstNonBlank(typeDisplay, typeKey);
        String text = firstNonBlank(display, content, id, type);
        String snippet = buildSnippet(content, text, typeKey, typeDisplay);

        return new SearchHit(id, type, text, snippet);
    }

    /**
     * Executes the determine Display operation.
     * @param type type.
     * @param id identifier.
     * @param fields fields.
     * @return the computed result.
     */
    private String determineDisplay(String type, String id, String... fields) {
        String display = firstNonBlank(fields);
        if (display.isEmpty()) {
            display = (safe(type) + " " + safe(id)).trim();
        }
        if (display.isEmpty()) {
            display = safe(id);
        }
        return display;
    }

    /**
     * Executes the first Non Blank operation.
     * @param values values.
     * @return the computed result.
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    /**
     * Builds the Snippet from the supplied inputs.
     * @param content content.
     * @param text text.
     * @param tokensToStrip tokens to strip.
     * @return the built Snippet.
     */
    private String buildSnippet(String content, String text, String... tokensToStrip) {
        String normalized = normalizeWhitespace(content);
        if (normalized.isEmpty()) {
            return "";
        }

        normalized = stripLeadingToken(normalized, text);
        if (tokensToStrip != null) {
            for (String token : tokensToStrip) {
                normalized = stripLeadingToken(normalized, token);
            }
        }

        if (normalized.isEmpty()) {
            return "";
        }

        final int limit = 160;
        if (normalized.length() > limit) {
            normalized = normalized.substring(0, limit - 1).trim() + "…";
        }
        return normalized;
    }

    /**
     * Executes the strip Leading Token operation.
     * @param value value.
     * @param token token.
     * @return the computed result.
     */
    private String stripLeadingToken(String value, String token) {
        if (value.isEmpty() || token == null) {
            return value;
        }
        String normalizedToken = normalizeWhitespace(token);
        if (normalizedToken.isEmpty()) {
            return value;
        }
        if (startsWithIgnoreCase(value, normalizedToken)) {
            return value.substring(normalizedToken.length()).trim();
        }
        return value;
    }

    /**
     * Executes the starts With Ignore Case operation.
     * @param value value.
     * @param prefix prefix.
     * @return true when the condition is met; otherwise false.
     */
    private boolean startsWithIgnoreCase(String value, String prefix) {
        if (prefix == null || prefix.isEmpty() || value.length() < prefix.length()) {
            return false;
        }
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * Normalizes the Whitespace to a canonical form.
     * @param value value.
     * @return the computed result.
     */
    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    // =================== Indexing Methods ===================

    /**
     * Indexes the Account for search operations.
     * @param accountId account identifier.
     * @param accountName account name.
     * @param country country.
     * @param contactEmail contact email.
     */
    @Override
    public void indexAccount(String accountId, String accountName, String country, String contactEmail) {
        indexDocument(accountId, TYPE_ACCOUNT, accountName, country, contactEmail);
    }

    /**
     * Indexes the Address for search operations.
     * @param addressId address identifier.
     * @param street street.
     * @param cityId city identifier.
     */
    @Override
    public void indexAddress(String addressId, String street, String cityId) {
        indexDocument(addressId, TYPE_ADDRESS, street, cityId);
    }

    /**
     * Indexes the City for search operations.
     * @param cityId city identifier.
     * @param cityName city name.
     * @param countryCode country code.
     */
    @Override
    public void indexCity(String cityId, String cityName, String countryCode) {
        indexDocument(cityId, TYPE_CITY, cityName, countryCode);
    }

    /**
     * Indexes the Client for search operations.
     * @param clientId client identifier.
     * @param siteId site identifier.
     * @param clientName client name.
     * @param clientBrand client brand.
     * @param clientOS client os.
     * @param installType install type.
     * @param workingPositionType working position type.
     * @param otherInstalledSoftware other installed software.
     */
    @Override
    public void indexClient(String clientId, String siteId, String clientName, String clientBrand, String clientOS,
                           String installType, String workingPositionType, String otherInstalledSoftware) {
        indexDocument(clientId, TYPE_CLIENT, clientName, clientBrand, clientOS, installType, workingPositionType, otherInstalledSoftware, siteId);
    }

    /**
     * Indexes the Country for search operations.
     * @param countryCode country code.
     * @param countryName country name.
     */
    @Override
    public void indexCountry(String countryCode, String countryName) {
        indexDocument(countryCode, TYPE_COUNTRY, countryName);
    }

    /**
     * Indexes the Audio Device for search operations.
     * @param audioDeviceId audio device identifier.
     * @param clientId client identifier.
     * @param brand brand.
     * @param serialNr serial nr.
     * @param firmware firmware.
     * @param deviceType device type.
     * @param direction direction.
     */
    @Override
    public void indexAudioDevice(String audioDeviceId, String clientId, String brand, String serialNr, String firmware, String deviceType, String direction) {
        indexDocument(audioDeviceId, TYPE_AUDIO_DEVICE, brand, serialNr, firmware, deviceType, direction, clientId);
    }

    /**
     * Indexes the Deployment Variant for search operations.
     * @param variantId variant identifier.
     * @param variantCode variant code.
     * @param variantName variant name.
     * @param description description.
     * @param active active.
     */
    @Override
    public void indexDeploymentVariant(String variantId, String variantCode, String variantName, String description, boolean active) {
        indexDocument(variantId, TYPE_DEPLOYMENT_VARIANT, variantName, variantCode, description, String.valueOf(active));
    }

    /**
     * Indexes the Installed Software for search operations.
     * @param installedSoftwareId installed software identifier.
     * @param siteId site identifier.
     * @param softwareId software identifier.
     * @param status status.
     * @param offeredDate offered date.
     * @param installedDate installed date.
     * @param rejectedDate rejected date.
     * @param outdatedDate outdated date.
     */
    @Override
    public void indexInstalledSoftware(String installedSoftwareId, String siteId, String softwareId, String status,
                                       String offeredDate, String installedDate, String rejectedDate, String outdatedDate) {
        InstalledSoftwareStatus resolved;
        try {
            resolved = InstalledSoftwareStatus.from(status);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown installed software status '{}', defaulting to Offered", status);
            resolved = InstalledSoftwareStatus.OFFERED;
        }
        String statusValue = resolved.dbValue();
        String statusLabel = resolved.label();
        String statusToken = tokenWithPrefix("status", statusValue);
        String offeredSafe = safe(offeredDate);
        String installedSafe = safe(installedDate);
        String rejectedSafe = safe(rejectedDate);
        String outdatedSafe = safe(outdatedDate);
        String offeredToken = tokenWithPrefix("offered", offeredSafe);
        String installedToken = tokenWithPrefix("installed", installedSafe);
        String rejectedToken = tokenWithPrefix("rejected", rejectedSafe);
        String outdatedToken = tokenWithPrefix("outdated", outdatedSafe);
        indexDocument(installedSoftwareId, TYPE_INSTALLED_SOFTWARE,
                statusValue, statusLabel, statusToken,
                offeredSafe, installedSafe, rejectedSafe, outdatedSafe,
                offeredToken, installedToken, rejectedToken, outdatedToken,
                siteId, softwareId);
    }

    /**
     * Indexes the Phone Integration for search operations.
     * @param phoneIntegrationId phone integration identifier.
     * @param siteId site identifier.
     * @param phoneType phone type.
     * @param phoneBrand phone brand.
     * @param interfaceName interface name.
     * @param capacity capacity.
     * @param phoneFirmware phone firmware.
     */
    @Override
    public void indexPhoneIntegration(String phoneIntegrationId, String siteId, String phoneType, String phoneBrand, String interfaceName, Integer capacity, String phoneFirmware) {
        String capacityValue = capacity != null ? capacity.toString() : null;
        indexDocument(phoneIntegrationId, TYPE_PHONE_INTEGRATION, phoneType, phoneBrand, interfaceName, capacityValue, phoneFirmware, siteId);
    }

    /**
     * Indexes the Project for search operations.
     * @param projectId project identifier.
     * @param projectSAPId project sap identifier.
     * @param projectName project name.
     * @param deploymentVariantId deployment variant identifier.
     * @param bundleType bundle type.
     * @param lifecycleStatus lifecycle status.
     * @param accountId account identifier.
     * @param addressId address identifier.
     * @param specialNotes special notes.
     */
    @Override
    public void indexProject(String projectId, String projectSAPId, String projectName, String deploymentVariantId, String bundleType, String lifecycleStatus,
                             String accountId, String addressId, String specialNotes) {
        String status = lifecycleStatus == null ? "" : lifecycleStatus.trim();
        String statusLabel = status.replace('_', ' ').toLowerCase(Locale.ROOT);
        if (!statusLabel.isEmpty()) {
            statusLabel = statusLabel.substring(0, 1).toUpperCase(Locale.ROOT) + statusLabel.substring(1);
        }
        String statusToken = tokenWithPrefix("status", status);
        indexDocument(projectId, TYPE_PROJECT, projectName, bundleType,
                status, statusLabel, statusToken, projectSAPId, deploymentVariantId, accountId, addressId, specialNotes);
    }

    /**
     * Indexes the Radio for search operations.
     * @param radioId radio identifier.
     * @param siteId site identifier.
     * @param assignedClientId assigned client identifier.
     * @param radioBrand radio brand.
     * @param radioSerialNr radio serial nr.
     * @param mode mode.
     * @param digitalStandard digital standard.
     */
    @Override
    public void indexRadio(String radioId, String siteId, String assignedClientId, String radioBrand, String radioSerialNr, String mode, String digitalStandard) {
        indexDocument(radioId, TYPE_RADIO, radioBrand, radioSerialNr, mode, digitalStandard, siteId, assignedClientId);
    }

    /**
     * Indexes the Server for search operations.
     * @param serverId server identifier.
     * @param siteId site identifier.
     * @param serverName server name.
     * @param serverBrand server brand.
     * @param serverSerialNr server serial nr.
     * @param serverOS server os.
     * @param patchLevel patch level.
     * @param virtualPlatform virtual platform.
     * @param virtualVersion virtual version.
     */
    @Override
    public void indexServer(String serverId, String siteId, String serverName, String serverBrand, String serverSerialNr, String serverOS,
                            String patchLevel, String virtualPlatform, String virtualVersion) {
        indexDocument(serverId, TYPE_SERVER, serverName, serverBrand, serverSerialNr, serverOS, patchLevel, virtualPlatform, virtualVersion, siteId);
    }

    /**
     * Indexes the Service Contract for search operations.
     * @param contractId contract identifier.
     * @param accountId account identifier.
     * @param projectId project identifier.
     * @param siteId site identifier.
     * @param contractNumber contract number.
     * @param status status.
     * @param startDate start date.
     * @param endDate end date.
     */
    @Override
    public void indexServiceContract(String contractId, String accountId, String projectId, String siteId, String contractNumber, String status,
                                     String startDate, String endDate) {
        String statusToken = tokenWithPrefix("status", status);
        indexDocument(contractId, TYPE_SERVICE_CONTRACT, contractNumber, status, statusToken, startDate, endDate, accountId, projectId, siteId);
    }

    /**
     * Indexes the Site for search operations.
     * @param siteId site identifier.
     * @param projectIds project identifiers.
     * @param addressId address identifier.
     * @param siteName site name.
     * @param fireZone fire zone.
     * @param tenantCount tenant count.
     * @param redundantServers redundant servers.
     * @param highAvailability high availability.
     */
    @Override
    public void indexSite(String siteId, Collection<String> projectIds, String addressId, String siteName, String fireZone,
                          Integer tenantCount, Integer redundantServers, boolean highAvailability) {
        String tenants = tenantCount != null ? tenantCount.toString() : "";
        String redundant = redundantServers != null ? redundantServers.toString() : "";
        String zoneToken = tokenWithPrefix("zone", fireZone);
        String redundantToken = tokenWithPrefix("redundancy", redundant);
        String haValue = String.valueOf(highAvailability);
        String haToken = tokenWithPrefix("ha", haValue);
        List<String> projectTokens = projectIds == null ? List.of() : projectIds.stream().filter(Objects::nonNull).toList();
        List<String> fields = new ArrayList<>(List.of(siteName, fireZone, zoneToken, tenants, redundant, redundantToken, haValue, haToken, addressId));
        fields.addAll(projectTokens);
        indexDocument(siteId, TYPE_SITE, fields.toArray(String[]::new));
    }

    /**
     * Indexes the Software for search operations.
     * @param softwareId software identifier.
     * @param name name.
     * @param release release.
     * @param revision revision.
     * @param supportPhase support phase.
     * @param licenseModel license model.
     * @param thirdParty third party.
     * @param endOfSalesDate end of sales date.
     * @param supportStartDate support start date.
     * @param supportEndDate support end date.
     */
    @Override
    public void indexSoftware(String softwareId, String name, String release, String revision, String supportPhase,
                              String licenseModel, boolean thirdParty, String endOfSalesDate, String supportStartDate, String supportEndDate) {
        String vendorLabel = thirdParty ? "Third-party" : "LifeX";
        String vendorToken = tokenWithPrefix("thirdparty", thirdParty ? "true" : "false");
        indexDocument(softwareId, TYPE_SOFTWARE, name, release, revision, supportPhase, licenseModel, vendorLabel, vendorToken, endOfSalesDate, supportStartDate, supportEndDate);
    }

    /**
     * Indexes the Upgrade Plan for search operations.
     * @param upgradePlanId upgrade plan identifier.
     * @param siteId site identifier.
     * @param softwareId software identifier.
     * @param plannedWindowStart planned window start.
     * @param plannedWindowEnd planned window end.
     * @param status status.
     * @param createdAt created at.
     * @param createdBy created by.
     */
    @Override
    public void indexUpgradePlan(String upgradePlanId, String siteId, String softwareId, String plannedWindowStart, String plannedWindowEnd,
                                 String status, String createdAt, String createdBy) {
        indexDocument(upgradePlanId, TYPE_UPGRADE_PLAN, status, plannedWindowStart, plannedWindowEnd, createdAt, createdBy, siteId, softwareId);
    }

    private static class LicenseReadingException extends RuntimeException {
        /**
         * Executes the License Reading Exception operation.
         * @param message message.
         * @param cause cause.
         * @return the computed result.
         */
        private LicenseReadingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
