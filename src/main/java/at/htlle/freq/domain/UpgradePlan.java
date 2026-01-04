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

    /**
     * Creates a new UpgradePlan instance.
     */
    public UpgradePlan() {}
    /**
     * Creates a new UpgradePlan instance and initializes it with the provided values.
     * @param upgradePlanID upgrade plan identifier.
     * @param siteID site identifier.
     * @param softwareID software identifier.
     * @param plannedWindowStart planned window start.
     * @param plannedWindowEnd planned window end.
     * @param status status.
     * @param createdAt created at.
     * @param createdBy created by.
     */
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

    /**
     * Returns the Upgrade Plan ID value held by this instance.
     * @return the Upgrade Plan ID value.
     */
    public UUID getUpgradePlanID() { return upgradePlanID; }
    /**
     * Sets the Upgrade Plan ID value and updates the current state.
     * @param upgradePlanID upgrade plan identifier.
     */
    public void setUpgradePlanID(UUID upgradePlanID) { this.upgradePlanID = upgradePlanID; }

    /**
     * Returns the Site ID value held by this instance.
     * @return the Site ID value.
     */
    public UUID getSiteID() { return siteID; }
    /**
     * Sets the Site ID value and updates the current state.
     * @param siteID site identifier.
     */
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    /**
     * Returns the Software ID value held by this instance.
     * @return the Software ID value.
     */
    public UUID getSoftwareID() { return softwareID; }
    /**
     * Sets the Software ID value and updates the current state.
     * @param softwareID software identifier.
     */
    public void setSoftwareID(UUID softwareID) { this.softwareID = softwareID; }

    /**
     * Returns the Planned Window Start value held by this instance.
     * @return the Planned Window Start value.
     */
    public LocalDate getPlannedWindowStart() { return plannedWindowStart; }
    /**
     * Sets the Planned Window Start value and updates the current state.
     * @param plannedWindowStart planned window start.
     */
    public void setPlannedWindowStart(LocalDate plannedWindowStart) { this.plannedWindowStart = plannedWindowStart; }

    /**
     * Returns the Planned Window End value held by this instance.
     * @return the Planned Window End value.
     */
    public LocalDate getPlannedWindowEnd() { return plannedWindowEnd; }
    /**
     * Sets the Planned Window End value and updates the current state.
     * @param plannedWindowEnd planned window end.
     */
    public void setPlannedWindowEnd(LocalDate plannedWindowEnd) { this.plannedWindowEnd = plannedWindowEnd; }

    /**
     * Returns the Status value held by this instance.
     * @return the Status value.
     */
    public String getStatus() { return status; }
    /**
     * Sets the Status value and updates the current state.
     * @param status status.
     */
    public void setStatus(String status) { this.status = status; }

    /**
     * Returns the Created At value held by this instance.
     * @return the Created At value.
     */
    public LocalDate getCreatedAt() { return createdAt; }
    /**
     * Sets the Created At value and updates the current state.
     * @param createdAt created at.
     */
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }

    /**
     * Returns the Created By value held by this instance.
     * @return the Created By value.
     */
    public String getCreatedBy() { return createdBy; }
    /**
     * Sets the Created By value and updates the current state.
     * @param createdBy created by.
     */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
