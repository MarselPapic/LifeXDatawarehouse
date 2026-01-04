package at.htlle.freq.web.dto;

import at.htlle.freq.domain.InstalledSoftware;

import java.util.UUID;

/**
 * DTO that captures installed software assignments during site upsert operations.
 */
public record SiteSoftwareAssignmentDto(
        UUID installedSoftwareId,
        UUID softwareId,
        String status,
        String offeredDate,
        String installedDate,
        String rejectedDate,
        String outdatedDate
) {
    /**
     * Maps this DTO to an {@link InstalledSoftware} domain entity.
     *
     * @param siteId site identifier to associate with the installation.
     * @return populated {@link InstalledSoftware} entity.
     */
    public InstalledSoftware toDomain(UUID siteId) {
        InstalledSoftware entity = new InstalledSoftware();
        entity.setInstalledSoftwareID(installedSoftwareId);
        entity.setSiteID(siteId);
        entity.setSoftwareID(softwareId);
        entity.setStatus(status);
        entity.setOfferedDate(offeredDate);
        entity.setInstalledDate(installedDate);
        entity.setRejectedDate(rejectedDate);
        entity.setOutdatedDate(outdatedDate);
        return entity;
    }
}
