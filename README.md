# LifeX Data Warehouse

---

## 📑 Projektüberblick

LifeX Data Warehouse ist eine schlanke, aber funktionsreiche Applikation, die im Rahmen einer Diplomarbeit an der HTL Leoben (Abteilung Informationstechnik) entsteht.  
Sie vereint **ETL-ähnliche Datenhaltung**, **Volltextsuche via Lucene** (oder normale Suche mit Autocomplete), ein **leichtgewichtiges Web-UI** und eine **REST-API** in einem einzigen Spring-Boot-Projekt.

> **Mission Statement**  
> „Statische Stammdaten (Account → Project → Site …) sollen schnell erfasst, durchsucht und exportiert werden können – ohne schwergewichtige BI-Tools.“

Neu: Die globale Suche unterstützt jetzt **Lucene-Syntax oder normale Eingaben** mit automatischer Präfix-Erweiterung (token\*), Autocomplete-Vorschlägen und angereicherten Ergebnislisten.

---

## ✨ Haupt-Features

- **Datenmodell** – relationale H2-In-Memory-DB (Account, Project, Site, Server …)
- **API** – CRUD-REST-Controller je Entität + generischer GenericCrudController (GET/POST/PUT/DELETE)
- **Reporting & Export** – KPI-Übersichten mit Filtern, KPI-Kacheln sowie CSV/PDF-Export via `/api/reports/*`
- **Indexing** – Apache Lucene 8 (Full-Reindex alle 3 min + inkrementeller Camel-Sync, manuelles Reindexing über UI)
- **Suche**
  - Globale Lucene-Query-Syntax im Dashboard und via `/search?q=`
  - Normale Suchbegriffe werden automatisch zu Präfix-Suchen (`beispiel*`)
  - Autocomplete mit Vorschlägen
  - Ergebnislisten mit zusätzlicher Info-Spalte (z. B. Kontaktdaten, Marken, Varianten)
- **UI**
  - Rein statisches HTML / CSS / JS (kein Build-Tool erforderlich)
  - Shortcut-Buttons direkt editierbar (Name + Query)
  - Fortschrittsanzeige für laufenden Index-Build mit Live-Daten aus `/api/index-progress`
  - Generischer Tabellen-Viewer (100 Zeilen Vorschau)
- **Automation** – Apache Camel 4 Timer-Routes (Sync, Full-Reindex, Einzel-Index)
- **Dev-Ergonomie** – Spring Boot DevTools, LiveReload, H2-Console, Lombok

---

## 🏗️ Architektur-Überblick

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

## 🧰 Tech-Stack

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
# Repository klonen
git clone https://github.com/<user>/LifeXDatawarehouse.git
cd LifeXDatawarehouse

