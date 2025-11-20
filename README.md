# LifeX Data Warehouse

---

## 📑 Project Overview

LifeX Data Warehouse is a lean yet feature-rich application developed as part of a diploma thesis at HTL Leoben (Department of Information Technology).
The project combines **ETL-style data storage**, **full-text search via Lucene** (or regular search with autocomplete), a **lightweight web UI**, and a **REST API** in a single Spring Boot codebase.

> **Mission Statement**
> "Static master data (Account → Project → Site …) should be captured, searched, and exported quickly—without heavyweight BI tools."

Global search supports **Lucene syntax or regular input** with automatic prefix expansion (token\*), autocomplete suggestions, and enriched result lists.

---

## ✨ Key Features

- **Data Model** – relational H2 in-memory database (Account, Project, Site, Server …)
- **API** – CRUD REST controllers per entity + generic `GenericCrudController` (GET/POST/PUT/DELETE)
- **Reporting & Export** – KPI overviews with filters, KPI tiles, and CSV/PDF export via `/api/reports/*`
- **Indexing** – Apache Lucene 8 (full reindex every 3 minutes + incremental Camel sync, manual reindex via UI)
- **Search**
  - Global Lucene query syntax on the dashboard and via `/search?q=`
  - Regular search terms are automatically converted to prefix searches (`example*`)
  - Autocomplete with suggestions
  - Result lists with an additional information column (e.g., contact data, brands, variants)
- **UI**
  - Pure static HTML / CSS / JS (no build tool required)
  - Shortcut buttons are directly editable (name + query)
  - Progress indicator for ongoing index builds with live data from `/api/index-progress`
  - Generic table viewer (100-row preview)
- **Automation** – Apache Camel 4 timer routes (sync, full reindex, single index)
- **Developer Ergonomics** – Spring Boot DevTools, LiveReload, H2 console, Lombok

---

## 🏗️ Architecture Overview

```text
┌──────────────────────────────┐     Timer          ┌─────────────────────────┐
│             UI               │  (Camel 4)         │      Lucene Index       │
│  static/ (HTML + JS + CSS)   │ ────────────►      │   · account docs        │
└────────────┬─────────────────┘                    │   · project docs        │
             │  REST (JSON)                         └────────────┬────────────┘
┌────────────▼─────────────────┐  Spring Boot 3 (Java 17)        │ search()
│          Web Layer           │                                 │
│  AccountController …         │ ◄───────────────────────────────┘
└────────────┬─────────────────┘        JDBC
             │                                 ┌─────────────────────────┐
┌────────────▼─────────────────┐            ┌─►│  H2 Database (memory)   │
│       Service Layer          │            │  └─────────────────────────┘
│  AccountService …            │            │
└────────────┬─────────────────┘            │
             │ Repository (NamedParamJdbc)  │
┌────────────▼─────────────────┐            │
│        Domain Model          │            │
│  POJOs + Lombok DTOs         │ ◄──────────┘
└──────────────────────────────┘
```

---

## 🧰 Tech Stack

- Java 17 (17.x LTS)
- Spring Boot 3.4.6
- H2 Database 2.3.x
- Apache Lucene 8.11.4
- Apache Camel 4.4.1
- Maven 3.9+
- Lombok & Spring DevTools

---

## 🚀 Build & Run

```bash
# Clone repository
git clone https://github.com/<user>/LifeXDatawarehouse.git
cd LifeXDatawarehouse

# Clean build & run tests (clears any stale target/ output)
./mvnw clean verify

# Start in dev mode
./mvnw spring-boot:run
```

The CI pipeline should run `./mvnw clean verify` (or `mvn clean verify`) to guarantee a fresh build and prevent `target/` artifacts from lingering between jobs.

**Open in the browser:**

- http://localhost:8080
- Hot reload via DevTools
- H2 console: `/h2-console` (JDBC URL: `jdbc:h2:mem:testdb`)

---

## 🧪 Seed Data & Generator

- `src/main/resources/data.sql` now contains roughly **500 interconnected records** across all tables (Countries → ServiceContract). The volume planning is documented in [`docs/data-volume-plan.md`](docs/data-volume-plan.md).
- The **UUIDs** receive a two-digit hex prefix per table in the last block (e.g., `07` for `Project`) plus a ten-digit sequence number. This convention makes it easier to group IDs in the UI while keeping them compliant with the UUID format.
- Use the helper tool [`SeedDataGenerator`](src/test/java/at/htlle/freq/seed/SeedDataGenerator.java) to reproduce the dataset. The generator creates a backup (`data.sql.legacy`) when necessary and then overwrites the current seed file.
- After updating the generator, rebuild and run it with `javac --release 17 -d target/test-classes src/test/java/at/htlle/freq/seed/SeedDataGenerator.java && java -cp target/test-classes at.htlle.freq.seed.SeedDataGenerator`

---

## 🌐 REST API (Quick Reference)

