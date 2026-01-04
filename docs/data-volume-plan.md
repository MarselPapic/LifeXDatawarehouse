# Seed Data Plan (around 500 records)

The following target volumes are based on the schema from `src/main/resources/schema.sql` and ensure the dataset contains roughly 500 interlinked records. The "Variation" column explains which attributes receive descriptive or rotating values to keep the sample data readable.

| Table | Target Count | Variation for readability |
| --- | --- | --- |
| `Country` | 10 | ISO codes remain fixed, but the spelled-out country names are chosen to be diverse. |
| `City` | 30 | Combined IDs (e.g., `DE-BERLIN`) and a variety of city names per country. |
| `Address` | 60 | Different street names with numbers and city references. |
| `Account` | 30 | Distinct organization names (`Guardian Network 01`), varying contacts, phone numbers, and domains. |
| `DeploymentVariant` | 10 | Expressive codes (`URB-HA`) and descriptive names per variant. |
| `Software` | 12 | Version sequences (`2025.1`, `2025.2`), changing license and support phases. |
| `Project` | 38 | Project SAP IDs follow a sequence (`PX-2101`), project names like `Project Aurora 01`, and lifecycle statuses alternate between Active, Maintenance, Planned, and Retired. |
| `Site` | 55 | Site names reference the city or project (`Aurora Hub Vienna`), with varied fire zones and tenant counts. |
| `Server` | 28 | Server names (`SRV-VIE-001`), brands, serial numbers, and OS/hypervisor combinations vary. |
| `Clients` | 40 | Operator station names (`Operator Console 014`), serial numbers, and a mix of installation types (`LOCAL` and `BROWSER`). |
| `Radio` | 18 | Serial numbers include site abbreviations, with modes toggling between `Analog` and `Digital` and standards rotating accordingly. |
| `AudioDevice` | 36 | Device labels (`Headset 021`), firmware versions, and device types (`HEADSET`, `SPEAKER`, `MIC`). |
| `PhoneIntegration` | 32 | Telephone types rotate (`Emergency`, `NonEmergency`, `Both`), with interface names and capacities varying per record. |
| `InstalledSoftware` | 55 | Each installation links a site to software; release combinations alternate for coverage. |
| `UpgradePlan` | 18 | Time windows use `DATEADD` offsets, and status values rotate through `Planned`, `Approved`, and more. |
| `ServiceContract` | 28 | Contract numbers such as `SC-2025-030`, status values rotate, and durations use relative date values. |

**Note on UUID readability:** A dedicated sequence is maintained for each table. The last block of the UUID receives a two-digit hex prefix for the table type plus a ten-digit decimal sequence number (e.g., `05 0000000123` for the 123rd project record). This approach keeps the IDs easy to scan in the UI while still producing valid UUIDs.
