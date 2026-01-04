package at.htlle.freq.domain;

import java.util.UUID;

/**
 * Represents a customer account that owns {@link Project projects} and
 * {@link Site sites} inside the data warehouse. The account stores the
 * organization-wide master data such as VAT number and country as well as the
 * primary contact person responsible for coordinating roll-outs and service
 * contracts.
 */
public class Account {
    private UUID accountID;
    private String accountName;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String vatNumber;
    private String country;

    /**
     * Creates a new Account instance.
     */
    public Account() {}
    /**
     * Creates an account with all attributes set.
     *
     * @param accountID account identifier.
     * @param accountName account name.
     * @param contactName primary contact name.
     * @param contactEmail primary contact email.
     * @param contactPhone primary contact phone.
     * @param vatNumber VAT number.
     * @param country country name or code.
     */
    public Account(UUID accountID, String accountName, String contactName, String contactEmail,
                   String contactPhone, String vatNumber, String country) {
        this.accountID = accountID;
        this.accountName = accountName;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.vatNumber = vatNumber;
        this.country = country;
    }

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
     * Returns the Account Name value held by this instance.
     * @return the Account Name value.
     */
    public String getAccountName() { return accountName; }
    /**
     * Sets the Account Name value and updates the current state.
     * @param accountName account name.
     */
    public void setAccountName(String accountName) { this.accountName = accountName; }

    /**
     * Returns the Contact Name value held by this instance.
     * @return the Contact Name value.
     */
    public String getContactName() { return contactName; }
    /**
     * Sets the Contact Name value and updates the current state.
     * @param contactName contact name.
     */
    public void setContactName(String contactName) { this.contactName = contactName; }

    /**
     * Returns the Contact Email value held by this instance.
     * @return the Contact Email value.
     */
    public String getContactEmail() { return contactEmail; }
    /**
     * Sets the Contact Email value and updates the current state.
     * @param contactEmail contact email.
     */
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    /**
     * Returns the Contact Phone value held by this instance.
     * @return the Contact Phone value.
     */
    public String getContactPhone() { return contactPhone; }
    /**
     * Sets the Contact Phone value and updates the current state.
     * @param contactPhone contact phone.
     */
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    /**
     * Returns the Vat Number value held by this instance.
     * @return the Vat Number value.
     */
    public String getVatNumber() { return vatNumber; }
    /**
     * Sets the Vat Number value and updates the current state.
     * @param vatNumber vat number.
     */
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }

    /**
     * Returns the Country value held by this instance.
     * @return the Country value.
     */
    public String getCountry() { return country; }
    /**
     * Sets the Country value and updates the current state.
     * @param country country.
     */
    public void setCountry(String country) { this.country = country; }
}
