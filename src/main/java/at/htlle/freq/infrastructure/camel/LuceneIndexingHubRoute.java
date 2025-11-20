// src/main/java/at/htlle/freq/infrastructure/camel/LuceneIndexingHubRoute.java
package at.htlle.freq.infrastructure.camel;

import at.htlle.freq.domain.*;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/*
 * Camel routing hub for all Lucene write operations.
 *
 * Data flow:
 *  - UnifiedIndexingRoutes (timers, direct endpoints) push domain entities into the "seda:lucene-index" queue.
 *  - This route consumes the queue with a single consumer and invokes the matching indexXxx() method based on the payload.
 *  - Errors are logged inside onException with the template {@code "Lucene indexing failed for {}: {}"} and the
 *    message is dropped to prevent retry storms.
 *
 * Retry / locking considerations:
 *  - Camel SEDA with concurrentConsumers=1 ensures only one writer at a time; LuceneIndexServiceImpl also serializes write
 *    operations via a ReentrantLock as an additional safeguard.
 *  - Automatic redelivery is disabled (handled(true)) to align with the logging guidance and avoid requeue loops.
 *
 * Integration points:
 *  - Serves as the bridge between Camel and LuceneIndexServiceImpl.
 *  - Consumes entities from the repositories (see UnifiedIndexingRoutes) and mirrors their structure one-to-one in the indexing methods.
 */
