package at.htlle.freq.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

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
    private static final Map<String, Set<String>> COLUMNS;
    private static final Map<String, Set<String>> DATE_COLUMNS;
    private static final Pattern COLUMN_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

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

        Map<String, Set<String>> c = new HashMap<>();
        c.put("Account", Set.of("AccountID", "AccountName", "ContactName", "ContactEmail", "ContactPhone", "VATNumber", "Country"));
        c.put("Project", Set.of("ProjectID", "ProjectSAPID", "ProjectName", "DeploymentVariantID", "BundleType", "CreateDateTime", "LifecycleStatus", "AccountID", "AddressID"));
        c.put("Site", Set.of("SiteID", "SiteName", "ProjectID", "AddressID", "FireZone", "TenantCount"));
        c.put("Server", Set.of("ServerID", "SiteID", "ServerName", "ServerBrand", "ServerSerialNr", "ServerOS", "PatchLevel", "VirtualPlatform", "VirtualVersion", "HighAvailability"));
        c.put("Clients", Set.of("ClientID", "SiteID", "ClientName", "ClientBrand", "ClientSerialNr", "ClientOS", "PatchLevel", "InstallType"));
        c.put("Radio", Set.of("RadioID", "SiteID", "AssignedClientID", "RadioBrand", "RadioSerialNr", "Mode", "DigitalStandard"));
        c.put("AudioDevice", Set.of("AudioDeviceID", "ClientID", "AudioDeviceBrand", "DeviceSerialNr", "AudioDeviceFirmware", "DeviceType"));
        c.put("PhoneIntegration", Set.of("PhoneIntegrationID", "ClientID", "PhoneType", "PhoneBrand", "PhoneSerialNr", "PhoneFirmware"));
        c.put("Country", Set.of("CountryCode", "CountryName"));
        c.put("City", Set.of("CityID", "CityName", "CountryCode"));
        c.put("Address", Set.of("AddressID", "Street", "CityID"));
        c.put("DeploymentVariant", Set.of("VariantID", "VariantCode", "VariantName", "Description", "IsActive"));
        c.put("Software", Set.of("SoftwareID", "Name", "Release", "Revision", "SupportPhase", "LicenseModel", "ThirdParty", "EndOfSalesDate", "SupportStartDate", "SupportEndDate"));
        c.put("InstalledSoftware", Set.of("InstalledSoftwareID", "SiteID", "SoftwareID", "Status",
                "OfferedDate", "InstalledDate", "RejectedDate"));
        c.put("UpgradePlan", Set.of("UpgradePlanID", "SiteID", "SoftwareID", "PlannedWindowStart", "PlannedWindowEnd", "Status", "CreatedAt", "CreatedBy"));
        c.put("ServiceContract", Set.of("ContractID", "AccountID", "ProjectID", "SiteID", "ContractNumber", "Status", "StartDate", "EndDate"));
        COLUMNS = Collections.unmodifiableMap(c);

        Map<String, Set<String>> d = new HashMap<>();
        d.put("UpgradePlan", Set.of("PlannedWindowStart", "PlannedWindowEnd", "CreatedAt"));
        d.put("ServiceContract", Set.of("StartDate", "EndDate"));
        DATE_COLUMNS = Collections.unmodifiableMap(d);
    }

    private ResponseStatusException logAndThrow(HttpStatus status, String table, String action, String reason) {
        return logAndThrow(status, table, action, reason, null);
    }

    private ResponseStatusException logAndThrow(HttpStatus status, String table, String action, String reason, String logReason) {
        String actionLabel = action == null ? "(unknown action)" : action;
        String tableLabel = table == null ? "(unknown table)" : table;
        String message = logReason == null ? reason : logReason;
        log.warn("Action {} on table {} failed: {}", actionLabel, tableLabel, message);
        return new ResponseStatusException(status, reason);
    }

    private String normalizeTable(String name) {
        if (name == null) throw logAndThrow(HttpStatus.BAD_REQUEST, null, "normalizeTable", "table missing");
        String key = name.trim().toLowerCase();
        String table = TABLES.get(key);
        if (table == null) {
            throw logAndThrow(HttpStatus.BAD_REQUEST, name, "normalizeTable", "unknown table: " + name, "unknown table");
        }
        return table;
    }

    private static boolean pkIsString(String table) {
        return "Country".equals(table) || "City".equals(table);
    }

    private Map<String, Object> sanitizeColumns(String table, Map<String, Object> body) {
        Set<String> allowed = COLUMNS.get(table);
        if (allowed == null) {
            throw logAndThrow(HttpStatus.BAD_REQUEST, table, "sanitizeColumns", "no columns known for table " + table);
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String column = entry.getKey();
            if (!COLUMN_PATTERN.matcher(column).matches()) {
                throw logAndThrow(HttpStatus.BAD_REQUEST, table, "sanitizeColumns", "invalid column: " + column);
            }
            if (!allowed.contains(column)) {
                throw logAndThrow(HttpStatus.BAD_REQUEST, table, "sanitizeColumns", "column not allowed: " + column);
            }
            sanitized.put(column, entry.getValue());
        }

        if (sanitized.isEmpty()) {
            throw logAndThrow(HttpStatus.BAD_REQUEST, table, "sanitizeColumns", "empty body");
        }
        return sanitized;
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
        if (pk == null) throw logAndThrow(HttpStatus.BAD_REQUEST, table, "row", "no PK known for table " + table);

        String sql = "SELECT * FROM " + table + " WHERE " + pk + " = :id";
        var params = new MapSqlParameterSource("id", id);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        if (rows.isEmpty()) throw logAndThrow(HttpStatus.NOT_FOUND, table, "row", "not found");
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
        if (body.isEmpty()) throw logAndThrow(HttpStatus.BAD_REQUEST, table, "insert", "empty body");

        Map<String, Object> sanitized = convertTemporalValues(table, sanitizeColumns(table, body));

        var columns = String.join(", ", sanitized.keySet());
        var values = ":" + String.join(", :", sanitized.keySet());

        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ")";
        jdbc.update(sql, new MapSqlParameterSource(sanitized));

        String pk = PKS.get(table);
        Object recordId = null;
        if (pk != null) {
            recordId = sanitized.get(pk);
        }
        if (recordId == null) {
            recordId = sanitized.getOrDefault("id", sanitized.getOrDefault("ID", "(unknown)"));
        }

        log.info("Inserted {} {} with fields {}", table, recordId, sanitized.keySet());
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
        if (pk == null) throw logAndThrow(HttpStatus.BAD_REQUEST, table, "update", "no PK known for table " + table);

        if (body.isEmpty()) throw logAndThrow(HttpStatus.BAD_REQUEST, table, "update", "empty body");

        Map<String, Object> sanitized = new LinkedHashMap<>(convertTemporalValues(table, sanitizeColumns(table, body)));

        Object removedPkValue = sanitized.remove(pk);
        if (removedPkValue != null) {
            if (sanitized.isEmpty()) {
                throw logAndThrow(HttpStatus.BAD_REQUEST, table, "update", "no updatable columns provided");
            }
            log.debug("Ignoring primary key column {} in update for table {}", pk, table);
        }

        if (sanitized.isEmpty()) {
            throw logAndThrow(HttpStatus.BAD_REQUEST, table, "update", "no updatable columns provided");
        }

        var setClauses = new ArrayList<String>();
        for (String col : sanitized.keySet()) {
            setClauses.add(col + " = :" + col);
        }
        String sql = "UPDATE " + table + " SET " + String.join(", ", setClauses) + " WHERE " + pk + " = :id";
        var params = new MapSqlParameterSource(sanitized).addValue("id", id);

        int count = jdbc.update(sql, params);
        if (count == 0)
            throw logAndThrow(HttpStatus.NOT_FOUND, table, "update", "no record updated");

        log.info("Updated {} {} with fields {}", table, id, sanitized.keySet());
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
        if (pk == null) throw logAndThrow(HttpStatus.BAD_REQUEST, table, "delete", "no PK known for table " + table);

        String sql = "DELETE FROM " + table + " WHERE " + pk + " = :id";
        int count = jdbc.update(sql, new MapSqlParameterSource("id", id));
        if (count == 0) {
            log.warn("Attempted to delete {} {} but no record was found", table, id);
            throw logAndThrow(HttpStatus.NOT_FOUND, table, "delete", "no record deleted");
        }

        log.info("Deleted {} {} with fields {}", table, id, Collections.singleton(pk));
    }

    private Map<String, Object> convertTemporalValues(String table, Map<String, Object> values) {
        Set<String> dateColumns = DATE_COLUMNS.get(table);
        if (dateColumns == null || dateColumns.isEmpty()) {
            return values;
        }

        Map<String, Object> converted = new LinkedHashMap<>(values);
        for (String column : dateColumns) {
            if (!converted.containsKey(column)) {
                continue;
            }
            Object value = converted.get(column);
            if (value == null || value instanceof LocalDate) {
                continue;
            }
            if (value instanceof String s) {
                try {
                    converted.put(column, LocalDate.parse(s));
                } catch (DateTimeParseException ex) {
                    throw logAndThrow(HttpStatus.BAD_REQUEST, table, "convertTemporalValues", "invalid date for " + column,
                            "invalid date for " + column);
                }
            } else {
                throw logAndThrow(HttpStatus.BAD_REQUEST, table, "convertTemporalValues", "invalid date type for " + column);
            }
        }
        return converted;
    }
}
