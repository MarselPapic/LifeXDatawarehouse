package at.htlle.freq.domain;

import org.springframework.stereotype.Component;

/**
 * Factory responsible for creating Account instances.
 */
@Component
public class AccountFactory {
    /**
     * Creates a new {@link Account} aggregate that represents a customer
     * account with the provided master data. The identifier remains
     * {@code null} so the persistence layer can write the database-generated
     * {@code AccountID} during persistence.
     *
     * @return a transient customer account ready for persistence
     */
    public Account create(String accountName,
                          String contactName,
                          String contactEmail,
                          String contactPhone,
                          String vatNumber,
                          String country) {
        return new Account(
                null, // Database assigns the AccountID column value
                accountName,
                contactName,
                contactEmail,
                contactPhone,
                vatNumber,
                country
        );
    }
}
