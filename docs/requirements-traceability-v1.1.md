# Requirements Traceability Matrix (V1.1)

Source document: `HTLLE01_LifeXDWH_Req_V1.1.pdf`  
Created: 2026-02-22

## Status legend
- `ERFUELLT`: Implemented and backed by code + test evidence.
- `TEILWEISE`: Core implementation exists, but requirement scope is not fully covered.
- `OFFEN`: Missing implementation or missing verifiable acceptance evidence.
- `AUS_SCOPE_ENTSCHEID`: Deliberately not implemented based on agreed project scope.

## Requirement matrix
| Req ID | Heading | Status | Ist-Umsetzung (kurz) | Nachweis (Code/Test) | Gap / Naechster Schritt |
|---|---|---|---|---|---|
| 2.1.01 | Project phases | ERFUELLT | Projekt-Lifecycle wird gespeichert und validiert (offer/active/maintenance/eol). | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/domain/ProjectLifecycleStatus.java`, `src/main/java/at/htlle/freq/web/ProjectController.java` | Kein Gap sichtbar. |
| 2.1.02 | Data model consistency | ERFUELLT | Relationen/FKs/Unique-Constraints vorhanden; keine eventual consistency Architektur. | `src/main/resources/schema.sql`, `src/test/java/at/htlle/freq/web/RelationshipLinkingIntegrationTest.java` | Kein Gap sichtbar. |
| 2.1.03 | Database load expectations | OFFEN | Datenmodell und Seed-Daten vorhanden, aber kein Last-/Kapazitaetsnachweis gegen Grenzwerte. | `docs/data-volume-plan.md`, `src/main/resources/data.sql` | Lasttest-Suite (200 Kunden/200 Projekte/1000 Komponenten) mit Messprotokoll erstellen. |
| 2.1.04 | Compare offered vs installed SW/HW | TEILWEISE | InstalledSoftware hat Status und Datumsfelder; Report-Views vorhanden, aber kein expliziter Diff-Report Offered vs Installed inkl. HW. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/application/report/ReportService.java` | Dedizierten Delta-Report (offered minus installed) fuer SW+HW ergaenzen. |
| 2.1.05 | Document configuration of installed SW/HW | TEILWEISE | Erfassung und Pflege ueber UI + CRUD fuer Software/Hardware vorhanden. | `src/main/resources/static/create.html`, `src/main/resources/static/details.html`, `src/test/java/at/htlle/freq/web/CreateFlowIntegrationTest.java` | Keine formale Usability-Abnahme (nur funktional). |
| 2.1.06 | Document project specialties | ERFUELLT | Projekt-Sonderheiten werden ueber `SpecialNotes` gespeichert. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/ProjectController.java` | Kein Gap sichtbar. |
| 2.1.07 | Document changes of installed SW/HW | ERFUELLT | Aenderungen via Update-Endpoints; technische Nachvollziehbarkeit ueber Audit-Logs inkl. Fail-Faelle. | `src/main/java/at/htlle/freq/infrastructure/logging/AuditLogger.java`, `src/main/java/at/htlle/freq/web/SiteController.java`, `src/test/java/at/htlle/freq/infrastructure/logging/AuditLoggerTest.java` | Kein Gap sichtbar. |
| 2.1.08 | Report SW/HW under maintenance | TEILWEISE | Risk- und Support-Reports vorhanden; ServiceContract Daten existieren. Vollstaendiger Maintenance-Report je Produkt (SW+HW) mit Start/End nicht als eigener Report umgesetzt. | `src/main/java/at/htlle/freq/application/report/ReportService.java`, `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/ServiceContractController.java` | Eigenen Maintenance-Report je Produkt/Kunde/Projekt inkl. Start/End definieren und implementieren. |
| 2.1.09 | Report configuration of installed SW/HW | TEILWEISE | Konfigurationsdaten sind ueber Detailansichten/Entity-APIs verfuegbar, aber kein dedizierter Konfigurationsreport fuer SW+HW als Gesamtbild. | `src/main/java/at/htlle/freq/web/SiteController.java`, `src/main/resources/static/details.html` | Aggregierten Konfigurationsreport (Projekt/Site/SW/HW) ergaenzen. |
| 2.1.10 | Report installed SW/HW | TEILWEISE | SW-Reporting inkl. Version/Release/Revision vorhanden; HW-Reporting nicht als konsolidierter Report umgesetzt. | `src/main/java/at/htlle/freq/application/report/ReportService.java`, `src/test/java/at/htlle/freq/application/report/ReportServiceTest.java` | HW in Reporting-Modul aufnehmen oder explizit als nicht gefordert abgrenzen. |
| 2.1.11 | Project information storage | ERFUELLT | Geforderte Projektfelder + Beziehungen (Account/Address/Sites) umgesetzt. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/ProjectController.java`, `src/test/java/at/htlle/freq/web/CreateFlowIntegrationTest.java` | Kein Gap sichtbar. |
| 2.1.12 | Account information storage | ERFUELLT | Kontodaten inkl. Kontakt/VAT/Country gespeichert und ueber API bearbeitbar. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/AccountController.java`, `src/test/java/at/htlle/freq/web/CreateFlowIntegrationTest.java` | Kein Gap sichtbar. |
| 2.1.13 | Site information storage | TEILWEISE | Site-Kerndaten und InstalledSoftware-Assignments vorhanden. Optionales Feld fuer Architekturzeichnung nicht im Modell. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/SiteController.java` | Optionales Zeichnungsfeld (Dateireferenz/Blob) nur umsetzen, falls fuer Abnahme verlangt. |
| 2.1.14 | Server information storage | ERFUELLT | Alle benoetigten Serverfelder im Modell und CRUD vorhanden. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/ServerController.java`, `src/test/java/at/htlle/freq/web/CreateFlowIntegrationTest.java` | Kein Gap sichtbar. |
| 2.1.15 | Working position information storage | ERFUELLT | Clients-Tabelle deckt Working-Position Felder inkl. OS/Patch/InstallType/Type/OtherSW/Site ab. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/ClientController.java`, `src/test/java/at/htlle/freq/web/CreateFlowIntegrationTest.java` | Kein Gap sichtbar. |
| 2.1.16 | Audio peripheral information storage | ERFUELLT | AudioDevice inkl. Brand/Serial/Firmware/Direction und Bezug zu Working Position vorhanden. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/AudioDeviceController.java`, `src/test/java/at/htlle/freq/web/CreateFlowIntegrationTest.java` | Kein Gap sichtbar. |
| 2.1.17 | Phone integration information storage | ERFUELLT | PhoneIntegration inkl. Site/Interface/Type/Brand/Firmware/Capacity vorhanden. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/web/PhoneController.java`, `src/test/java/at/htlle/freq/web/CreateFlowIntegrationTest.java` | Kein Gap sichtbar. |
| 2.1.18 | Installed software information storage | ERFUELLT | Software- und InstalledSoftware-Datenmodell deckt Name/Version/Release/Revision/SupportPhase/LicenseModel/ThirdParty ab. | `src/main/resources/schema.sql`, `src/main/java/at/htlle/freq/application/InstalledSoftwareService.java`, `src/test/java/at/htlle/freq/web/RelationshipLinkingIntegrationTest.java` | Kein Gap sichtbar. |
| 2.2.1 | Web UI | ERFUELLT | Web-UI mit Dashboard/Create/Details/Reports vorhanden. | `src/main/resources/static/index.html`, `src/main/resources/static/create.html`, `src/main/resources/static/details.html`, `src/main/resources/static/reports.html`, `src/test/java/at/htlle/freq/web/FrontendLinkRegressionTest.java` | Kein Gap sichtbar. |
| 2.3.1 | Backend service interface authentication | ERFUELLT | Backend-Endpunkte sind per HTTP Basic geschuetzt; Preset-Passwort konfiguriert. | `src/main/java/at/htlle/freq/infrastructure/security/BackendSecurityConfig.java`, `src/main/resources/application.properties`, `src/test/java/at/htlle/freq/web/BackendSecurityIntegrationTest.java` | Kein Gap sichtbar. |
| 2.4.1 | User interface authentication | ERFUELLT | UI bleibt ohne eigene Login-Maske erreichbar; nur Backend-APIs sind geschuetzt. | `src/main/java/at/htlle/freq/infrastructure/security/BackendSecurityConfig.java`, `src/test/java/at/htlle/freq/web/BackendSecurityIntegrationTest.java` | Kein Gap sichtbar. |
| 2.4.2 | Logging | ERFUELLT | App-/Audit-/Ops-Logs, Request-Kontext, Access-Logging, strukturierte Audit-Eintraege inkl. FAIL. | `src/main/resources/logback-spring.xml`, `src/main/java/at/htlle/freq/infrastructure/logging/LoggingContextFilter.java`, `src/main/java/at/htlle/freq/infrastructure/logging/AccessLoggingFilter.java`, `src/test/java/at/htlle/freq/infrastructure/logging/AccessLoggingFilterTest.java`, `src/test/java/at/htlle/freq/infrastructure/logging/LogbackRoutingIntegrationTest.java` | Kein Gap sichtbar. |
| 2.4.3 | Disaster recovery | AUS_SCOPE_ENTSCHEID | Kein eigenes DR-Modul umgesetzt; Backup-Strategie auf Container-/Volume-Ebene beschlossen. | `README.md` | Falls formal verlangt: DR-Akzeptanztext im Requirement explizit auf Container-Backup aendern. |
| 3.1.1 | Performance UI search <= 10s | OFFEN | Suchfunktion vorhanden, aber kein verifizierter SLA-Nachweis unter Last. | `src/main/java/at/htlle/freq/web/SearchController.java`, `src/test/java/at/htlle/freq/web/SearchControllerIntegrationTest.java` | Messbare Performance-Tests mit Grenzwert-Assertions einfuehren. |
| 3.1.2 | Performance UI report rendering <= 10s | OFFEN | Reporting vorhanden, aber kein automatischer Performance-Nachweis fuer 10s-SLA. | `src/main/java/at/htlle/freq/web/ReportController.java`, `src/test/java/at/htlle/freq/web/ReportControllerMaintenanceIntegrationTest.java` | Performance-Tests mit realistischem Datenvolumen und Laufzeitprotokoll einfuehren. |
| 3.2.1 | Code readability and maintainability | TEILWEISE | Strukturierte Packages, Controller-/Service-Trennung, umfangreiche Tests und Doku vorhanden. | `src/main/java`, `src/test/java`, `README.md` | Formale Style-Gates (z. B. Checkstyle/Spotless/Sonar) als CI-Musskriterium ergaenzen. |

## Summary
- `ERFUELLT`: 15
- `TEILWEISE`: 7
- `OFFEN`: 3
- `AUS_SCOPE_ENTSCHEID`: 1

## Priorisierte Restarbeiten fuer Abnahme
1. Last- und Performance-Nachweis fuer 2.1.03, 3.1.1, 3.1.2.
2. Reporting-Luecken fuer 2.1.04, 2.1.08, 2.1.09, 2.1.10 final klaeren (implementieren oder scope-klarstellen).
3. 2.4.3 formell im Requirement-Set als Container-Backup-Entscheid dokumentieren.