# Start im Dev-Modus
mvn spring-boot:run
```

**Öffnen im Browser:**

- http://localhost:8080
- Hot-Reload via DevTools
- H2-Console: `/h2-console` (JDBC-URL: `jdbc:h2:mem:testdb`)

---

## 🧪 Seed-Daten & Generator

- `src/main/resources/data.sql` enthält jetzt rund **500 miteinander verknüpfte Datensätze** über alle Tabellen hinweg (Countries → ServiceContract). Die Mengenplanung ist in [`docs/data-volume-plan.md`](docs/data-volume-plan.md) dokumentiert.
- Die **UUIDs** erhalten im letzten Block ein zweistelliges Hex-Präfix pro Tabelle (z. B. `07` für `Project`) und eine zehnstellige Sequenznummer. Dadurch lassen sich IDs im UI leichter gruppieren, bleiben aber vollständig UUID-kompatibel.
- Zur Reproduktion dient das Hilfstool [`SeedDataGenerator`](src/test/java/at/htlle/freq/seed/SeedDataGenerator.java). Der Generator legt bei Bedarf ein Backup (`data.sql.legacy`) an und überschreibt anschließend die aktuelle Seed-Datei.
- Nach Änderungen am Generator: `javac --release 17 -d target/test-classes src/test/java/at/htlle/freq/seed/SeedDataGenerator.java && java -cp target/test-classes at.htlle.freq.seed.SeedDataGenerator`

---

## 🌐 REST-API (Schnellreferenz)

- `GET  /accounts` – alle Accounts
- `GET  /accounts/{id}` – einzelner Account
- `POST /accounts` – neuen Account anlegen (JSON-Body)
- `POST /projects` – neues Projekt; optionales `stillActive`-Flag (Default `true`)
- `GET  /search?q=…` – globale Suche (Lucene oder normal)
  → Liefert Trefferobjekte mit `id`, `type`, `text` (Primärbezeichnung) und optional `snippet` (zusätzliche Inhalte); das Frontend lädt Detaildaten aus `/row/{table}/{id}` nach
- `GET  /table/{name}` – 100-Zeilen-Dump einer Tabelle
- `GET  /row/{name}/{id}` – Einzel-Zeile (Detail-View)
- `POST /row/{name}` – Generischer Insert über den GenericCrudController
- `PUT  /row/{name}/{id}` – Generisches Update (feldbasierter Merge)
- `DELETE /row/{name}/{id}` – Generisches Löschen
- `GET  /api/reports/options` – Filter- und KPI-Optionen für das Reporting
- `GET  /api/reports/data` – Aggregierte Kennzahlen inkl. Tabellenansicht
- `GET  /api/reports/export/csv` – Export der aktuellen Auswertung als CSV
- `GET  /api/reports/export/pdf` – Export der aktuellen Auswertung als PDF

Weitere Endpunkte für `Project`, `Site`, `Server` usw. analog.

---

## 🖥️ Frontend-Seiten

- **`index.html` – Dashboard**
  - Globale Suche (Lucene + normale Suche mit automatischem `*`)
  - Autocomplete-Vorschläge beim Tippen
  - Editierbare Shortcut-Buttons
  - Tabellen-Explorer
  - Ergebnisliste mit zusätzlicher Info-Spalte
  - Reindex-Button und Fortschrittsbalken für Indexaufbau
  - Dashboard fragt den Fortschritt regelmäßig über `/api/index-progress` ab; der Backend-Indexlauf liefert hierzu Statuswerte

- **`create.html` – Datensatz-Erstellung**
  - Schritt-für-Schritt-Wizard zur Anlage neuer Datensätze (inkl. Country, City, Address, Software, InstalledSoftware, UpgradePlan und ServiceContract)
  - Dynamische Formularfelder je Entitätstyp mit abhängigen Dropdowns und asynchronen Datenquellen
  - Direkte Validierung der Eingaben im Browser (Pflichtfelder, Datentypen, Datumslogik)
  - Abschließende Übersicht vor dem Speichern

- **`details.html` – Detailansicht**
  - Generische Key/Value-Darstellung aller Felder
  - Verknüpfte Entitäten werden als klickbare Links angezeigt
  - Einheitliches Layout für alle Entitätstypen
  - Kompaktansicht und Vollansicht umschaltbar

- **`reports.html` – Reporting & KPI-Übersicht**
  - Dynamische Filter (Zeitraum, Suchbegriff, Varianten)
  - KPI-Kacheln und Tabellenansicht aus `/api/reports/data`
  - CSV- und PDF-Export über Buttons (`/api/reports/export/*`)
  - Sofortige UI-Aktualisierung beim Anpassen der Filter

**Alle Assets:**  
Liegen unter `src/main/resources/static/` – kein Frontend-Build nötig.

---

## 🔍 Lucene Quick Ref

```text
tech*                       # Wildcard  
"green valley"              # Phrase
+foo -bar                   # Muss / Nicht
country:germany             # Feldsuche
type:project AND statusActive        # Aktive Projekte
type:serviceContract AND statusInProgress  # Laufende Serviceverträge
type:site AND zoneBravo              # Sites in FireZone Bravo
type:server AND Lenovo      # Lenovo-Serverbestand
```

**Frontend-Feature:**
Wenn keine Lucene-Syntax erkannt wird, fügt das Frontend automatisch ein `*` an den Suchbegriff an (Präfixsuche).

**Voreingestellte Dashboard-Shortcuts:**

- Accounts – Gesamtbestand → `type:account`
- Projekte – aktiv → `type:project AND statusActive`
- Serviceverträge – In Progress → `type:serviceContract AND statusInProgress`
- Sites – FireZone Bravo → `type:site AND zoneBravo`
- Server – Lenovo → `type:server AND Lenovo`

**Indexierte Felder (Beispiele):**

- Account → `txt` (Name), `country`
- Project → `txt` (Name), `variant`
- Site    → `txt` (Name), `fireZone`
- Server  → `txt` (Name), `os`

```text
erDiagram
    Account ||--o{ Project           : owns
    Project ||--o{ Site              : hosts
    Site    ||--o{ Server            : contains
    Site    ||--o{ WorkingPosition   : "WP"
    WorkingPosition ||--|{ AudioDevice      : has
    WorkingPosition ||--|{ PhoneIntegration : phones
```

*(Die vollständige SQL-Definition findest du in `schema.sql`.)*

---

## 🛡️ Qualität & CI

- **JUnit-Tests** – Maven führt die vorhandenen Tests unter `src/test/java` aus, u. a. für `IndexProgress` und dessen REST-Controller.
- **IndexProgress-Updates** – Die Fortschrittsanzeige nutzt die produktiven Updates aus `IndexProgress`, sodass UI und API denselben Status liefern.
- **Lokale Checks** – Vor Commits laufen `mvn test` sowie manuelle UI-Prüfungen (Autocomplete, Debouncing, API-Fallbacks).
- **Statische Analyse** – Checkstyle und SpotBugs bleiben auf der Roadmap.

---

## 🚧 Roadmap

- ✔️ Lucene-Index + globale Suche
- ✔️ Shortcut-UI (editierbar)
- ✔️ Create-Wizard
- ✔️ Autocomplete in Suche
- ✔️ Zusatzinfos in Ergebnisliste
- ✔️ CSV-Export per REST (`/api/reports/export/csv`)
- ✔️ PDF-Export per REST (`/api/reports/export/pdf`)
- ☐ Excel-Export per REST
- ☐ Benutzer-Auth (Spring Security + JWT)
- ☐ Docker-Compose (PostgreSQL + OpenSearch)

---

## 👥 Mitwirkende

- Mario Ziegerhofer – Entwickler
- Marcel Papic – Entwickler
- Alexander Schüller – Team-Lead

---

© 2025 Mario Ziegerhofer • HTL Leoben Informationstechnik • Alle Angaben ohne Gewähr
