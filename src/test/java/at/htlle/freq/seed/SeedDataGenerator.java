package at.htlle.freq.seed;

import at.htlle.freq.domain.InstalledSoftwareStatus;
import at.htlle.freq.domain.ProjectLifecycleStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Helper tool to generate a deterministic demo data set with ~500 rows that still respects
 * foreign-key relationships defined in schema.sql.
 */
public final class SeedDataGenerator {

    private static final Path OUTPUT = Path.of("src/main/resources/data.sql");
    private static final Path BACKUP = Path.of("src/main/resources/data.sql.legacy");

    private static final Random RANDOM = new Random(42);

    private enum EntityType {
        COUNTRY,
        CITY,
        ADDRESS,
        ACCOUNT,
        DEPLOYMENT_VARIANT,
        SOFTWARE,
        PROJECT,
        SITE,
        SERVER,
        CLIENT,
        RADIO,
        AUDIO_DEVICE,
        PHONE_INTEGRATION,
        INSTALLED_SOFTWARE,
        UPGRADE_PLAN,
        SERVICE_CONTRACT
    }

    private static final Map<EntityType, AtomicInteger> COUNTERS = new EnumMap<>(EntityType.class);

    static {
        for (EntityType type : EntityType.values()) {
            COUNTERS.put(type, new AtomicInteger());
        }
    }

    private SeedDataGenerator() {
    }

    public static void main(String[] args) throws IOException {
        new SeedDataGenerator().run();
    }

    private void run() throws IOException {
        List<Country> countries = generateCountries();
        List<City> cities = generateCities(countries);
        List<Address> addresses = generateAddresses(cities);
        List<Account> accounts = generateAccounts(countries);
        List<DeploymentVariant> variants = generateDeploymentVariants();
        List<Software> software = generateSoftware();
        List<Project> projects = generateProjects(accounts, variants, addresses);
        List<Site> sites = generateSites(projects, addresses);
        List<Server> servers = generateServers(sites);
        List<Client> clients = generateClients(sites);
        List<Radio> radios = generateRadios(sites, clients);
        List<AudioDevice> audioDevices = generateAudioDevices(clients);
        List<PhoneIntegration> phones = generatePhoneIntegrations(clients);
        List<InstalledSoftware> installedSoftware = generateInstalledSoftware(sites, software);
        List<UpgradePlan> upgradePlans = generateUpgradePlans(installedSoftware);
        List<ServiceContract> contracts = generateServiceContracts(projects, sites, accounts);

        String sql = buildSql(
                countries,
                cities,
                addresses,
                accounts,
                variants,
                software,
                projects,
                sites,
                servers,
                clients,
                radios,
                audioDevices,
                phones,
                installedSoftware,
                upgradePlans,
                contracts
        );

        backupExistingSeed();
        Files.writeString(OUTPUT, sql);
    }

    private void backupExistingSeed() throws IOException {
        if (Files.exists(OUTPUT) && !Files.exists(BACKUP)) {
            Files.copy(OUTPUT, BACKUP);
        }
    }

    private List<Country> generateCountries() {
        return List.of(
                new Country("AT", "Austria"),
                new Country("DE", "Germany"),
                new Country("CH", "Switzerland"),
                new Country("SE", "Sweden"),
                new Country("NO", "Norway"),
                new Country("FI", "Finland"),
                new Country("ES", "Spain"),
                new Country("FR", "France"),
                new Country("US", "United States"),
                new Country("CA", "Canada")
        );
    }

