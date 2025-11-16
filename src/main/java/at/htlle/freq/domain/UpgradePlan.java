package at.htlle.freq.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a scheduled upgrade activity for a {@link Site} and a target
 * {@link Software} release. The plan captures the maintenance window,
 * workflow status, and auditing information for traceability.
 */
public class UpgradePlan {
    private UUID upgradePlanID;
    private UUID siteID;
    private UUID softwareID;
    private LocalDate plannedWindowStart;
    private LocalDate plannedWindowEnd;
    private String status;     // Planned / Approved / InProgress / Done / Canceled
    private LocalDate createdAt;
    private String createdBy;

    public UpgradePlan() {}
    public UpgradePlan(UUID upgradePlanID, UUID siteID, UUID softwareID, LocalDate plannedWindowStart,
                       LocalDate plannedWindowEnd, String status, LocalDate createdAt, String createdBy) {
        this.upgradePlanID = upgradePlanID;
        this.siteID = siteID;
        this.softwareID = softwareID;
        this.plannedWindowStart = plannedWindowStart;
        this.plannedWindowEnd = plannedWindowEnd;
        this.status = status;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public UUID getUpgradePlanID() { return upgradePlanID; }
    public void setUpgradePlanID(UUID upgradePlanID) { this.upgradePlanID = upgradePlanID; }

    public UUID getSiteID() { return siteID; }
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    public UUID getSoftwareID() { return softwareID; }
    public void setSoftwareID(UUID softwareID) { this.softwareID = softwareID; }

    public LocalDate getPlannedWindowStart() { return plannedWindowStart; }
    public void setPlannedWindowStart(LocalDate plannedWindowStart) { this.plannedWindowStart = plannedWindowStart; }

    public LocalDate getPlannedWindowEnd() { return plannedWindowEnd; }
    public void setPlannedWindowEnd(LocalDate plannedWindowEnd) { this.plannedWindowEnd = plannedWindowEnd; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
