package at.htlle.freq.domain;

import org.junit.jupiter.api.Test;

import at.htlle.freq.domain.ProjectLifecycleStatus;
import at.htlle.freq.domain.InstalledSoftwareStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainFactoryTest {

    private static final UUID UUID1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void factoriesCreateProperDomainObjects() {
        AccountFactory accountFactory = new AccountFactory();
        Account account = accountFactory.create("Acme", "Alice", "alice@test", "123", "ATU", "AT");
        assertEquals("Acme", account.getAccountName());
        assertEquals("Alice", account.getContactName());

        AddressFactory addressFactory = new AddressFactory();
        Address address = addressFactory.create("Main St", "Vienna");
        assertEquals("Main St", address.getStreet());
        assertEquals("Vienna", address.getCityID());

        AudioDeviceFactory audioDeviceFactory = new AudioDeviceFactory();
        AudioDevice audioDevice = audioDeviceFactory.create(UUID1, "Brand", "SN", "FW", "HEADSET");
        assertEquals(UUID1, audioDevice.getClientID());
        assertEquals("Brand", audioDevice.getAudioDeviceBrand());

        CityFactory cityFactory = new CityFactory();
        City city = cityFactory.create("1010", "Vienna", "AT");
        assertEquals("1010", city.getCityID());
        assertEquals("AT", city.getCountryCode());

        ClientsFactory clientsFactory = new ClientsFactory();
        Clients client = clientsFactory.create(UUID1, "Client", "Brand", "SN", "OS", "Patch", "LOCAL");
        assertEquals("Client", client.getClientName());
        assertEquals("LOCAL", client.getInstallType());

        CountryFactory countryFactory = new CountryFactory();
        Country country = countryFactory.create("AT", "Austria");
        assertEquals("AT", country.getCountryCode());

        DeploymentVariantFactory deploymentVariantFactory = new DeploymentVariantFactory();
        DeploymentVariant variant = deploymentVariantFactory.create("VAR", "Variant", "Desc", true);
        assertEquals("VAR", variant.getVariantCode());
        assertTrue(variant.isActive());

        InstalledSoftwareFactory installedSoftwareFactory = new InstalledSoftwareFactory();
        InstalledSoftware installedSoftware = installedSoftwareFactory.create(UUID1, UUID2);
        assertEquals(UUID1, installedSoftware.getSiteID());
        assertEquals(UUID2, installedSoftware.getSoftwareID());
        assertEquals(InstalledSoftwareStatus.ACTIVE.dbValue(), installedSoftware.getStatus());

        PhoneIntegrationFactory phoneIntegrationFactory = new PhoneIntegrationFactory();
        PhoneIntegration phoneIntegration = phoneIntegrationFactory.create(UUID1, "TYPE", "Brand", "SN", "FW");
        assertEquals(UUID1, phoneIntegration.getClientID());
        assertEquals("TYPE", phoneIntegration.getPhoneType());

        ProjectFactory projectFactory = new ProjectFactory();
        Project project = projectFactory.create("SAP", "Project", UUID1, "Bundle", "2024-01-01", ProjectLifecycleStatus.ACTIVE, UUID1, UUID2);
        assertEquals("SAP", project.getProjectSAPID());
        assertEquals(ProjectLifecycleStatus.ACTIVE, project.getLifecycleStatus());

        RadioFactory radioFactory = new RadioFactory();
        Radio radio = radioFactory.create(UUID1, UUID2, "Brand", "SN", "MODE", "STANDARD");
        assertEquals(UUID1, radio.getSiteID());
        assertEquals("MODE", radio.getMode());

        ServerFactory serverFactory = new ServerFactory();
        Server server = serverFactory.create(UUID1, "Server", "Brand", "SN", "OS", "Patch", "Platform", "Version", true);
        assertEquals("Server", server.getServerName());
        assertTrue(server.isHighAvailability());

        ServiceContractFactory serviceContractFactory = new ServiceContractFactory();
        ServiceContract serviceContract = serviceContractFactory.create(UUID1, UUID2, UUID1, "C-1", "Active", "2024-01-01", "2024-12-31");
        assertEquals("C-1", serviceContract.getContractNumber());
        assertEquals("Active", serviceContract.getStatus());

        SiteFactory siteFactory = new SiteFactory();
        Site site = siteFactory.create("Site", UUID1, UUID2, "Zone", 10);
        assertEquals("Site", site.getSiteName());
        assertEquals(10, site.getTenantCount());

        SoftwareFactory softwareFactory = new SoftwareFactory();
        Software software = softwareFactory.create("Name", "1.0", "1", "Phase", "License", "2024-01-01", "2024-01-02", "2024-12-31");
        assertEquals("Name", software.getName());
        assertEquals("License", software.getLicenseModel());

        UpgradePlanFactory upgradePlanFactory = new UpgradePlanFactory();
        UpgradePlan upgradePlan = upgradePlanFactory.create(UUID1, UUID2, "2024-01-01", "2024-01-02", "Planned", "Alice", "System");
        assertEquals("Planned", upgradePlan.getStatus());
        assertEquals("Alice", upgradePlan.getCreatedBy());
    }
}