    private List<City> generateCities(List<Country> countries) {
        Map<String, List<String>> namesByCountry = Map.of(
                "AT", List.of("Vienna", "Graz", "Linz"),
                "DE", List.of("Berlin", "Munich", "Hamburg"),
                "CH", List.of("Zurich", "Bern", "Basel"),
                "SE", List.of("Stockholm", "Gothenburg", "Malmö"),
                "NO", List.of("Oslo", "Bergen", "Trondheim"),
                "FI", List.of("Helsinki", "Tampere", "Oulu"),
                "ES", List.of("Madrid", "Barcelona", "Valencia"),
                "FR", List.of("Paris", "Lyon", "Bordeaux"),
                "US", List.of("Seattle", "Austin", "Denver"),
                "CA", List.of("Toronto", "Vancouver", "Calgary")
        );

        List<City> result = new ArrayList<>();
        for (Country country : countries) {
            List<String> cityNames = namesByCountry.get(country.code());
            for (String name : cityNames) {
                String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replace("'", "")
                        .replace(' ', '-');
                String cityId = country.code() + "-" + normalized.toUpperCase(Locale.ROOT);
                result.add(new City(cityId, name, country.code()));
            }
        }
        return result;
    }

    private List<Address> generateAddresses(List<City> cities) {
        List<String> streetPrefixes = List.of("Riverside", "Central", "Harbor", "Sunrise", "Meadow", "Summit", "Market", "Baker", "Liberty", "Oak");
        List<String> streetSuffixes = List.of("Road", "Avenue", "Street", "Lane", "Boulevard", "Drive");

        List<Address> addresses = new ArrayList<>();
        int streetIndex = 1;
        for (int i = 0; i < 60; i++) {
            City city = cities.get(i % cities.size());
            String street = streetPrefixes.get(i % streetPrefixes.size()) + " " + streetSuffixes.get(i % streetSuffixes.size()) + " " + streetIndex++;
            addresses.add(new Address(generateId(EntityType.ADDRESS), street, city.id()));
        }
        return addresses;
    }

