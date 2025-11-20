package at.htlle.freq.infrastructure.camel;

import at.htlle.freq.domain.AccountRepository;
import at.htlle.freq.domain.AddressRepository;
import at.htlle.freq.domain.AudioDeviceRepository;
import at.htlle.freq.domain.CityRepository;
import at.htlle.freq.domain.ClientsRepository;
import at.htlle.freq.domain.CountryRepository;
import at.htlle.freq.domain.DeploymentVariantRepository;
import at.htlle.freq.domain.InstalledSoftwareRepository;
import at.htlle.freq.domain.PhoneIntegrationRepository;
import at.htlle.freq.domain.ProjectRepository;
import at.htlle.freq.domain.RadioRepository;
import at.htlle.freq.domain.ServerRepository;
import at.htlle.freq.domain.ServiceContractRepository;
import at.htlle.freq.domain.SiteRepository;
import at.htlle.freq.domain.SoftwareRepository;
import at.htlle.freq.domain.UpgradePlanRepository;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UnifiedIndexingRoutesTest extends CamelTestSupport {

    private final List<String> timerRouteIds = List.of(
            "ReindexAccounts",
            "ReindexAddresses",
            "ReindexAudioDevices",
            "ReindexCities",
            "ReindexClients",
            "ReindexCountries",
            "ReindexDeploymentVariants",
            "ReindexInstalledSoftware",
            "ReindexPhoneIntegrations",
            "ReindexProjects",
            "ReindexRadios",
            "ReindexServers",
            "ReindexServiceContracts",
            "ReindexSites",
            "ReindexSoftware",
            "ReindexUpgradePlans"
    );

    private final List<String> directRouteIds = List.of(
            "IndexSingleAccount",
            "IndexSingleAddress",
            "IndexSingleAudioDevice",
            "IndexSingleCity",
            "IndexSingleClient",
            "IndexSingleCountry",
            "IndexSingleDeploymentVariant",
            "IndexSingleInstalledSoftware",
            "IndexSinglePhoneIntegration",
            "IndexSingleProject",
            "IndexSingleRadio",
            "IndexSingleServer",
            "IndexSingleServiceContract",
            "IndexSingleSite",
            "IndexSingleSoftware",
            "IndexSingleUpgradePlan"
    );

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @BeforeEach
    void adviseTimerRoutes() throws Exception {
        for (String routeId : timerRouteIds) {
            adviceWith(context, routeId, advice -> advice.replaceFromWith("direct:" + routeId + "-trigger"));
        }
        context.start();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new UnifiedIndexingRoutes(
                mock(AccountRepository.class),
                mock(AddressRepository.class),
                mock(AudioDeviceRepository.class),
                mock(CityRepository.class),
                mock(ClientsRepository.class),
                mock(CountryRepository.class),
                mock(DeploymentVariantRepository.class),
                mock(InstalledSoftwareRepository.class),
                mock(PhoneIntegrationRepository.class),
                mock(ProjectRepository.class),
                mock(RadioRepository.class),
                mock(ServerRepository.class),
                mock(ServiceContractRepository.class),
                mock(SiteRepository.class),
                mock(SoftwareRepository.class),
                mock(UpgradePlanRepository.class)
        );
    }

    @Test
    void timerRoutesUseLuceneIndexQueueWithConfiguredOptions() {
        List<String> timerTargets = timerRouteIds.stream()
                .map(this::getRouteDefinition)
                .flatMap(this::collectToUris)
                .collect(Collectors.toList());

        assertThat(timerTargets)
                .containsOnly("seda:lucene-index?size=2000&blockWhenFull=true");

        SedaEndpoint endpoint = context.getEndpoint("seda:lucene-index?size=2000&blockWhenFull=true", SedaEndpoint.class);
        assertThat(endpoint).isNotNull();
        assertThat(endpoint.isBlockWhenFull()).isTrue();
        assertThat(endpoint.getSize()).isEqualTo(2000);
    }

    @Test
    void directRoutesForwardUnchangedBodiesToSharedQueue() {
        CamelContext camelContext = context;
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        String payload = "body-from-direct";

        for (String routeId : directRouteIds) {
            String directUri = getRouteDefinition(routeId).getInput().getEndpointUri();
            producerTemplate.sendBody(directUri, payload);
            Object received = consumer.receiveBody("seda:lucene-index", 1000);
            assertThat(received).as("Payload for route %s", routeId).isEqualTo(payload);
        }
    }

    @Test
    void routesAreLoadedWhenCamelFlagIsMissing() {
        List<Route> activeRoutes = context.getRoutes();
        assertThat(activeRoutes).hasSize(timerRouteIds.size() + directRouteIds.size());

        List<String> routeIds = context.getRouteDefinitions().stream().map(RouteDefinition::getId).collect(Collectors.toList());
        assertThat(routeIds).containsAll(timerRouteIds).containsAll(directRouteIds);
    }

    private List<String> collectUris(ProcessorDefinition<?> definition) {
        List<String> targets = new ArrayList<>();
        for (ProcessorDefinition<?> output : definition.getOutputs()) {
            if (output instanceof SendDefinition<?> sendDefinition) {
                targets.add(sendDefinition.getUri());
            }
            targets.addAll(collectUris(output));
        }
        return targets;
    }

    private java.util.stream.Stream<String> collectToUris(RouteDefinition routeDefinition) {
        return collectUris(routeDefinition).stream();
    }

    private RouteDefinition getRouteDefinition(String routeId) {
        return context.getRouteDefinitions().stream()
                .filter(rd -> routeId.equals(rd.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Route definition not found for id: " + routeId));
    }
}
