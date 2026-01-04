# LifeX Data Warehouse

## Overview
LifeX Data Warehouse is a lean but feature-rich Spring Boot application created as part of a diploma thesis at HTL Leoben (Information Technology). The project combines ETL-style storage, full-text search, reporting, and a lightweight web UI in a single codebase.

Mission: capture static master data (accounts, projects, sites, hardware, software, contracts), make it searchable, and export operational insights without heavyweight BI tooling.

## Key Features
- Data model in H2 (in-memory) covering accounts, projects, sites, hardware, software, and contracts.
- REST API with dedicated controllers per entity plus a generic table CRUD API.
- Reporting for software support end dates with CSV export.
- Lucene 8 full-text search with smart query parsing, field filters, and autocomplete suggestions.
- Apache Camel routes for scheduled reindexing and single-entity indexing.
- Static HTML/CSS/JS frontend (no build tool required).
- Live index progress via `/api/index-progress` and manual reindex via `/api/index/reindex`.

## Architecture Overview
```
[Static UI] -> [REST Controllers] -> [Services] -> [JDBC/H2]
                               \-> [Lucene Index]
[Camel timers + direct routes] -> [Lucene Index]
```

## Tech Stack
- Java 17 (LTS)
- Spring Boot 3.4.6
- H2 Database 2.3.x
- Apache Lucene 8.11.4
- Apache Camel 4.4.1
- Apache PDFBox 2.0.32
- Maven 3.9+
- Lombok, Spring DevTools

## Build and Run
```bash
# Clean build & run tests
./mvnw clean verify

# Start in dev mode
./mvnw spring-boot:run
```

Open in the browser:
- http://localhost:8080
- H2 console: `/h2-console` (JDBC URL: `jdbc:h2:mem:testdb`)

The Lucene index is stored under `target/lifex-index` by default.

## Seed Data and Generator
- `src/main/resources/data.sql` provides the initial dataset (hundreds of interconnected rows across all tables). The volume planning is documented in `docs/data-volume-plan.md`.
- UUIDs receive a two-digit hex prefix per table in the last block (e.g., `07` for Project) plus a 10-digit sequence number. This keeps IDs sortable while staying UUID-compatible.
- Use the helper tool `SeedDataGenerator` to reproduce the dataset:
  - `javac --release 17 -d target/test-classes src/test/java/at/htlle/freq/seed/SeedDataGenerator.java && java -cp target/test-classes at.htlle.freq.seed.SeedDataGenerator`

## Data Model (Main Entities)
Account, Address, AudioDevice, City, Client, Country, DeploymentVariant, InstalledSoftware, PhoneIntegration, Project, Radio, Server, ServiceContract, Site, Software, UpgradePlan, plus join tables such as ProjectSite.

## REST API Quick Reference
Search:
- `GET /search?q=` free-text or Lucene query; optional `type` filter and `raw=true` to force Lucene parsing
- `GET /search/suggest?q=&max=` autocomplete suggestions

Indexing:
- `POST /api/index/reindex` start full reindex
- `GET /api/index-progress` current reindex progress

Reporting:
- `GET /api/reports/data?from=&to=&preset=` support end report data
- `GET /api/reports/export/csv?from=&to=&preset=` CSV export

Generic table API:
- `GET /table/{name}` 100-row preview of a table
- `GET /row/{name}/{id}` single row
- `POST /row/{name}` insert
- `PUT /row/{name}/{id}` update (field merge)
- `DELETE /row/{name}/{id}` delete

Entity controllers (selection):
- `/accounts`, `/projects`, `/sites`, `/servers`, `/addresses`, `/clients`, `/radios`, `/audio`, `/phones`, `/deployment-variants`, `/servicecontracts`

Site-specific helpers:
- `GET /sites/software-summary?status=` aggregated counts by status
- `GET /sites/{id}/detail` site with software assignments and project links
- `GET /sites/{id}/software` software assignment overview
- `PATCH /sites/{siteId}/software/{installationId}/status` update an install status

## Frontend Pages
- `index.html` Dashboard (global search, shortcuts, table preview, index progress)
- `create.html` Record creation wizard with validation and dependent dropdowns
- `details.html` Generic detail view with linked entities
- `reports.html` Support end report with CSV export

All assets live under `src/main/resources/static/`.

Language guideline: all user-facing text and documentation must be written in English.

## Search Syntax Quick Reference
```
tech*                       # Prefix wildcard
"green valley"              # Phrase
+foo -bar                   # Must / must not
country:germany             # Field search
(type:site OR type:server) AND status:active
```

## Quality and CI
- Tests live under `src/test/java` and are executed via `./mvnw test` or `./mvnw clean verify`.
- Manual UI checks typically cover search, autocomplete, and report export.

## Manual QA (UI)
- Create a project and assign multiple sites; verify the main index lists only project rows.
- Edit a site and update project assignments; ensure join-table links update correctly.
- Clear selections in create or edit flows and confirm associations are removed cleanly.

## Roadmap
- [x] Lucene index + global search
- [x] Shortcut UI (editable)
- [x] Create wizard
- [x] Autocomplete suggestions
- [x] CSV export via REST (`/api/reports/export/csv`)
- [ ] PDF export via REST
- [ ] Excel export via REST
- [ ] User authentication (Spring Security + JWT)
- [ ] Docker Compose (PostgreSQL + OpenSearch)

## Contributors
- Mario Ziegerhofer - Developer
- Marcel Papic - Developer
- Alexander Schueller - Team Lead

(c) 2025 Mario Ziegerhofer - HTL Leoben Information Technology. All information is provided as-is without warranty.
