package at.htlle.freq.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Generischer CRUD-Controller auf Tabellenbasis.
 *
 * <p>Erlaubt vereinfachte Verwaltungsoperationen über den
 * {@link NamedParameterJdbcTemplate} und eine Whitelist an Tabellen.</p>
 */
@RestController
public class GenericCrudController {

    private static final Logger log = LoggerFactory.getLogger(GenericCrudController.class);

    private final NamedParameterJdbcTemplate jdbc;

    public GenericCrudController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------- Whitelist gültiger Tabellen + Aliase --------
    private static final Map<String, String> TABLES;
    private static final Map<String, String> PKS;

    static {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("account", "Account");
        t.put("project", "Project");
        t.put("site", "Site");
        t.put("server", "Server");
        t.put("client", "Clients");
        t.put("clients", "Clients");
        t.put("workingposition", "Clients");
        t.put("radio", "Radio");
        t.put("audiodevice", "AudioDevice");
        t.put("phoneintegration", "PhoneIntegration");
        t.put("country", "Country");
        t.put("city", "City");
        t.put("address", "Address");
        t.put("deploymentvariant", "DeploymentVariant");
        t.put("software", "Software");
        t.put("installedsoftware", "InstalledSoftware");
        t.put("upgradeplan", "UpgradePlan");
        t.put("servicecontract", "ServiceContract");
        TABLES = Collections.unmodifiableMap(t);

        Map<String, String> p = new HashMap<>();
        p.put("Account", "AccountID");
        p.put("Project", "ProjectID");
        p.put("Site", "SiteID");
        p.put("Server", "ServerID");
        p.put("Clients", "ClientID");
        p.put("Radio", "RadioID");
        p.put("AudioDevice", "AudioDeviceID");
        p.put("PhoneIntegration", "PhoneIntegrationID");
        p.put("Country", "CountryCode");
        p.put("City", "CityID");
        p.put("Address", "AddressID");
        p.put("DeploymentVariant", "VariantID");
        p.put("Software", "SoftwareID");
        p.put("InstalledSoftware", "InstalledSoftwareID");
        p.put("UpgradePlan", "UpgradePlanID");
        p.put("ServiceContract", "ContractID");
        PKS = Collections.unmodifiableMap(p);
    }

