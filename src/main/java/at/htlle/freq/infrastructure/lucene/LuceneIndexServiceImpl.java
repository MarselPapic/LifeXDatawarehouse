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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LuceneIndexServiceImpl implements LuceneIndexService {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexServiceImpl.class);
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

    public LuceneIndexServiceImpl() {
        this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

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

    public LuceneIndexServiceImpl(Path indexPath) {
        this();
        setIndexPath(indexPath);
    }

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
                            log.warn("Konnte Lucene-Verzeichnis nicht schließen", closeEx);
                        }
                    }
                }
            }
        } finally {
            writerLock.unlock();
        }
    }

    private boolean clearStaleLock(FSDirectory dir) {
        boolean cleared = false;

        if (dir != null) {
            boolean lockAcquired = false;
            try (Lock luceneLock = dir.obtainLock(IndexWriter.WRITE_LOCK_NAME)) {
                log.warn("Lucene-Lock auf {} wurde über obtainLock() freigegeben.", indexDir.toAbsolutePath());
                lockAcquired = true;
            } catch (LockObtainFailedException e) {
                log.debug("Lucene-Lock auf {} ist weiterhin aktiv und konnte nicht übernommen werden.", indexDir.toAbsolutePath());
            } catch (IOException e) {
                log.error("Konnte Lucene-Lock nicht freigeben: {}", indexDir.toAbsolutePath(), e);
            }

            if (lockAcquired) {
                Path lockFile = indexDir.resolve("write.lock");
                try {
                    if (Files.deleteIfExists(lockFile)) {
                        log.warn("Verwaiste Lucene write.lock entfernt ({}).", lockFile.toAbsolutePath());
                    }
                } catch (IOException e) {
                    log.error("Konnte verwaiste Lucene write.lock nicht löschen: {}", lockFile.toAbsolutePath(), e);
                }

                cleared = true;
            }
        }

        return cleared;
    }

    private JsonNode readStoredLicenseJson() {
        if (!Files.exists(storedLicenseJsonPath)) {
            return JsonNodeFactory.instance.objectNode();
        }

        try (InputStream in = Files.newInputStream(storedLicenseJsonPath)) {
            JsonNode parsed = objectMapper.readTree(in);
            return parsed != null ? parsed : JsonNodeFactory.instance.objectNode();
        } catch (IOException e) {
            throw new LicenseReadingException("Konnte gespeichertes license-fragments.json nicht lesen", e);
        }
    }

    private void writeStoredLicenseJson(ObjectNode node) {
        try {
            Files.createDirectories(storedLicenseJsonPath.getParent());
            try (OutputStream out = Files.newOutputStream(storedLicenseJsonPath)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, node);
            }
        } catch (IOException e) {
            throw new LicenseReadingException("Konnte license-fragments.json nicht speichern", e);
        }
    }

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
    public synchronized Path getIndexPath() {
        return Objects.requireNonNull(indexDir, "indexPath is not configured");
    }

    @Override
    public synchronized void setIndexPath(Path indexPath) {
        Path normalized = Objects.requireNonNull(indexPath, "indexPath must not be null");
        this.indexDir = normalized;
        this.storedLicenseJsonPath = this.indexDir.resolve("license-fragments.json");
    }

    // =================== Search ===================

    @Override
    public List<SearchHit> search(String queryText) {
        try {
            QueryParser parser = new QueryParser("content", analyzer);
            Query query = parser.parse(queryText);
            return search(query);
        } catch (Exception e) {
            log.error("Fehler beim Parsen der Suchanfrage: {}", queryText, e);
            return List.of();
        }
    }

    @Override
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
            log.error("Fehler bei der Suche", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException closeEx) {
                    log.warn("Konnte Lucene-Reader nicht schließen", closeEx);
                }
            }
        }
        return results;
    }

    // =================== Reindex ===================

    @Override
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
            log.error("Fehler beim Löschen des Lucene-Index vor dem Reindex", e);
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
            log.info("Starte vollständiges Lucene-Reindexing mit {} Datensätzen.", totalRecords);

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
                        audioDevice.getDeviceType()
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
                        client.getInstallType()
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
                        toStringOrNull(item.getSoftwareID())
                );
            }
            for (PhoneIntegration integration : phoneIntegrations) {
                indexPhoneIntegration(
                        toStringOrNull(integration.getPhoneIntegrationID()),
                        toStringOrNull(integration.getClientID()),
                        integration.getPhoneType(),
                        integration.getPhoneBrand(),
                        integration.getPhoneSerialNr(),
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
                        project.isStillActive(),
                        toStringOrNull(project.getAccountID()),
                        toStringOrNull(project.getAddressID())
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
                        server.getVirtualVersion(),
                        server.isHighAvailability()
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
                        contract.getStartDate(),
                        contract.getEndDate()
                );
            }
            for (Site site : sites) {
                indexSite(
                        toStringOrNull(site.getSiteID()),
                        toStringOrNull(site.getProjectID()),
                        toStringOrNull(site.getAddressID()),
                        site.getSiteName(),
                        site.getFireZone(),
                        site.getTenantCount()
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
                        plan.getPlannedWindowStart(),
                        plan.getPlannedWindowEnd(),
                        plan.getStatus(),
                        plan.getCreatedAt(),
                        plan.getCreatedBy()
                );
            }

            log.info("Lucene-Reindex abgeschlossen. {} Dokumente verarbeitet.", progress.totalDone());
        } catch (Exception e) {
            log.error("Fehler beim Reindexieren", e);
        } finally {
            if (started) {
                progress.finish();
            }
        }
    }

    // =================== Helper ===================

    private void clearIndex() throws IOException {
        withWriter(writer -> {
            writer.deleteAll();
            writer.commit();
        });
        log.info("Lucene-Index geleert (bereit für Reindex) am {}", indexDir.toAbsolutePath());
    }

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

    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

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
            }
            log.info("{} indexiert: {}", type, id);
        } catch (Exception e) {
            log.error("Fehler beim Indexieren von {}", type, e);
        }
    }

    @FunctionalInterface
    private interface WriterCallback {
        void execute(IndexWriter writer) throws IOException;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\p{Alnum}]+", "").toLowerCase();
    }

    private String tokenWithPrefix(String prefix, String value) {
        String normalized = normalizeToken(value);
        if (normalized.isEmpty()) {
            return "";
        }
        return prefix + normalized;
    }

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

    private boolean startsWithIgnoreCase(String value, String prefix) {
        if (prefix == null || prefix.isEmpty() || value.length() < prefix.length()) {
            return false;
        }
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    // =================== Indexing Methods ===================

    @Override
    public void indexAccount(String accountId, String accountName, String country, String contactEmail) {
        indexDocument(accountId, TYPE_ACCOUNT, accountName, country, contactEmail);
    }

    @Override
    public void indexAddress(String addressId, String street, String cityId) {
        indexDocument(addressId, TYPE_ADDRESS, street, cityId);
    }

    @Override
    public void indexCity(String cityId, String cityName, String countryCode) {
        indexDocument(cityId, TYPE_CITY, cityName, countryCode);
    }

    @Override
    public void indexClient(String clientId, String siteId, String clientName, String clientBrand, String clientOS, String installType) {
        indexDocument(clientId, TYPE_CLIENT, siteId, clientName, clientBrand, clientOS, installType);
    }

    @Override
    public void indexCountry(String countryCode, String countryName) {
        indexDocument(countryCode, TYPE_COUNTRY, countryName);
    }

    @Override
    public void indexAudioDevice(String audioDeviceId, String clientId, String brand, String serialNr, String firmware, String deviceType) {
        indexDocument(audioDeviceId, TYPE_AUDIO_DEVICE, clientId, brand, serialNr, firmware, deviceType);
    }

    @Override
    public void indexDeploymentVariant(String variantId, String variantCode, String variantName, String description, boolean active) {
        indexDocument(variantId, TYPE_DEPLOYMENT_VARIANT, variantCode, variantName, description, String.valueOf(active));
    }

    @Override
    public void indexInstalledSoftware(String installedSoftwareId, String siteId, String softwareId) {
        indexDocument(installedSoftwareId, TYPE_INSTALLED_SOFTWARE, siteId, softwareId);
    }

    @Override
    public void indexPhoneIntegration(String phoneIntegrationId, String clientId, String phoneType, String phoneBrand, String phoneSerialNr, String phoneFirmware) {
        indexDocument(phoneIntegrationId, TYPE_PHONE_INTEGRATION, clientId, phoneType, phoneBrand, phoneSerialNr, phoneFirmware);
    }

    @Override
    public void indexProject(String projectId, String projectSAPId, String projectName, String deploymentVariantId, String bundleType, boolean stillActive,
                             String accountId, String addressId) {
        String activeWord  = stillActive ? "active" : "inactive";
        String activeToken = stillActive ? "statusactive" : "statusinactive";
        indexDocument(projectId, TYPE_PROJECT, projectSAPId, projectName, deploymentVariantId, bundleType,
                String.valueOf(stillActive), activeWord, activeToken, accountId, addressId);
    }

    @Override
    public void indexRadio(String radioId, String siteId, String assignedClientId, String radioBrand, String radioSerialNr, String mode, String digitalStandard) {
        indexDocument(radioId, TYPE_RADIO, siteId, assignedClientId, radioBrand, radioSerialNr, mode, digitalStandard);
    }

    @Override
    public void indexServer(String serverId, String siteId, String serverName, String serverBrand, String serverSerialNr, String serverOS,
                            String patchLevel, String virtualPlatform, String virtualVersion, boolean highAvailability) {
        indexDocument(serverId, TYPE_SERVER, siteId, serverName, serverBrand, serverSerialNr, serverOS, patchLevel, virtualPlatform, virtualVersion, String.valueOf(highAvailability));
    }

    @Override
    public void indexServiceContract(String contractId, String accountId, String projectId, String siteId, String contractNumber, String status,
                                     String startDate, String endDate) {
        String statusToken = tokenWithPrefix("status", status);
        indexDocument(contractId, TYPE_SERVICE_CONTRACT, accountId, projectId, siteId, contractNumber, status, statusToken, startDate, endDate);
    }

    @Override
    public void indexSite(String siteId, String projectId, String addressId, String siteName, String fireZone, Integer tenantCount) {
        String tenants = tenantCount != null ? tenantCount.toString() : "";
        String zoneToken = tokenWithPrefix("zone", fireZone);
        indexDocument(siteId, TYPE_SITE, projectId, addressId, siteName, fireZone, zoneToken, tenants);
    }

    @Override
    public void indexSoftware(String softwareId, String name, String release, String revision, String supportPhase,
                              String licenseModel, String endOfSalesDate, String supportStartDate, String supportEndDate) {
        indexDocument(softwareId, TYPE_SOFTWARE, name, release, revision, supportPhase, licenseModel, endOfSalesDate, supportStartDate, supportEndDate);
    }

    @Override
    public void indexUpgradePlan(String upgradePlanId, String siteId, String softwareId, String plannedWindowStart, String plannedWindowEnd,
                                 String status, String createdAt, String createdBy) {
        indexDocument(upgradePlanId, TYPE_UPGRADE_PLAN, siteId, softwareId, plannedWindowStart, plannedWindowEnd, status, createdAt, createdBy);
    }

    private static class LicenseReadingException extends RuntimeException {
        private LicenseReadingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
