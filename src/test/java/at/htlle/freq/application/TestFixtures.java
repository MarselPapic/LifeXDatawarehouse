package at.htlle.freq.application;

import at.htlle.freq.domain.*;
import at.htlle.freq.domain.ProjectLifecycleStatus;

import java.time.LocalDate;
import java.util.UUID;

final class TestFixtures {

    static final UUID UUID1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID UUID2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID UUID3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final UUID UUID4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    static final UUID UUID5 = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private TestFixtures() {
    }

    static Address address() {
        return new Address(UUID1, "Main Street 1", "CITY-1");
    }

    static Country country() {
        return new Country("AT", "Austria");
    }

    static City city() {
        return new City("CITY-1", "Vienna", "AT");
    }

    static Clients client() {
        return new Clients(UUID1, UUID2, "Client", "Brand", "SN", "OS", "Patch", "LOCAL");
    }

    static DeploymentVariant deploymentVariant() {
        return new DeploymentVariant(UUID2, "CODE", "Variant", "Description", Boolean.TRUE);
    }

    static Project project() {
        return new Project(UUID3, "SAP-1", "Project", UUID2, "Bundle", "2024-01-01", ProjectLifecycleStatus.ACTIVE, UUID4, UUID5);
    }

    static Site site() {
        return new Site(UUID4, "Site", UUID3, UUID5, "Zone", 10);
    }

    static Software software() {
        return new Software(UUID5, "Software", "1.0", "rev1", "Production", "Subscription",
                false, "2024-12-31", "2024-01-01", "2025-12-31");
    }

    static UpgradePlan upgradePlan() {
        return new UpgradePlan(UUID3, UUID4, UUID5,
                LocalDate.parse("2024-03-01"),
                LocalDate.parse("2024-03-02"),
                "Planned",
                LocalDate.parse("2024-01-01"),
                "Alice");
    }

    static Server server() {
        return new Server(UUID2, UUID4, "Server", "Brand", "SERIAL", "Linux", "Patch",
                "Platform", "1.0", true);
    }

    static Radio radio() {
        return new Radio(UUID2, UUID4, UUID1, "Brand", "SERIAL", "MODE", "STANDARD");
    }

    static PhoneIntegration phoneIntegration() {
        return new PhoneIntegration(UUID2, UUID1, "TYPE", "Brand", "SERIAL", "FW");
    }

    static InstalledSoftware installedSoftware() {
        return new InstalledSoftware(UUID2, UUID4, UUID5, InstalledSoftwareStatus.OFFERED.dbValue(),
                "2024-01-10", null, null);
    }

    static ServiceContract serviceContract() {
        return new ServiceContract(UUID3, UUID4, UUID3, UUID4, "C-1", "Active",
                LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
    }
}
