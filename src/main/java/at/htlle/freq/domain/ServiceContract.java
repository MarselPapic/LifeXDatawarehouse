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

    public ServiceContract() {}
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

    public UUID getContractID() { return contractID; }
    public void setContractID(UUID contractID) { this.contractID = contractID; }

    public UUID getAccountID() { return accountID; }
    public void setAccountID(UUID accountID) { this.accountID = accountID; }

    public UUID getProjectID() { return projectID; }
    public void setProjectID(UUID projectID) { this.projectID = projectID; }

    public UUID getSiteID() { return siteID; }
    public void setSiteID(UUID siteID) { this.siteID = siteID; }

    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
