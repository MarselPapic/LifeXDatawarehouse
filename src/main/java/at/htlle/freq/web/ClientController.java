// src/main/java/at/htlle/freq/web/ClientController.java
package at.htlle.freq.web;

import at.htlle.freq.application.ClientsService;
import at.htlle.freq.domain.ArchiveState;
import at.htlle.freq.domain.Clients;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for {@link Clients} within a site.
 *
 * <p>Uses {@link ClientsService} for persistence.</p>
 */
@RestController
@RequestMapping("/clients")
public class ClientController {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);

    private final ClientsService service;
    private final AuditLogger audit;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a controller that delegates client operations to {@link ClientsService}.
     *
     * @param service service used for client CRUD operations.
     */
    @Autowired
    public ClientController(ClientsService service, AuditLogger audit, NamedParameterJdbcTemplate jdbc) {
        this.service = service;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /**
     * Backwards-compatible constructor for tests.
     */
    public ClientController(ClientsService service, AuditLogger audit) {
        this(service, audit, null);
    }

    /**
     * Lists clients, optionally filtered by site.
     *
     * <p>Path: {@code GET /clients}</p>
     * <p>Query parameter: {@code siteId} (optional, UUID) for filtering.</p>
     *
     * @param siteId optional site ID.
     * @return 200 OK with a list of {@link Clients}.
     */
    @GetMapping
    public List<Clients> findBySite(@RequestParam(required = false) UUID siteId,
                                    @RequestParam(required = false, name = "archiveState") String archiveStateRaw) {
        ArchiveState archiveState = parseArchiveState(archiveStateRaw);
        if (archiveState == ArchiveState.ACTIVE || jdbc == null) {
            if (siteId == null) {
                return service.findAll();
            }
            return service.findBySite(siteId);
        }
        MapSqlParameterSource params = new MapSqlParameterSource("archived", archiveState.name());
        StringBuilder sql = new StringBuilder("""
                SELECT ClientID, SiteID, ClientName, ClientBrand, ClientSerialNr,
                       ClientOS, PatchLevel, InstallType, WorkingPositionType, OtherInstalledSoftware
                FROM Clients
                WHERE (:archived = 'ALL'
                       OR (:archived = 'ACTIVE' AND IsArchived = FALSE)
                       OR (:archived = 'ARCHIVED' AND IsArchived = TRUE))
                """);
        if (siteId != null) {
            sql.append(" AND SiteID = :sid");
            params.addValue("sid", siteId);
        }
        return jdbc.query(sql.toString(), params, (rs, n) -> new Clients(
                rs.getObject("ClientID", UUID.class),
                rs.getObject("SiteID", UUID.class),
                rs.getString("ClientName"),
                rs.getString("ClientBrand"),
                rs.getString("ClientSerialNr"),
                rs.getString("ClientOS"),
                rs.getString("PatchLevel"),
                rs.getString("InstallType"),
                rs.getString("WorkingPositionType"),
                rs.getString("OtherInstalledSoftware")
        ));
    }

    /**
     * Backwards-compatible overload without archive-state parameter.
     */
    public List<Clients> findBySite(UUID siteId) {
        return findBySite(siteId, null);
    }

    /**
     * Creates a new client.
     *
     * <p>Path: {@code POST /clients}</p>
     * <p>Request body: JSON representation of a {@link Clients} record validated by the service.</p>
     *
     * @param client client payload.
     * @return HTTP 200 containing the saved entity; HTTP 400/500 with an explanatory error message otherwise.
     */
    @PostMapping
    public ResponseEntity<Clients> create(@RequestBody Clients client) {
        try {
            Clients saved = service.create(client);
            Map<String, Object> identifiers = new HashMap<>();
            identifiers.put("ClientID", saved.getClientID());
            audit.created("Client", identifiers, saved);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            audit.failed("CREATE", "Client", Map.of(), ex.getMessage(), client);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            audit.failed("CREATE", "Client", Map.of(), ex.getMessage(), client);
            log.error("Create client failed", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Create client failed", ex);
        }
    }

    private ArchiveState parseArchiveState(String raw) {
        try {
            return ArchiveState.from(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