@Component("LuceneIndexingHubRoute")
@ConditionalOnProperty(value = "lifex.lucene.camel.enabled", havingValue = "true", matchIfMissing = true)
public class LuceneIndexingHubRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexingHubRoute.class);
    private final LuceneIndexService lucene;

    public LuceneIndexingHubRoute(LuceneIndexService lucene) {
        this.lucene = lucene;
    }

    @Override
    /**
     * Configures the onException logging and enforces a single-consumer flow.
     * Parallel processing is disabled to match the lock warnings emitted by the service.
     */
    public void configure() {

        // Global error handling: log and drop the message to avoid retry storms
        onException(Exception.class)
                .handled(true)
                .process(ex -> {
                    Object body = ex.getIn().getBody();
                    Exception cause = ex.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    log.error("Lucene indexing failed for {}: {}",
                            body == null ? "null" : body.getClass().getSimpleName(),
                            cause != null ? cause.getMessage() : "unknown",
                            cause);
                });

        // Single consumer to prevent write.lock conflicts
        from("seda:lucene-index?concurrentConsumers=1")
                .routeId("LuceneIndexHub")
                .process(ex -> {
                    Object body = ex.getIn().getBody();

                    if (body instanceof Account a) {
                        lucene.indexAccount(
                                a.getAccountID() != null ? a.getAccountID().toString() : null,
                                a.getAccountName(),
                                a.getCountry(),
                                a.getContactEmail()
                        );
                        return;
                    }

                    if (body instanceof Address a) {
                        lucene.indexAddress(
                                a.getAddressID() != null ? a.getAddressID().toString() : null,
                                a.getStreet(),
                                a.getCityID()
                        );
                        return;
                    }

                    if (body instanceof AudioDevice d) {
                        lucene.indexAudioDevice(
                                d.getAudioDeviceID() != null ? d.getAudioDeviceID().toString() : null,
                                d.getClientID() != null ? d.getClientID().toString() : null,
                                d.getAudioDeviceBrand(),
                                d.getDeviceSerialNr(),
                                d.getAudioDeviceFirmware(),
                                d.getDeviceType()
                        );
                        return;
                    }

                    if (body instanceof City c) {
                        lucene.indexCity(c.getCityID(), c.getCityName(), c.getCountryCode());
                        return;
                    }

                    if (body instanceof Clients c) {
                        lucene.indexClient(
                                c.getClientID() != null ? c.getClientID().toString() : null,
                                c.getSiteID() != null ? c.getSiteID().toString() : null,
                                c.getClientName(),
                                c.getClientBrand(),
                                c.getClientOS(),
                                c.getInstallType()
                        );
                        return;
                    }

                    if (body instanceof Country c) {
                        lucene.indexCountry(c.getCountryCode(), c.getCountryName());
                        return;
                    }

                    if (body instanceof DeploymentVariant v) {
                        lucene.indexDeploymentVariant(
                                v.getVariantID() != null ? v.getVariantID().toString() : null,
                                v.getVariantCode(),
                                v.getVariantName(),
                                v.getDescription(),
                                v.isActive()
                        );
                        return;
                    }

                    if (body instanceof InstalledSoftware is) {
                        lucene.indexInstalledSoftware(
                                is.getInstalledSoftwareID() != null ? is.getInstalledSoftwareID().toString() : null,
                                is.getSiteID() != null ? is.getSiteID().toString() : null,
                                is.getSoftwareID() != null ? is.getSoftwareID().toString() : null,
                                is.getStatus(),
                                is.getOfferedDate(),
                                is.getInstalledDate(),
                                is.getRejectedDate(),
                                is.getOutdatedDate()
                        );
                        return;
                    }

                    if (body instanceof PhoneIntegration p) {
                        lucene.indexPhoneIntegration(
                                p.getPhoneIntegrationID() != null ? p.getPhoneIntegrationID().toString() : null,
                                p.getClientID() != null ? p.getClientID().toString() : null,
                                p.getPhoneType(),
                                p.getPhoneBrand(),
                                p.getPhoneSerialNr(),
                                p.getPhoneFirmware()
                        );
                        return;
                    }

                    if (body instanceof Project p) {
                        lucene.indexProject(
                                p.getProjectID() != null ? p.getProjectID().toString() : null,
                                p.getProjectSAPID(),
                                p.getProjectName(),
                                p.getDeploymentVariantID() != null ? p.getDeploymentVariantID().toString() : null,
                                p.getBundleType(),
                                p.getLifecycleStatus() != null ? p.getLifecycleStatus().name() : null,
                                p.getAccountID() != null ? p.getAccountID().toString() : null,
                                p.getAddressID() != null ? p.getAddressID().toString() : null
                        );
                        return;
                    }

                    if (body instanceof Radio r) {
                        lucene.indexRadio(
                                r.getRadioID() != null ? r.getRadioID().toString() : null,
                                r.getSiteID() != null ? r.getSiteID().toString() : null,
                                r.getAssignedClientID() != null ? r.getAssignedClientID().toString() : null,
                                r.getRadioBrand(),
                                r.getRadioSerialNr(),
                                r.getMode(),
                                r.getDigitalStandard()
                        );
                        return;
                    }

                    if (body instanceof Server s) {
                        lucene.indexServer(
                                s.getServerID() != null ? s.getServerID().toString() : null,
                                s.getSiteID() != null ? s.getSiteID().toString() : null,
                                s.getServerName(),
                                s.getServerBrand(),
                                s.getServerSerialNr(),
                                s.getServerOS(),
                                s.getPatchLevel(),
                                s.getVirtualPlatform(),
                                s.getVirtualVersion(),
                                s.isHighAvailability()
                        );
                        return;
                    }

                    if (body instanceof ServiceContract sc) {
                                lucene.indexServiceContract(
                                        sc.getContractID() != null ? sc.getContractID().toString() : null,
                                        sc.getAccountID() != null ? sc.getAccountID().toString() : null,
                                        sc.getProjectID() != null ? sc.getProjectID().toString() : null,
                                        sc.getSiteID() != null ? sc.getSiteID().toString() : null,
                                        sc.getContractNumber(),
                                        sc.getStatus(),
                                        sc.getStartDate() != null ? sc.getStartDate().toString() : null,
                                        sc.getEndDate() != null ? sc.getEndDate().toString() : null
                                );
                        return;
                    }

                    if (body instanceof Site s) {
                        lucene.indexSite(
                                s.getSiteID() != null ? s.getSiteID().toString() : null,
                                s.getProjectID() != null ? s.getProjectID().toString() : null,
                                s.getAddressID() != null ? s.getAddressID().toString() : null,
                                s.getSiteName(),
                                s.getFireZone(),
                                s.getTenantCount()
                        );
                        return;
                    }

                    if (body instanceof Software sw) {
                        lucene.indexSoftware(
                                sw.getSoftwareID() != null ? sw.getSoftwareID().toString() : null,
                                sw.getName(),
                                sw.getRelease(),
                                sw.getRevision(),
                                sw.getSupportPhase(),
                                sw.getLicenseModel(),
                                sw.isThirdParty(),
                                sw.getEndOfSalesDate(),
                                sw.getSupportStartDate(),
                                sw.getSupportEndDate()
                        );
                        return;
                    }

                    if (body instanceof UpgradePlan up) {
                                lucene.indexUpgradePlan(
                                        up.getUpgradePlanID() != null ? up.getUpgradePlanID().toString() : null,
                                        up.getSiteID() != null ? up.getSiteID().toString() : null,
                                        up.getSoftwareID() != null ? up.getSoftwareID().toString() : null,
                                        up.getPlannedWindowStart() != null ? up.getPlannedWindowStart().toString() : null,
                                        up.getPlannedWindowEnd() != null ? up.getPlannedWindowEnd().toString() : null,
                                        up.getStatus(),
                                        up.getCreatedAt() != null ? up.getCreatedAt().toString() : null,
                                        up.getCreatedBy()
                                );
                        return;
                    }

                    // Fallback: log unsupported payload types
                    log.warn("LuceneIndexHub: Unsupported body type: {}", body == null ? "null" : body.getClass());
                });
    }
}
