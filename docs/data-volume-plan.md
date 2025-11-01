# Seed-Datenplan (rund 500 Datensätze)

Die folgenden Zielmengen basieren auf dem Schema aus `src/main/resources/schema.sql` und wurden so gewählt, dass insgesamt 500 verbundene Datensätze entstehen. Die Spalte "Variation" beschreibt, welche Attribute zur besseren Lesbarkeit sprechend oder abwechslungsreich gestaltet werden.

| Tabelle | Zielanzahl | Variation für Lesbarkeit |
| --- | --- | --- |
| `Country` | 10 | ISO-Codes bleiben fix, aber ausgeschriebene Ländernamen werden vielfältig gewählt. |
| `City` | 30 | Kombinierte IDs (z. B. `DE-BERLIN`) und diverse Stadtnamen pro Land. |
| `Address` | 60 | Unterschiedliche Straßennamen mit Nummern und Stadtbezug. |
| `Account` | 30 | Prägnante Organisationsnamen (`Guardian Network 01`), variierende Ansprechpartner:innen, Telefonnummern und Domains. |
| `DeploymentVariant` | 10 | Aussagekräftige Codes (`URB-HA`) und sprechende Namen pro Ausprägung. |
| `Software` | 12 | Versionsketten (`2025.1`, `2025.2`), Lizenz- und Supportphasen wechseln. |
| `Project` | 38 | Projekt-SAP-IDs mit Sequenz (`PX-2101`), Projektnamen im Stil `Project Aurora 01`, boolesche Aktiv-Flaggen variieren. |
| `Site` | 55 | Standortnamen mit Bezug zu Stadt oder Projekt (`Aurora Hub Vienna`), unterschiedliche Brandabschnitte und Mieterzahlen. |
| `Server` | 28 | Servernamen (`SRV-VIE-001`), Marken, Seriennummern und OS/Hypervisor-Kombinationen wechseln. |
| `Clients` | 40 | Bedienplatznamen (`Operator Console 014`), Seriennummern sowie gemischte Installationsarten (`LOCAL`/`BROWSER`). |
| `Radio` | 18 | Seriennummern mit Standortkürzeln, unterschiedliche Modi (`Analog`/`Digital`) und Standards. |
| `AudioDevice` | 36 | Gerätebezeichnungen (`Headset 021`), Firmwarestände und Gerätetyp (`HEADSET`/`SPEAKER`/`MIC`). |
| `PhoneIntegration` | 32 | Telefon-Typen variieren (`Emergency`, `NonEmergency`, `Both`), Hersteller/Seriennummern wechseln. |
| `InstalledSoftware` | 55 | Jede Installation verknüpft Site und Software, Kombinationen wechseln zwischen Releases. |
| `UpgradePlan` | 18 | Zeitfenster als `DATEADD`-Offsets, Statuswerte rotieren (`Planned`, `Approved`, …). |
| `ServiceContract` | 28 | Vertragsnummern wie `SC-2025-030`, Statusmix, Laufzeiten als relative Datumsangaben. |

**Hinweis zur Lesbarkeit der UUIDs:** Für jede Tabelle wird eine eigene Sequenz geführt. Der letzte Block der UUID erhält ein zweistelliges Hex-Präfix für den Tabellentyp sowie eine zehnstellige, dezimale Sequenznummer (z. B. `05 0000000123` für den 123. Projekt-Datensatz). Somit lassen sich IDs im UI leicht einsortieren und dennoch als gültige UUIDs verwenden.
