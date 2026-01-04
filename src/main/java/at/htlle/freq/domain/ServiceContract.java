package at.htlle.freq.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Captures maintenance or rollout agreements between a customer
 * {@link Account}, a {@link Project}, and optionally a specific {@link Site}.
 * Contract data stores the commercial identifiers and schedules to coordinate
 * service delivery.
 */
public class ServiceContract {
    private UUID contractID;
    private UUID accountID;
    private UUID projectID;
    private UUID siteID;
    private String contractNumber;
    private String status;    // Planned / Approved / InProgress / Done / Canceled
    private LocalDate startDate;
    private LocalDate endDate;

    /**
     * Creates a new ServiceContract instance.
     */
    public ServiceContract() {}
    /**
     * Creates a new ServiceContract instance and initializes it with the provided values.
     * @param contractID contract identifier.
     * @param accountID account identifier.
     * @param projectID project identifier.
     * @param siteID site identifier.
     * @param contractNumber contract number.
     * @param status status.
     * @param startDate start date.
     * @param endDate end date.
     */
    public ServiceContract(UUID contractID, UUID accountID, UUID projectID, UUID siteID,
                           String contractNumber, String status, LocalDate startDate, LocalDate endDate) {
        this.contractID = contractID;
        this.accountID = accountID;
        this.projectID = projectID;
        this.siteID = siteID;
        this.contractNumber = contractNumber;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Returns the Contract ID value held by this instance.
     * @return the Contract ID value.
     */
    public UUID getContractID() { return contractID; }
    /**
     * Sets the Contract ID value and updates the current state.
     * @param contractID contract identifier.
     */
    public void setContractID(UUID contractID) { this.contractID = contractID; }

    /**
     * Returns the Account ID value held by this instance.
     * @return the Account ID value.
     */
    public UUID getAccountID() { return accountID; }
    /**
     * Sets the Account ID value and updates the current state.
     * @param accountID account identifier.
     */
    public void setAccountID(UUID accountID) { this.accountID = accountID; }

    /**
     * Returns the Project ID value held by this instance.
     * @return the Project ID value.
     */
    public UUID getProjectID() { return projectID; }
    /**
     * Sets the Project ID value and updates the current state.
     * @param projectID project identifier.
     */
    public void setProjectID(UUID projectID) { this.projectID = projectID; }

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
     * Returns the Contract Number value held by this instance.
     * @return the Contract Number value.
     */
    public String getContractNumber() { return contractNumber; }
    /**
     * Sets the Contract Number value and updates the current state.
     * @param contractNumber contract number.
     */
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }

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
     * Returns the Start Date value held by this instance.
     * @return the Start Date value.
     */
    public LocalDate getStartDate() { return startDate; }
    /**
     * Sets the Start Date value and updates the current state.
     * @param startDate start date.
     */
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    /**
     * Returns the End Date value held by this instance.
     * @return the End Date value.
     */
    public LocalDate getEndDate() { return endDate; }
    /**
     * Sets the End Date value and updates the current state.
     * @param endDate end date.
     */
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