- `GET  /accounts` – all accounts
- `GET  /accounts/{id}` – single account
- `POST /accounts` – create a new account (JSON body)
- `POST /projects` – create a new project; optional `stillActive` flag (default `true`)
- `GET  /search?q=…` – global search (Lucene or regular)
  → Returns hit objects with `id`, `type`, `text` (primary label) and optional `snippet` (additional content); the frontend fetches detail data from `/row/{table}/{id}`
- `GET  /table/{name}` – 100-row dump of a table
- `GET  /row/{name}/{id}` – single row (detail view)
- `POST /row/{name}` – generic insert via `GenericCrudController`
- `PUT  /row/{name}/{id}` – generic update (field-based merge)
- `DELETE /row/{name}/{id}` – generic delete
- `GET  /api/reports/options` – filter and KPI options for reporting
- `GET  /api/reports/data` – aggregated metrics including table view
- `GET  /api/reports/export/csv` – export the current evaluation as CSV
- `GET  /api/reports/export/pdf` – export the current evaluation as PDF

Additional endpoints for `Project`, `Site`, `Server`, and more follow the same pattern.

---

## 🖥️ Frontend Pages

- **`index.html` – Dashboard**
  - Global search (Lucene + regular search with automatic `*`)
  - Autocomplete suggestions while typing
  - Editable shortcut buttons
  - Table explorer
  - Result list with an additional info column
  - Reindex button and progress bar for index builds
  - Dashboard polls progress regularly via `/api/index-progress`; the backend index job provides status values for this

- **`create.html` – Record creation**
  - Step-by-step wizard to create new records (including Country, City, Address, Software, InstalledSoftware, UpgradePlan, and ServiceContract)
  - Dynamic form fields per entity type with dependent dropdowns and asynchronous data sources
  - Direct validation in the browser (required fields, data types, date logic)
  - Final summary before saving

- **`details.html` – Detail view**
  - Generic key/value display for all fields
  - Linked entities shown as clickable links
  - Unified layout for all entity types
  - Toggle between compact view and full view

- **`reports.html` – Reporting & KPI overview**
  - Dynamic filters (time range, search term, variants)
  - KPI tiles and table view from `/api/reports/data`
  - CSV and PDF export buttons (`/api/reports/export/*`)
  - Instant UI updates when filters change

**All assets:**
Live under `src/main/resources/static/`—no frontend build required.

---

## 🔍 Lucene Quick Ref

```text
tech*                       # Wildcard
"green valley"              # Phrase
+foo -bar                   # Must / Must not
country:germany             # Field search
type:project AND statusActive        # Active projects
type:serviceContract AND statusInProgress  # Ongoing service contracts
type:site AND zoneBravo              # Sites in FireZone Bravo
type:server AND Lenovo      # Lenovo server inventory
```

**Frontend feature:**
If no Lucene syntax is detected, the frontend automatically appends `*` to the search term (prefix search).

**Preset dashboard shortcuts:**

- Accounts – Total inventory → `type:account`
- Projects – Active → `type:project AND statusActive`
- Service contracts – In progress → `type:serviceContract AND statusInProgress`
- Sites – FireZone Bravo → `type:site AND zoneBravo`
- Servers – Lenovo → `type:server AND Lenovo`

**Indexed fields (examples):**

- Account → `txt` (name), `country`
- Project → `txt` (name), `variant`
- Site    → `txt` (name), `fireZone`
- Server  → `txt` (name), `os`

```text
erDiagram
    Account ||--o{ Project           : owns
    Project ||--o{ Site              : hosts
    Site    ||--o{ Server            : contains
    Site    ||--o{ WorkingPosition   : "WP"
    WorkingPosition ||--|{ AudioDevice      : has
    WorkingPosition ||--|{ PhoneIntegration : phones
```

*(You can find the full SQL definition in `schema.sql`.)*

---

## 🛡️ Quality & CI

- **JUnit tests** – Maven runs the available tests under `src/test/java`, including those for `IndexProgress` and its REST controller.
- **IndexProgress updates** – The progress indicator uses the production updates from `IndexProgress`, keeping UI and API aligned.
- **Local checks** – Before commits, `mvn test` runs alongside manual UI checks (autocomplete, debouncing, API fallbacks).
- **Static analysis** – Checkstyle and SpotBugs remain on the roadmap.

---

## 🚧 Roadmap

- ✔️ Lucene index + global search
- ✔️ Shortcut UI (editable)
- ✔️ Create wizard
- ✔️ Autocomplete in search
- ✔️ Additional info in result list
- ✔️ CSV export via REST (`/api/reports/export/csv`)
- ✔️ PDF export via REST (`/api/reports/export/pdf`)
- ☐ Excel export via REST
- ☐ User authentication (Spring Security + JWT)
- ☐ Docker Compose (PostgreSQL + OpenSearch)

---

## 👥 Contributors

- Mario Ziegerhofer – Developer
- Marcel Papic – Developer
- Alexander Schüller – Team Lead

---

© 2025 Mario Ziegerhofer • HTL Leoben Information Technology • All information is provided as-is without warranty.