    private List<Account> generateAccounts(List<Country> countries) {
        List<String> orgPrefixes = List.of("Guardian", "Resilience", "Aurora", "Sentinel", "Vertex", "Atlas", "Northern", "Harbor", "Summit", "Nova");
        List<String> orgSuffixes = List.of("Network", "Services", "Alliance", "Consortium", "Operations", "Group");
        List<String> contactFirst = List.of("Anna", "Markus", "Lena", "Sven", "Carla", "Diego", "Fatima", "Noah", "Sophia", "Elias");
        List<String> contactLast = List.of("Gruber", "Kallio", "Fernandez", "Dubois", "Larsson", "Nielsen", "Meier", "Campbell", "Olsen", "Jensen");

        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            if (i == 0) {
                Country country = countries.get(0);
                accounts.add(new Account(
                        generateId(EntityType.ACCOUNT),
                        "Acme Integration",
                        "Ingrid Novak",
                        "ingrid.novak@acme-integration.example",
                        "+43 720000",
                        country.code() + "19999999",
                        country.name()
                ));
                continue;
            }
            String prefix = orgPrefixes.get(i % orgPrefixes.size());
            String suffix = orgSuffixes.get((i / orgPrefixes.size()) % orgSuffixes.size());
            String accountName = prefix + " " + suffix + " " + String.format(Locale.ROOT, "%02d", i + 1);
            String firstName = contactFirst.get(i % contactFirst.size());
            String lastName = contactLast.get((i + 3) % contactLast.size());
            String contactName = firstName + " " + lastName;
            String emailDomain = (prefix + suffix).toLowerCase(Locale.ROOT).replace(" ", "");
            String contactEmail = firstName.toLowerCase(Locale.ROOT) + "." + lastName.toLowerCase(Locale.ROOT) + "@" + emailDomain + ".example";
            String contactPhone = "+" + (30 + i) + " " + (100000 + RANDOM.nextInt(800000));
            Country country = countries.get(i % countries.size());
            String vatNumber = country.code() + String.format(Locale.ROOT, "%08d", 10000000 + i);
            accounts.add(new Account(generateId(EntityType.ACCOUNT), accountName, contactName, contactEmail, contactPhone, vatNumber, country.name()));
        }
        return accounts;
    }

    private List<DeploymentVariant> generateDeploymentVariants() {
        List<String> codes = List.of("URB-HA", "URB-STD", "RUR-LT", "MTN-OPS", "CST-EDGE", "MET-X2", "RAP-RT", "AIR-MOB", "SEC-PLUS", "BSC-LITE");
        List<String> names = List.of(
                "Urban High Availability",
                "Urban Standard",
                "Rural Light",
                "Mountain Operations",
                "Coastal Edge",
                "Metropolitan X2",
                "Rapid Response",
                "Air Mobile",
                "Secure Plus",
                "Basic Lite"
        );
        List<String> descriptions = List.of(
                "Redundant core with disaster recovery",
                "Standard city deployment",
                "Compact package for small communities",
                "Hardened variant for alpine regions",
                "Optimised for coastline coverage",
                "Extended capacity for mega cities",
                "Fast deploy incident bundle",
                "Airborne operations set",
                "Security-focused blueprint",
                "Entry level configuration"
        );

        List<DeploymentVariant> variants = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            variants.add(new DeploymentVariant(generateId(EntityType.DEPLOYMENT_VARIANT), codes.get(i), names.get(i), descriptions.get(i), i != 2));
        }
        return variants;
    }

    private List<Software> generateSoftware() {
        List<Software> result = new ArrayList<>();
        List<String> productNames = List.of("LifeX Core", "Command Center", "Edge Gateway", "Analytics Suite");
        String[] phases = {"Production", "Preview", "EoL"};
        String[] licenseModels = {"Subscription", "Perpetual"};
        int releaseCounter = 0;
        for (int i = 0; i < 12; i++) {
            String product = productNames.get(i % productNames.size());
            int releaseMajor = 2024 + (i / productNames.size());
            int releaseMinor = (releaseCounter % 3) + 1;
            releaseCounter++;
            String release = releaseMajor + "." + releaseMinor;
            String revision = Integer.toString(5 + i);
            String phase = phases[i % phases.length];
            String license = licenseModels[i % licenseModels.length];
            int endOfSalesOffset = 180 + RANDOM.nextInt(540);
            int supportStartOffset = -240 + RANDOM.nextInt(120);
            int supportEndOffset = supportStartOffset + 365;
            boolean thirdParty = "Edge Gateway".equals(product) || "Analytics Suite".equals(product);
            result.add(new Software(
                    generateId(EntityType.SOFTWARE),
                    product,
                    release,
                    revision,
                    phase,
                    license,
                    thirdParty,
                    endOfSalesOffset,
                    supportStartOffset,
                    supportEndOffset
            ));
        }
        return result;
    }

    private List<Project> generateProjects(List<Account> accounts, List<DeploymentVariant> variants, List<Address> addresses) {
        List<String> projectThemes = List.of("Aurora", "Beacon", "Cascade", "Delta", "Ember", "Frontier", "Guardian", "Harbor", "Ion", "Jade");
        List<String> bundleTypes = List.of("Premium", "Standard", "Hybrid", "Edge");
        List<Project> projects = new ArrayList<>();
        List<ProjectLifecycleStatus> lifecycleStatuses = List.of(
                ProjectLifecycleStatus.ACTIVE,
                ProjectLifecycleStatus.MAINTENANCE,
                ProjectLifecycleStatus.PLANNED,
                ProjectLifecycleStatus.RETIRED
        );
        for (int i = 0; i < 38; i++) {
            String sapId = "PX-" + (2101 + i);
            String theme = projectThemes.get(i % projectThemes.size());
            String projectName = "Project " + theme + " " + String.format(Locale.ROOT, "%02d", (i % 20) + 1);
            DeploymentVariant variant = variants.get(i % variants.size());
            Account account = accounts.get(i % accounts.size());
            Address address = addresses.get(i % addresses.size());
            String bundleType = bundleTypes.get(i % bundleTypes.size());
            int createOffset = - (10 + i * 4);
            ProjectLifecycleStatus lifecycleStatus = lifecycleStatuses.get(i % lifecycleStatuses.size());
            projects.add(new Project(
                    generateId(EntityType.PROJECT),
                    sapId,
                    projectName,
                    variant.id(),
                    bundleType,
                    createOffset,
                    lifecycleStatus,
                    account.id(),
                    address.id()
            ));
        }
        return projects;
    }

    private List<Site> generateSites(List<Project> projects, List<Address> addresses) {
        List<String> zoneCodes = List.of("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot");
        List<Site> sites = new ArrayList<>();
        int siteCounter = 0;
        for (Project project : projects) {
            int siteCount = (siteCounter < 17) ? 2 : 1; // Assign two sites to the first 17 projects and one to the remainder
            for (int i = 0; i < siteCount; i++) {
                Address address = addresses.get((siteCounter + i) % addresses.size());
                String name = project.projectName().replace("Project", "")
                        .trim() + " Hub " + (i + 1);
                int tenants = 6 + RANDOM.nextInt(20);
                String fireZone = zoneCodes.get((siteCounter + i) % zoneCodes.size());
                sites.add(new Site(
                        generateId(EntityType.SITE),
                        name.trim(),
                        project.id(),
                        address.id(),
                        fireZone,
                        tenants
                ));
            }
            siteCounter++;
        }
        return sites.stream().limit(55).collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Server> generateServers(List<Site> sites) {
        List<String> brands = List.of("Lenovo", "Dell", "HPE", "Fujitsu");
        List<String> operatingSystems = List.of("Windows Server 2022", "Ubuntu 24.04 LTS", "Red Hat Enterprise 9", "SUSE Linux Enterprise 15");
        List<String> virtualPlatforms = List.of("vSphere", "HyperV", "BareMetal");
        List<Server> servers = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            Site site = sites.get(i % sites.size());
            String code = String.format(Locale.ROOT, "%03d", i + 1);
            String serverName = "SRV-" + (100 + i);
            String brand = brands.get(i % brands.size());
            String serial = brand.substring(0, 2).toUpperCase(Locale.ROOT) + "-" + code;
            String os = operatingSystems.get(i % operatingSystems.size());
            String patchLevel = "2025." + String.format(Locale.ROOT, "%02d", (i % 12) + 1);
            String platform = virtualPlatforms.get(i % virtualPlatforms.size());
            String platformVersion = platform.equals("BareMetal") ? null : (platform.equals("vSphere") ? "8.0" : "2022");
            boolean highAvailability = i % 2 == 0;
            servers.add(new Server(
                    generateId(EntityType.SERVER),
                    site.id(),
                    serverName,
                    brand,
                    serial,
                    os,
                    patchLevel,
                    platform,
                    platformVersion,
                    highAvailability
            ));
        }
        return servers;
    }

    private List<Client> generateClients(List<Site> sites) {
        List<String> brands = List.of("Dell", "Lenovo", "HP", "Panasonic", "Getac");
        List<String> osList = List.of("Windows 11", "Windows 10", "Ubuntu 24.04");
        List<Client> clients = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Site site = sites.get(i % sites.size());
            String name = "Operator Console " + String.format(Locale.ROOT, "%03d", i + 1);
            String brand = brands.get(i % brands.size());
            int prefixLength = Math.min(3, brand.length());
            String serialPrefix = brand.substring(0, prefixLength).toUpperCase(Locale.ROOT);
            String serial = serialPrefix + "-" + String.format(Locale.ROOT, "%04d", 200 + i);
            String os = osList.get(i % osList.size());
            String patchLevel = "2025." + String.format(Locale.ROOT, "%02d", (i % 12) + 1);
            String installType = (i % 3 == 0) ? "LOCAL" : "BROWSER";
            clients.add(new Client(
                    generateId(EntityType.CLIENT),
                    site.id(),
                    name,
                    brand,
                    serial,
                    os,
                    patchLevel,
                    installType
            ));
        }
        return clients;
    }

    private List<Radio> generateRadios(List<Site> sites, List<Client> clients) {
        List<String> brands = List.of("Motorola", "Airbus", "Kenwood", "Hytera");
        List<String> digitalStandards = new ArrayList<>(List.of("Motorola", "Airbus", "P25", "Tetra"));
        digitalStandards.add(null);
        List<Radio> radios = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            Site site = sites.get(i % sites.size());
            Client assignedClient = (i % 2 == 0 && i < clients.size()) ? clients.get(i) : null;
            String serial = "RD-" + String.format(Locale.ROOT, "%04d", 300 + i);
            String mode = (i % 3 == 0) ? "Analog" : "Digital";
            String standard = digitalStandards.get(i % digitalStandards.size());
            radios.add(new Radio(
                    generateId(EntityType.RADIO),
                    site.id(),
                    assignedClient == null ? null : assignedClient.id(),
                    brands.get(i % brands.size()),
                    serial,
                    mode,
                    standard
            ));
        }
        return radios;
    }

    private List<AudioDevice> generateAudioDevices(List<Client> clients) {
        List<String> brands = List.of("Jabra", "Bose", "Poly", "Sennheiser", "Logitech");
        List<String> firmwares = List.of("1.0.5", "2.1.3", "3.0.1", "4.2.0");
        List<String> types = List.of("HEADSET", "SPEAKER", "MIC");
        List<AudioDevice> devices = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            Client client = clients.get(i % clients.size());
            String brand = brands.get(i % brands.size());
            String serial = "AD-" + String.format(Locale.ROOT, "%04d", 400 + i);
            String firmware = firmwares.get(i % firmwares.size());
            String type = types.get(i % types.size());
            devices.add(new AudioDevice(
                    generateId(EntityType.AUDIO_DEVICE),
                    client.id(),
                    brand,
                    serial,
                    firmware,
                    type
            ));
        }
        return devices;
    }

    private List<PhoneIntegration> generatePhoneIntegrations(List<Client> clients) {
        List<String> brands = List.of("Avaya", "Cisco", "Unify", "Alcatel", "Mitel");
        List<String> types = List.of("Emergency", "NonEmergency", "Both");
        List<PhoneIntegration> phones = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            Client client = clients.get(i % clients.size());
            String serial = "PH-" + String.format(Locale.ROOT, "%04d", 500 + i);
            String firmware = "v" + (1 + i % 4) + "." + (i % 10);
            phones.add(new PhoneIntegration(
                    generateId(EntityType.PHONE_INTEGRATION),
                    client.id(),
                    types.get(i % types.size()),
                    brands.get(i % brands.size()),
                    serial,
                    firmware
            ));
        }
        return phones;
    }

    private List<InstalledSoftware> generateInstalledSoftware(List<Site> sites, List<Software> software) {
        List<InstalledSoftware> installs = new ArrayList<>();
        InstalledSoftwareStatus[] statuses = InstalledSoftwareStatus.values();
        for (int i = 0; i < 55; i++) {
            Site site = sites.get(i % sites.size());
            Software soft = software.get(i % software.size());
            InstalledSoftwareStatus status = statuses[i % statuses.length];
            installs.add(new InstalledSoftware(
                    generateId(EntityType.INSTALLED_SOFTWARE),
                    site.id(),
                    soft.id(),
                    status.dbValue()
            ));
        }
        return installs;
    }

    private List<UpgradePlan> generateUpgradePlans(List<InstalledSoftware> installs) {
        List<String> statuses = List.of("Planned", "Approved", "InProgress", "Done", "Canceled");
        List<UpgradePlan> plans = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            InstalledSoftware install = installs.get(i % installs.size());
            int startOffset = 14 + i * 2;
            int endOffset = startOffset + 1;
            int createdOffset = - (30 + i * 3);
            String createdBy = List.of("automation", "regional-admin", "west-team", "north-team").get(i % 4);
            plans.add(new UpgradePlan(
                    generateId(EntityType.UPGRADE_PLAN),
                    install.siteId(),
                    install.softwareId(),
                    startOffset,
                    endOffset,
                    statuses.get(i % statuses.size()),
                    createdOffset,
                    createdBy
            ));
        }
        return plans;
    }

    private List<ServiceContract> generateServiceContracts(List<Project> projects, List<Site> sites, List<Account> accounts) {
        List<ServiceContract> contracts = new ArrayList<>();
        int index = 0;
        for (Project project : projects) {
            if (contracts.size() >= 28) {
                break;
            }
            List<Site> projectSites = sites.stream()
                    .filter(site -> Objects.equals(site.projectId(), project.id()))
                    .collect(Collectors.toList());
            if (projectSites.isEmpty()) {
                continue;
            }
            Site site = projectSites.get(index % projectSites.size());
            Account account = accounts.stream()
                    .filter(acc -> Objects.equals(acc.id(), project.accountId()))
                    .findFirst()
                    .orElse(accounts.get(index % accounts.size()));
            String contractNumber = "SC-2025-" + String.format(Locale.ROOT, "%03d", index + 1);
            String status = List.of("Planned", "Approved", "InProgress", "Done").get(index % 4);
            int startOffset = - (90 + index * 3);
            int endOffset = startOffset + 365;
            contracts.add(new ServiceContract(
                    generateId(EntityType.SERVICE_CONTRACT),
                    account.id(),
                    project.id(),
                    site.id(),
                    contractNumber,
                    status,
                    startOffset,
                    endOffset
            ));
            index++;
        }
        return contracts;
    }

    private String buildSql(
            List<Country> countries,
            List<City> cities,
            List<Address> addresses,
            List<Account> accounts,
            List<DeploymentVariant> variants,
            List<Software> software,
            List<Project> projects,
            List<Site> sites,
            List<Server> servers,
            List<Client> clients,
            List<Radio> radios,
            List<AudioDevice> audioDevices,
            List<PhoneIntegration> phones,
            List<InstalledSoftware> installedSoftware,
            List<UpgradePlan> upgradePlans,
            List<ServiceContract> contracts
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Auto-generated seed data (" + LocalDate.now() + ")\n");
        sb.append("-- Use at.htlle.freq.seed.SeedDataGenerator to regenerate.\n\n");

        appendInsert(sb, "Country", List.of("CountryCode", "CountryName"), countries.stream()
                .map(c -> row(str(c.code()), str(c.name())))
                .collect(Collectors.toList()));

        appendInsert(sb, "City", List.of("CityID", "CityName", "CountryCode"), cities.stream()
                .map(city -> row(str(city.id()), str(city.name()), str(city.countryCode())))
                .collect(Collectors.toList()));

        appendInsert(sb, "Address", List.of("AddressID", "Street", "CityID"), addresses.stream()
                .map(address -> row(str(address.id()), str(address.street()), str(address.cityId())))
                .collect(Collectors.toList()));

        appendInsert(sb, "Account", List.of("AccountID", "AccountName", "ContactName", "ContactEmail", "ContactPhone", "VATNumber", "Country"), accounts.stream()
                .map(acc -> row(str(acc.id()), str(acc.name()), str(acc.contactName()), str(acc.contactEmail()), str(acc.contactPhone()), str(acc.vatNumber()), str(acc.country())))
                .collect(Collectors.toList()));

        appendInsert(sb, "DeploymentVariant", List.of("VariantID", "VariantCode", "VariantName", "Description", "IsActive"), variants.stream()
                .map(variant -> row(str(variant.id()), str(variant.code()), str(variant.name()), str(variant.description()), bool(variant.active())))
                .collect(Collectors.toList()));

        appendInsert(sb, "Software", List.of("SoftwareID", "Name", "Release", "Revision", "SupportPhase", "LicenseModel", "ThirdParty", "EndOfSalesDate", "SupportStartDate", "SupportEndDate"), software.stream()
                .map(soft -> row(
                        str(soft.id()),
                        str(soft.name()),
                        str(soft.release()),
                        str(soft.revision()),
                        str(soft.supportPhase()),
                        str(soft.licenseModel()),
                        bool(soft.thirdParty()),
                        dateOffset(soft.endOfSalesOffset()),
                        dateOffset(soft.supportStartOffset()),
                        dateOffset(soft.supportEndOffset())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "Project", List.of("ProjectID", "ProjectSAPID", "ProjectName", "DeploymentVariantID", "BundleType", "CreateDateTime", "LifecycleStatus", "AccountID", "AddressID"), projects.stream()
                .map(project -> row(
                        str(project.id()),
                        str(project.sapId()),
                        str(project.projectName()),
                        str(project.deploymentVariantId()),
                        str(project.bundleType()),
                        dateOffset(project.createOffset()),
                        str(project.status().name()),
                        str(project.accountId()),
                        str(project.addressId())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "Site", List.of("SiteID", "SiteName", "ProjectID", "AddressID", "FireZone", "TenantCount"), sites.stream()
                .map(site -> row(
                        str(site.id()),
                        str(site.name()),
                        str(site.projectId()),
                        str(site.addressId()),
                        str(site.fireZone()),
                        number(site.tenantCount())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "Server", List.of("ServerID", "SiteID", "ServerName", "ServerBrand", "ServerSerialNr", "ServerOS", "PatchLevel", "VirtualPlatform", "VirtualVersion", "HighAvailability"), servers.stream()
                .map(server -> row(
                        str(server.id()),
                        str(server.siteId()),
                        str(server.name()),
                        str(server.brand()),
                        str(server.serial()),
                        str(server.os()),
                        str(server.patchLevel()),
                        str(server.virtualPlatform()),
                        nullable(server.virtualVersion()),
                        bool(server.highAvailability())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "Clients", List.of("ClientID", "SiteID", "ClientName", "ClientBrand", "ClientSerialNr", "ClientOS", "PatchLevel", "InstallType"), clients.stream()
                .map(client -> row(
                        str(client.id()),
                        str(client.siteId()),
                        str(client.name()),
                        str(client.brand()),
                        str(client.serial()),
                        str(client.os()),
                        str(client.patchLevel()),
                        str(client.installType())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "Radio", List.of("RadioID", "SiteID", "AssignedClientID", "RadioBrand", "RadioSerialNr", "Mode", "DigitalStandard"), radios.stream()
                .map(radio -> row(
                        str(radio.id()),
                        str(radio.siteId()),
                        nullable(radio.assignedClientId()),
                        str(radio.brand()),
                        str(radio.serial()),
                        str(radio.mode()),
                        nullable(radio.standard())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "AudioDevice", List.of("AudioDeviceID", "ClientID", "AudioDeviceBrand", "DeviceSerialNr", "AudioDeviceFirmware", "DeviceType"), audioDevices.stream()
                .map(device -> row(
                        str(device.id()),
                        str(device.clientId()),
                        str(device.brand()),
                        str(device.serial()),
                        str(device.firmware()),
                        str(device.type())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "PhoneIntegration", List.of("PhoneIntegrationID", "ClientID", "PhoneType", "PhoneBrand", "PhoneSerialNr", "PhoneFirmware"), phones.stream()
                .map(phone -> row(
                        str(phone.id()),
                        str(phone.clientId()),
                        str(phone.type()),
                        str(phone.brand()),
                        str(phone.serial()),
                        str(phone.firmware())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "InstalledSoftware", List.of("InstalledSoftwareID", "SiteID", "SoftwareID", "Status"), installedSoftware.stream()
                .map(install -> row(
                        str(install.id()),
                        str(install.siteId()),
                        str(install.softwareId()),
                        str(install.status())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "UpgradePlan", List.of("UpgradePlanID", "SiteID", "SoftwareID", "PlannedWindowStart", "PlannedWindowEnd", "Status", "CreatedAt", "CreatedBy"), upgradePlans.stream()
                .map(plan -> row(
                        str(plan.id()),
                        str(plan.siteId()),
                        str(plan.softwareId()),
                        dateOffset(plan.windowStartOffset()),
                        dateOffset(plan.windowEndOffset()),
                        str(plan.status()),
                        dateOffset(plan.createdOffset()),
                        str(plan.createdBy())
                ))
                .collect(Collectors.toList()));

        appendInsert(sb, "ServiceContract", List.of("ContractID", "AccountID", "ProjectID", "SiteID", "ContractNumber", "Status", "StartDate", "EndDate"), contracts.stream()
                .map(contract -> row(
                        str(contract.id()),
                        str(contract.accountId()),
                        str(contract.projectId()),
                        str(contract.siteId()),
                        str(contract.contractNumber()),
                        str(contract.status()),
                        dateOffset(contract.startOffset()),
                        dateOffset(contract.endOffset())
                ))
                .collect(Collectors.toList()));

        return sb.toString();
    }

    private void appendInsert(StringBuilder sb, String table, List<String> columns, List<String> rows) {
        sb.append("-- ").append(table).append("\n");
        if (rows.isEmpty()) {
            sb.append("-- (keine Daten)\n\n");
            return;
        }
        sb.append("INSERT INTO ").append(table).append(" (")
                .append(String.join(", ", columns))
                .append(") VALUES\n");
        for (int i = 0; i < rows.size(); i++) {
            sb.append("    ").append(rows.get(i));
            if (i < rows.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(";\n\n");
    }

    private static String generateId(EntityType type) {
        UUID uuid = UUID.randomUUID();
        String[] parts = uuid.toString().split("-");
        int sequence = COUNTERS.get(type).incrementAndGet();
        String typeCode = String.format(Locale.ROOT, "%02X", type.ordinal() + 1);
        String sequencePart = String.format(Locale.ROOT, "%010d", sequence);
        parts[4] = typeCode + sequencePart;
        return String.join("-", parts);
    }

    private static String row(SqlValue... values) {
        return "(" + List.of(values).stream().map(SqlValue::render).collect(Collectors.joining(", ")) + ")";
    }

    private static SqlValue str(String value) {
        return new SqlValue(value, true);
    }

    private static SqlValue nullable(String value) {
        return value == null ? new SqlValue("NULL", false) : str(value);
    }

    private static SqlValue number(int value) {
        return new SqlValue(Integer.toString(value), false);
    }

    private static SqlValue bool(boolean value) {
        return new SqlValue(value ? "TRUE" : "FALSE", false);
    }

    private static SqlValue dateOffset(int daysOffset) {
        if (daysOffset == 0) {
            return new SqlValue("CURRENT_DATE", false);
        }
        return new SqlValue("DATEADD('DAY', " + daysOffset + ", CURRENT_DATE)", false);
    }

    private record SqlValue(String value, boolean quoted) {
        private String render() {
            if (!quoted) {
                return value;
            }
            return "'" + value.replace("'", "''") + "'";
        }
    }

    private record Country(String code, String name) {
    }

    private record City(String id, String name, String countryCode) {
    }

    private record Address(String id, String street, String cityId) {
    }

    private record Account(String id, String name, String contactName, String contactEmail, String contactPhone, String vatNumber, String country) {
    }

    private record DeploymentVariant(String id, String code, String name, String description, boolean active) {
    }

    private record Software(String id, String name, String release, String revision, String supportPhase, String licenseModel, boolean thirdParty, int endOfSalesOffset, int supportStartOffset, int supportEndOffset) {
    }

    private record Project(String id, String sapId, String projectName, String deploymentVariantId, String bundleType, int createOffset, ProjectLifecycleStatus status, String accountId, String addressId) {
    }

    private record Site(String id, String name, String projectId, String addressId, String fireZone, int tenantCount) {
    }

    private record Server(String id, String siteId, String name, String brand, String serial, String os, String patchLevel, String virtualPlatform, String virtualVersion, boolean highAvailability) {
    }

    private record Client(String id, String siteId, String name, String brand, String serial, String os, String patchLevel, String installType) {
    }

    private record Radio(String id, String siteId, String assignedClientId, String brand, String serial, String mode, String standard) {
    }

    private record AudioDevice(String id, String clientId, String brand, String serial, String firmware, String type) {
    }

    private record PhoneIntegration(String id, String clientId, String type, String brand, String serial, String firmware) {
    }

    private record InstalledSoftware(String id, String siteId, String softwareId, String status) {
    }

    private record UpgradePlan(String id, String siteId, String softwareId, int windowStartOffset, int windowEndOffset, String status, int createdOffset, String createdBy) {
    }

    private record ServiceContract(String id, String accountId, String projectId, String siteId, String contractNumber, String status, int startOffset, int endOffset) {
    }
}
