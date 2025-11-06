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
 * Generic table-based CRUD controller.
 *
 * <p>Enables simplified administrative operations via {@link NamedParameterJdbcTemplate} and a whitelist of tables.</p>
 */
@RestController
public class GenericCrudController {

    private static final Logger log = LoggerFactory.getLogger(GenericCrudController.class);

    private final NamedParameterJdbcTemplate jdbc;

    public GenericCrudController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------- Whitelist of allowed tables and aliases --------
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
     * Reads multiple rows from an allowed table.
     *
     * <p>Path: {@code GET /table/{name}}</p>
     * <p>Path variable: {@code name} – table alias from the whitelist.</p>
     * <p>Query parameter: {@code limit} (optional, 1-500) limits the result size.</p>
     *
     * @param name  table alias.
     * @param limit desired number of records.
     * @return 200 OK with a JSON list of rows.
     */
    @GetMapping("/table/{name}")
    public List<Map<String, Object>> list(@PathVariable String name,
                                          @RequestParam(name = "limit", defaultValue = "100") int limit) {
        String table = normalizeTable(name);
        limit = Math.max(1, Math.min(limit, 500));
        return jdbc.queryForList("SELECT * FROM " + table + " LIMIT " + limit, new HashMap<>());
    }

    /**
     * Reads a single row from an allowed table.
     *
     * <p>Path: {@code GET /row/{name}/{id}}</p>
     *
     * @param name table alias.
     * @param id   primary key value (transferred as a string).
     * @return 200 OK with the data row or 404 if the ID is unknown.
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
     * Inserts a new row into an allowed table.
     *
     * <p>Path: {@code POST /row/{name}}</p>
     * <p>Request body: JSON object with column names and values.</p>
     *
     * @param name table alias.
     * @param body column values.
     * @throws ResponseStatusException 400 when the table is unknown or the body is empty.
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
     * Updates an existing row.
     *
     * <p>Path: {@code PUT /row/{name}/{id}}</p>
     * <p>Request body: JSON object with the columns to set.</p>
     *
     * @param name table alias.
     * @param id   primary key value.
     * @param body values to apply.
     * @throws ResponseStatusException 400 if the body is empty or the table is unknown, 404 if nothing was updated.
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
     * Deletes a row from an allowed table.
     *
     * <p>Path: {@code DELETE /row/{name}/{id}}</p>
     *
     * @param name table alias.
     * @param id   primary key value.
     * @throws ResponseStatusException 404 if no record was deleted.
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
