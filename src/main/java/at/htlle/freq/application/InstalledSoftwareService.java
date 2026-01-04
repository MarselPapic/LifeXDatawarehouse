package at.htlle.freq.application;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.InstalledSoftwareRepository;
import at.htlle.freq.domain.InstalledSoftwareStatus;
import at.htlle.freq.domain.SiteSoftwareOverview;
import at.htlle.freq.application.dto.SiteSoftwareOverviewEntry;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages installed software states, validates status values, and keeps the Lucene index up to date.
 */
@Service
public class InstalledSoftwareService {

    private static final Logger log = LoggerFactory.getLogger(InstalledSoftwareService.class);

    private final InstalledSoftwareRepository repo;
    private final LuceneIndexService lucene;

    /**
     * Creates the service with repository and index dependencies.
     *
     * @param repo   repository for installations
     * @param lucene Lucene indexing service
     */
    public InstalledSoftwareService(InstalledSoftwareRepository repo, LuceneIndexService lucene) {
        this.repo = repo;
        this.lucene = lucene;
    }

    // ---------- Queries ----------

    /**
     * Returns all installed software states.
     *
     * @return list of all installations
     */
    public List<InstalledSoftware> getAllInstalledSoftware() {
        return repo.findAll();
    }

    /**
     * Retrieves an installation by its identifier.
     *
     * @param id installation identifier
     * @return optional containing the installation or empty otherwise
     */
    public Optional<InstalledSoftware> getInstalledSoftwareById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repo.findById(id);
    }

    /**
     * Returns installations for a site.
     *
     * @param siteId site identifier
     * @return list of installations
     */
    public List<InstalledSoftware> getInstalledSoftwareBySite(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return repo.findBySite(siteId);
    }

    /**
     * Loads installed software assignments for a site with software metadata and normalized status labels.
     *
     * @param siteId identifier of the site
     * @return list of overview entries enriched with software information
     */
    public List<SiteSoftwareOverviewEntry> getSiteSoftwareOverview(UUID siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");

        return repo.findOverviewBySite(siteId).stream()
                .map(this::mapOverviewRow)
                .toList();
    }

    /**
     * Returns installations for a software record.
     *
     * @param softwareId software identifier
     * @return list of installations
     */
    public List<InstalledSoftware> getInstalledSoftwareBySoftware(UUID softwareId) {
        Objects.requireNonNull(softwareId, "softwareId must not be null");
        return repo.findBySoftware(softwareId);
    }

    // ---------- Commands ----------

    /**
     * Saves an installation, normalizes the status, and indexes it in Lucene after the commit.
     *
     * @param incoming installation to persist
     * @return stored installation
     */
    @Transactional
    public InstalledSoftware createOrUpdateInstalledSoftware(InstalledSoftware incoming) {
        Objects.requireNonNull(incoming, "installedSoftware payload must not be null");

        if (incoming.getSiteID() == null)
            throw new IllegalArgumentException("SiteID is required");
        if (incoming.getSoftwareID() == null)
            throw new IllegalArgumentException("SoftwareID is required");

        normalizeStatusAndDates(incoming);

        InstalledSoftware saved = repo.save(incoming);
        registerAfterCommitIndexing(saved);

        log.info("InstalledSoftware saved: id={} site={} software={} status={} offered={} installed={} rejected={} outdated={}",
                saved.getInstalledSoftwareID(), saved.getSiteID(), saved.getSoftwareID(), saved.getStatus(),
                saved.getOfferedDate(), saved.getInstalledDate(), saved.getRejectedDate(), saved.getOutdatedDate());
        return saved;
    }

    /**
     * Updates an installation and keeps the index synchronized.
     *
     * @param id    installation identifier
     * @param patch changes to apply to the installation
     * @return optional containing the updated installation or empty otherwise
     */
    @Transactional
    public Optional<InstalledSoftware> updateInstalledSoftware(UUID id, InstalledSoftware patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return repo.findById(id).map(existing -> {
            existing.setSiteID(patch.getSiteID() != null ? patch.getSiteID() : existing.getSiteID());
            existing.setSoftwareID(patch.getSoftwareID() != null ? patch.getSoftwareID() : existing.getSoftwareID());
            if (patch.getStatus() != null) {
                existing.setStatus(patch.getStatus());
            }
            if (patch.getOfferedDate() != null) {
                existing.setOfferedDate(patch.getOfferedDate());
            }
            if (patch.getInstalledDate() != null) {
                existing.setInstalledDate(patch.getInstalledDate());
            }
            if (patch.getRejectedDate() != null) { existing.setRejectedDate(patch.getRejectedDate()); }
            if (patch.getOutdatedDate() != null) { existing.setOutdatedDate(patch.getOutdatedDate()); }
            normalizeStatusAndDates(existing);

            InstalledSoftware saved = repo.save(existing);
            registerAfterCommitIndexing(saved);

            log.info("InstalledSoftware updated: id={} site={} software={} status={} offered={} installed={} rejected={} outdated={}",
                    id, saved.getSiteID(), saved.getSoftwareID(), saved.getStatus(),
                    saved.getOfferedDate(), saved.getInstalledDate(), saved.getRejectedDate(), saved.getOutdatedDate());
            return saved;
        });
    }

    /**
     * Updates only the status-related information of an installation.
     *
     * @param id     installation identifier
     * @param status new status value (see {@link InstalledSoftwareStatus})
     * @return persisted installation after the update
     */
    @Transactional
    public InstalledSoftware updateStatus(UUID id, String status) {
        Objects.requireNonNull(id, "id must not be null");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }

        InstalledSoftware patch = new InstalledSoftware();
        patch.setStatus(status);

        return updateInstalledSoftware(id, patch)
                .orElseThrow(() -> new NoSuchElementException("InstalledSoftware not found: " + id));
    }

    /**
     * Synchronises the installation records of a site with the provided collection. Missing records
     * are removed while new or changed records are persisted through the usual validation pipeline.
     *
     * @param siteId       identifier of the site
     * @param assignments  desired set of installations (may be {@code null} for none)
     * @return list of persisted installations after the operation
     */
    @Transactional
    public List<InstalledSoftware> replaceAssignmentsForSite(UUID siteId, List<InstalledSoftware> assignments) {
        Objects.requireNonNull(siteId, "siteId must not be null");

        List<InstalledSoftware> desired = assignments == null ? List.of() : assignments;
        List<InstalledSoftware> existing = repo.findBySite(siteId);
        Map<UUID, InstalledSoftware> existingById = existing.stream()
                .filter(isw -> isw.getInstalledSoftwareID() != null)
                .collect(Collectors.toMap(InstalledSoftware::getInstalledSoftwareID, isw -> isw));

        Set<UUID> processed = new HashSet<>();
        List<InstalledSoftware> stored = new ArrayList<>();

        for (InstalledSoftware incoming : desired) {
            Objects.requireNonNull(incoming, "assignment must not be null");
            incoming.setSiteID(siteId);
            if (incoming.getSoftwareID() == null) {
                throw new IllegalArgumentException("SoftwareID is required for installed software assignments");
            }

            UUID identifier = incoming.getInstalledSoftwareID();
            if (identifier == null) {
                stored.add(createOrUpdateInstalledSoftware(incoming));
                continue;
            }

            if (!existingById.containsKey(identifier)) {
                throw new IllegalArgumentException("InstalledSoftwareID " + identifier + " is not associated with site " + siteId);
            }

            InstalledSoftware patch = new InstalledSoftware();
            patch.setSiteID(siteId);
            patch.setSoftwareID(incoming.getSoftwareID());
            patch.setStatus(incoming.getStatus());
            patch.setOfferedDate(incoming.getOfferedDate());
            patch.setInstalledDate(incoming.getInstalledDate());
            patch.setRejectedDate(incoming.getRejectedDate());
            patch.setOutdatedDate(incoming.getOutdatedDate());

            Optional<InstalledSoftware> updated = updateInstalledSoftware(identifier, patch);
            updated.ifPresent(stored::add);
            processed.add(identifier);
        }

        for (InstalledSoftware stale : existing) {
            UUID id = stale.getInstalledSoftwareID();
            if (id != null && !processed.contains(id)) {
                deleteInstalledSoftware(id);
            }
        }

        return stored;
    }

    /**
     * Deletes an installation.
     *
     * @param id installation identifier
     */
    @Transactional
    public void deleteInstalledSoftware(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        repo.findById(id).ifPresent(isw -> {
            repo.deleteById(id);
            log.info("InstalledSoftware deleted: id={} site={} software={} status={}",
                    id, isw.getSiteID(), isw.getSoftwareID(), isw.getStatus());
            // Optionally remove the entry from Lucene once delete support exists.
        });
    }

    // ---------- Internals ----------

    /**
     * Registers the After Commit Indexing for deferred execution.
     *
     * @param isw installation record to index after the transaction commits.
     */
    private void registerAfterCommitIndexing(InstalledSoftware isw) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexToLucene(isw);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * Indexes the installation after the transaction commits.
             */
            @Override
            public void afterCommit() {
                indexToLucene(isw);
            }
        });
    }

    /**
     * Indexes an installation record in Lucene for search operations.
     *
     * @param isw installed software entity to index.
     */
    private void indexToLucene(InstalledSoftware isw) {
        try {
            lucene.indexInstalledSoftware(
                    isw.getInstalledSoftwareID() != null ? isw.getInstalledSoftwareID().toString() : null,
                    isw.getSiteID() != null ? isw.getSiteID().toString() : null,
                    isw.getSoftwareID() != null ? isw.getSoftwareID().toString() : null,
                    isw.getStatus(),
                    isw.getOfferedDate(),
                    isw.getInstalledDate(),
                    isw.getRejectedDate(),
                    isw.getOutdatedDate()
            );
            log.debug("InstalledSoftware indexed in Lucene: id={}", isw.getInstalledSoftwareID());
        } catch (Exception e) {
            log.error("Lucene indexing for InstalledSoftware {} failed", isw.getInstalledSoftwareID(), e);
        }
    }

    /**
     * Normalizes status and date fields based on the chosen status.
     *
     * @param entity installation entity to normalize.
     * @return normalized status enum.
     */
    private InstalledSoftwareStatus normalizeStatusAndDates(InstalledSoftware entity) {
        InstalledSoftwareStatus status;
        try {
            status = InstalledSoftwareStatus.from(entity.getStatus());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }

        entity.setStatus(status.dbValue());
        entity.setOfferedDate(normalizeDate(entity.getOfferedDate()));
        entity.setInstalledDate(normalizeDate(entity.getInstalledDate()));
        entity.setRejectedDate(normalizeDate(entity.getRejectedDate()));
        entity.setOutdatedDate(normalizeDate(entity.getOutdatedDate()));

        switch (status) {
            case OFFERED -> {
                entity.setInstalledDate(null);
                entity.setRejectedDate(null);
                entity.setOutdatedDate(null);
            }
            case INSTALLED -> {
                entity.setRejectedDate(null);
                entity.setOutdatedDate(null);
            }
            case REJECTED -> {
                entity.setInstalledDate(null);
                entity.setOutdatedDate(null);
            }
            case OUTDATED -> entity.setRejectedDate(null);
        }
        return status;
    }

    /**
     * Normalizes the Date to a canonical form.
     * @param value value.
     * @return the computed result.
     */
    private String normalizeDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date value: " + value, ex);
        }
    }

    /**
     * Maps the supplied input into a Overview Row representation.
     * @param row row.
     * @return the computed result.
     */
    private SiteSoftwareOverviewEntry mapOverviewRow(SiteSoftwareOverview row) {
        String normalizedStatus = row.status();
        String statusLabel = row.status();
        try {
            InstalledSoftwareStatus statusEnum = InstalledSoftwareStatus.from(row.status());
            normalizedStatus = statusEnum.dbValue();
            statusLabel = statusEnum.dbValue();
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown installed software status '{}' for installation {}", row.status(), row.installedSoftwareId());
        }

        return new SiteSoftwareOverviewEntry(
                row.installedSoftwareId(),
                row.siteId(),
                row.siteName(),
                row.softwareId(),
                row.softwareName(),
                row.release(),
                row.revision(),
                normalizedStatus,
                statusLabel,
                row.offeredDate(),
                row.installedDate(),
                row.rejectedDate(),
                row.outdatedDate()
        );
    }
}
