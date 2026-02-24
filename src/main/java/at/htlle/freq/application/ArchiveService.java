package at.htlle.freq.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Centralized soft-delete/restore service with recursive dependency handling.
 */
@Service
public class ArchiveService {

    private enum IdType { UUID, STRING }

    private record ParentRef(String parentAlias, String fkColumnOnCurrent) {}

    private record ChildRef(String childAlias, String fkColumnOnChild) {}

    private static final class Meta {
        private final String table;
        private final String pk;
        private final IdType idType;
        private final List<ParentRef> parents = new ArrayList<>();
        private final List<ChildRef> children = new ArrayList<>();

        private Meta(String table, String pk, IdType idType) {
            this.table = table;
            this.pk = pk;
            this.idType = idType;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, Meta> metadata;

    public ArchiveService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.metadata = buildMetadata();
    }

    /**
     * Archives an entity and recursively archives all dependent children.
     *
     * @param tableAlias table alias.
     * @param rawId entity identifier.
     * @param actor actor name.
     * @return true when the entity exists, false otherwise.
     */
    @Transactional
    public boolean archive(String tableAlias, String rawId, String actor) {
        Meta meta = resolve(tableAlias);
        Object id = parseId(meta, rawId);
        if (!exists(meta, id)) {
            return false;
        }
        archiveRecursive(meta, id, normalizeActor(actor), new HashSet<>());
        return true;
    }

    /**
     * Restores an entity and recursively restores required parents and children.
     *
     * @param tableAlias table alias.
     * @param rawId entity identifier.
     * @param actor actor name.
     * @return true when the entity exists, false otherwise.
     */
    @Transactional
    public boolean restore(String tableAlias, String rawId, String actor) {
        Meta meta = resolve(tableAlias);
        Object id = parseId(meta, rawId);
        if (!exists(meta, id)) {
            return false;
        }
        restoreRecursive(meta, id, normalizeActor(actor), new HashSet<>());
        return true;
    }

    public boolean supports(String tableAlias) {
        return metadata.containsKey(normalizeAlias(tableAlias));
    }

    public String tableFor(String tableAlias) {
        return resolve(tableAlias).table;
    }

    private void archiveRecursive(Meta meta, Object id, String actor, Set<String> visited) {
        String key = "A|" + meta.table + "|" + id;
        if (!visited.add(key)) {
            return;
        }
        for (ChildRef childRef : meta.children) {
            Meta child = resolve(childRef.childAlias());
            for (Object childId : findChildIds(child, childRef.fkColumnOnChild(), id, false)) {
                archiveRecursive(child, childId, actor, visited);
            }
        }
        archiveSelf(meta, id, actor);
    }

    private void restoreRecursive(Meta meta, Object id, String actor, Set<String> visited) {
        String key = "R|" + meta.table + "|" + id;
        if (!visited.add(key)) {
            return;
        }
        Map<String, Object> parentValues = readParentValues(meta, id);
        for (ParentRef parentRef : meta.parents) {
            Object parentId = parentValues.get(parentRef.fkColumnOnCurrent());
            if (parentId == null) {
                continue;
            }
            Meta parent = resolve(parentRef.parentAlias());
            Object normalizedParentId = castId(parent, parentId);
            if (normalizedParentId != null && exists(parent, normalizedParentId)) {
                restoreRecursive(parent, normalizedParentId, actor, visited);
            }
        }

        restoreSelf(meta, id);

        for (ChildRef childRef : meta.children) {
            Meta child = resolve(childRef.childAlias());
            for (Object childId : findChildIds(child, childRef.fkColumnOnChild(), id, true)) {
                restoreRecursive(child, childId, actor, visited);
            }
        }
    }

    private void archiveSelf(Meta meta, Object id, String actor) {
        String sql = "UPDATE " + meta.table +
                " SET IsArchived = TRUE, ArchivedAt = CURRENT_TIMESTAMP, ArchivedBy = :actor" +
                " WHERE " + meta.pk + " = :id AND IsArchived = FALSE";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("actor", actor));
    }