    private String normalizeTable(String name) {
        if (name == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "table missing");
        String key = name.trim().toLowerCase();
        String table = TABLES.get(key);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown table: " + name);
        }
        return table;
    }

    private static boolean pkIsString(String table) {
        return "Country".equals(table) || "City".equals(table);
    }

    // -------- READ --------

    /**
     * Liest mehrere Zeilen einer Whitelist-Tabelle.
     *
     * <p>Pfad: {@code GET /table/{name}}</p>
     * <p>Pfadvariable: {@code name} – Tabellenalias aus der Whitelist.</p>
     * <p>Query-Parameter: {@code limit} (optional, 1-500) begrenzt die Ergebnismenge.</p>
     *
     * @param name  Tabellenalias.
     * @param limit gewünschte Anzahl Datensätze.
     * @return 200 OK mit einer JSON-Liste von Zeilen.
     */
    @GetMapping("/table/{name}")
    public List<Map<String, Object>> list(@PathVariable String name,
                                          @RequestParam(name = "limit", defaultValue = "100") int limit) {
        String table = normalizeTable(name);
        limit = Math.max(1, Math.min(limit, 500));
        return jdbc.queryForList("SELECT * FROM " + table + " LIMIT " + limit, new HashMap<>());
    }

    /**
     * Liest eine einzelne Zeile aus einer Whitelist-Tabelle.
     *
     * <p>Pfad: {@code GET /row/{name}/{id}}</p>
     *
     * @param name Tabellenalias.
     * @param id   Primärschlüsselwert (als String übertragen).
     * @return 200 OK mit der Datenzeile oder 404 bei unbekannter ID.
     */
    @GetMapping("/row/{name}/{id}")
    public Map<String, Object> row(@PathVariable String name, @PathVariable String id) {
        String table = normalizeTable(name);
        String pk = PKS.get(table);
        if (pk == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no PK known for table " + table);

        String sql = "SELECT * FROM " + table + " WHERE " + pk + " = :id";
        var params = new MapSqlParameterSource("id", id);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        return rows.get(0);
    }

    // -------- CREATE --------
    /**
     * Erstellt eine neue Zeile in einer Whitelist-Tabelle.
     *
     * <p>Pfad: {@code POST /row/{name}}</p>
     * <p>Request-Body: JSON-Objekt mit Spaltennamen und Werten.</p>
     *
     * @param name Tabellenalias.
     * @param body Spaltenwerte.
     * @throws ResponseStatusException 400 bei unbekannter Tabelle oder leerem Body.
     */
    @PostMapping("/row/{name}")
    @ResponseStatus(HttpStatus.CREATED)
    public void insert(@PathVariable String name, @RequestBody Map<String, Object> body) {
        String table = normalizeTable(name);
        if (body.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");

        var columns = String.join(", ", body.keySet());
        var values = ":" + String.join(", :", body.keySet());

        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ")";
        jdbc.update(sql, new MapSqlParameterSource(body));

        String pk = PKS.get(table);
        Object recordId = null;
        if (pk != null) {
            recordId = body.get(pk);
        }
        if (recordId == null) {
            recordId = body.getOrDefault("id", body.getOrDefault("ID", "(unknown)"));
        }

        log.info("Inserted {} {} with fields {}", table, recordId, body.keySet());
    }

    // -------- UPDATE --------
    /**
     * Aktualisiert eine vorhandene Zeile.
     *
     * <p>Pfad: {@code PUT /row/{name}/{id}}</p>
     * <p>Request-Body: JSON-Objekt mit zu setzenden Spalten.</p>
     *
     * @param name Tabellenalias.
     * @param id   Primärschlüsselwert.
     * @param body zu übernehmende Werte.
     * @throws ResponseStatusException 400 bei leerem Body oder unbekannter Tabelle, 404 wenn nichts aktualisiert wurde.
     */
    @PutMapping("/row/{name}/{id}")
    public void update(@PathVariable String name, @PathVariable String id, @RequestBody Map<String, Object> body) {
        String table = normalizeTable(name);
        String pk = PKS.get(table);
        if (pk == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no PK known for table " + table);

        if (body.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty body");

        var setClauses = new ArrayList<String>();
        for (String col : body.keySet()) {
            setClauses.add(col + " = :" + col);
        }
        String sql = "UPDATE " + table + " SET " + String.join(", ", setClauses) + " WHERE " + pk + " = :id";
        var params = new MapSqlParameterSource(body).addValue("id", id);

        int count = jdbc.update(sql, params);
        if (count == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no record updated");

        log.info("Updated {} {} with fields {}", table, id, body.keySet());
    }

    // -------- DELETE --------
    /**
     * Löscht eine Zeile aus einer Whitelist-Tabelle.
     *
     * <p>Pfad: {@code DELETE /row/{name}/{id}}</p>
     *
     * @param name Tabellenalias.
     * @param id   Primärschlüsselwert.
     * @throws ResponseStatusException 404 wenn kein Datensatz gelöscht wurde.
     */
    @DeleteMapping("/row/{name}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name, @PathVariable String id) {
        String table = normalizeTable(name);
        String pk = PKS.get(table);
        if (pk == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no PK known for table " + table);

        String sql = "DELETE FROM " + table + " WHERE " + pk + " = :id";
        int count = jdbc.update(sql, new MapSqlParameterSource("id", id));
        if (count == 0) {
            log.warn("Attempted to delete {} {} but no record was found", table, id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no record deleted");
        }

        log.info("Deleted {} {} with fields {}", table, id, Collections.singleton(pk));
    }
}
