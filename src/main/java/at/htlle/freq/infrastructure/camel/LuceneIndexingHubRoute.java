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
 * Camel-Routen-Hub für alle Lucene-Schreiboperationen.
 *
 * Datenfluss:
 *  - UnifiedIndexingRoutes (Timer, Direct-Endpunkte) pushen Domain-Entities in "seda:lucene-index".
 *  - Diese Route konsumiert die Queue mit exactly-one Consumer und ruft abhängig vom Body die passenden indexXxx()-Methoden.
 *  - Fehler werden im onException-Block geloggt und die Nachricht verworfen, damit keine Retry-Stürme entstehen (siehe log.error).
 *
 * Retry-/Locking-Aspekte:
 *  - Camel-SEDA mit concurrentConsumers=1 verhindert parallele Writer-Zugriffe, zusätzlich serialisiert der Service selbst
 *    (ReentrantLock in LuceneIndexServiceImpl) die Schreibzugriffe.
 *  - Kein automatisches Redelivery: handled(true) sorgt dafür, dass Dead Letter Handling deaktiviert bleibt – konsistent mit den
 *    Logger-Hinweisen.
 *
 * Integrationspunkte:
 *  - Bindeglied zwischen Camel und LuceneIndexServiceImpl.
 *  - Konsumiert Entities aus Repositories (siehe UnifiedIndexingRoutes) und spiegelt deren Struktur 1:1 in die Indexmethoden.
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
     * Richtet das onException-Logging sowie den Single-Consumer-Flow ein.
     * Keine parallele Verarbeitung – consistent mit den Lock-Warnungen des Services.
     */
    public void configure() {

        // Globales Error-Handling: loggen & Nachricht verwerfen (kein Retry-Sturm)
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

        // EIN Consumer -> keine write.lock Konflikte
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
                                is.getStatus()
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
                                sc.getStartDate(),
                                sc.getEndDate()
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
                                up.getPlannedWindowStart(),
                                up.getPlannedWindowEnd(),
                                up.getStatus(),
                                up.getCreatedAt(),
                                up.getCreatedBy()
                        );
                        return;
                    }

                    // Fallback
                    log.warn("LuceneIndexHub: Unsupported body type: {}", body == null ? "null" : body.getClass());
                });
    }
}