    private void restoreSelf(Meta meta, Object id) {
        String sql = "UPDATE " + meta.table +
                " SET IsArchived = FALSE, ArchivedAt = NULL, ArchivedBy = NULL" +
                " WHERE " + meta.pk + " = :id AND IsArchived = TRUE";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    private Map<String, Object> readParentValues(Meta meta, Object id) {
        if (meta.parents.isEmpty()) {
            return Map.of();
        }
        String cols = String.join(", ", meta.parents.stream().map(ParentRef::fkColumnOnCurrent).toList());
        String sql = "SELECT " + cols + " FROM " + meta.table + " WHERE " + meta.pk + " = :id";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0);
    }

    private List<Object> findChildIds(Meta child, String fkColumn, Object parentId, boolean archivedState) {
        String sql = "SELECT " + child.pk + " FROM " + child.table +
                " WHERE " + fkColumn + " = :pid AND IsArchived = :archived";
        return jdbc.query(sql,
                new MapSqlParameterSource().addValue("pid", parentId).addValue("archived", archivedState),
                (rs, n) -> child.idType == IdType.UUID
                        ? rs.getObject(child.pk, java.util.UUID.class)
                        : rs.getString(child.pk));
    }

    private boolean exists(Meta meta, Object id) {
        String sql = "SELECT COUNT(1) FROM " + meta.table + " WHERE " + meta.pk + " = :id";
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Integer.class);
        return count != null && count > 0;
    }

    private Meta resolve(String tableAlias) {
        Meta meta = metadata.get(normalizeAlias(tableAlias));
        if (meta == null) {
            throw new IllegalArgumentException("Unsupported archive table: " + tableAlias);
        }
        return meta;
    }

    private Object parseId(Meta meta, String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (meta.idType == IdType.STRING) {
            return rawId;
        }
        try {
            return java.util.UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid UUID: " + rawId, ex);
        }
    }

    private Object castId(Meta meta, Object value) {
        if (value == null) {
            return null;
        }
        if (meta.idType == IdType.STRING) {
            return String.valueOf(value);
        }
        if (value instanceof java.util.UUID uuid) {
            return uuid;
        }
        try {
            return java.util.UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "system";
        }
        return actor.trim();
    }

    private String normalizeAlias(String alias) {
        return alias == null ? "" : alias.trim().toLowerCase(Locale.ROOT);
    }

    private void alias(Map<String, Meta> map, Meta meta, String... aliases) {
        for (String alias : aliases) {
            map.put(normalizeAlias(alias), meta);
        }
    }

    private void addParents(Meta meta, ParentRef... refs) {
        meta.parents.addAll(Arrays.asList(refs));
    }

    private void addChildren(Meta meta, ChildRef... refs) {
        meta.children.addAll(Arrays.asList(refs));
    }

    private Map<String, Meta> buildMetadata() {
        Map<String, Meta> map = new HashMap<>();

        Meta account = new Meta("Account", "AccountID", IdType.UUID);
        Meta address = new Meta("Address", "AddressID", IdType.UUID);
        Meta audio = new Meta("AudioDevice", "AudioDeviceID", IdType.UUID);
        Meta city = new Meta("City", "CityID", IdType.STRING);
        Meta clients = new Meta("Clients", "ClientID", IdType.UUID);
        Meta country = new Meta("Country", "CountryCode", IdType.STRING);
        Meta variant = new Meta("DeploymentVariant", "VariantID", IdType.UUID);
        Meta installed = new Meta("InstalledSoftware", "InstalledSoftwareID", IdType.UUID);
        Meta phone = new Meta("PhoneIntegration", "PhoneIntegrationID", IdType.UUID);
        Meta project = new Meta("Project", "ProjectID", IdType.UUID);
        Meta projectSite = new Meta("ProjectSite", "ProjectSiteID", IdType.UUID);
        Meta radio = new Meta("Radio", "RadioID", IdType.UUID);
        Meta server = new Meta("Server", "ServerID", IdType.UUID);
        Meta serviceContract = new Meta("ServiceContract", "ContractID", IdType.UUID);
        Meta site = new Meta("Site", "SiteID", IdType.UUID);
        Meta software = new Meta("Software", "SoftwareID", IdType.UUID);
        Meta upgrade = new Meta("UpgradePlan", "UpgradePlanID", IdType.UUID);

        alias(map, account, "account");
        alias(map, address, "address");
        alias(map, audio, "audiodevice", "audio");
        alias(map, city, "city");
        alias(map, clients, "clients", "client", "workingposition");
        alias(map, country, "country");
        alias(map, variant, "deploymentvariant", "variant");
        alias(map, installed, "installedsoftware");
        alias(map, phone, "phoneintegration", "phone", "phones");
        alias(map, project, "project");
        alias(map, projectSite, "projectsite");
        alias(map, radio, "radio");
        alias(map, server, "server");
        alias(map, serviceContract, "servicecontract");
        alias(map, site, "site");
        alias(map, software, "software");
        alias(map, upgrade, "upgradeplan");

        addChildren(country, new ChildRef("city", "CountryCode"));

        addParents(city, new ParentRef("country", "CountryCode"));
        addChildren(city, new ChildRef("address", "CityID"));

        addParents(address, new ParentRef("city", "CityID"));
        addChildren(address,
                new ChildRef("project", "AddressID"),
                new ChildRef("site", "AddressID"));

        addChildren(account,
                new ChildRef("project", "AccountID"),
                new ChildRef("servicecontract", "AccountID"));

        addChildren(variant, new ChildRef("project", "DeploymentVariantID"));

        addParents(project,
                new ParentRef("account", "AccountID"),
                new ParentRef("address", "AddressID"),
                new ParentRef("deploymentvariant", "DeploymentVariantID"));
        addChildren(project,
                new ChildRef("site", "ProjectID"),
                new ChildRef("servicecontract", "ProjectID"),
                new ChildRef("projectsite", "ProjectID"));

        addParents(site,
                new ParentRef("project", "ProjectID"),
                new ParentRef("address", "AddressID"));
        addChildren(site,
                new ChildRef("server", "SiteID"),
                new ChildRef("clients", "SiteID"),
                new ChildRef("phoneintegration", "SiteID"),
                new ChildRef("installedsoftware", "SiteID"),
                new ChildRef("upgradeplan", "SiteID"),
                new ChildRef("servicecontract", "SiteID"),
                new ChildRef("radio", "SiteID"),
                new ChildRef("projectsite", "SiteID"));

        addParents(clients, new ParentRef("site", "SiteID"));
        addChildren(clients,
                new ChildRef("audiodevice", "ClientID"),
                new ChildRef("radio", "AssignedClientID"));

        addParents(audio, new ParentRef("clients", "ClientID"));

        addParents(radio,
                new ParentRef("site", "SiteID"),
                new ParentRef("clients", "AssignedClientID"));

        addParents(server, new ParentRef("site", "SiteID"));
        addParents(phone, new ParentRef("site", "SiteID"));

        addChildren(software,
                new ChildRef("installedsoftware", "SoftwareID"),
                new ChildRef("upgradeplan", "SoftwareID"));
        addParents(installed,
                new ParentRef("site", "SiteID"),
                new ParentRef("software", "SoftwareID"));
        addParents(upgrade,
                new ParentRef("site", "SiteID"),
                new ParentRef("software", "SoftwareID"));

        addParents(serviceContract,
                new ParentRef("account", "AccountID"),
                new ParentRef("project", "ProjectID"),
                new ParentRef("site", "SiteID"));

        addParents(projectSite,
                new ParentRef("project", "ProjectID"),
                new ParentRef("site", "SiteID"));

        return Map.copyOf(map);
    }
}
