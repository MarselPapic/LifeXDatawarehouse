package at.htlle.freq.domain;

import org.springframework.stereotype.Component;

@Component
public class AccountFactory {
    /**
     * Creates a new {@link Account} aggregate with the provided master data. The
     * identifier is left {@code null} so that the persistence layer can assign a
     * UUID when the account is stored.
     *
     * @return a transient account instance ready for persistence
     */
    public Account create(String accountName,
                          String contactName,
                          String contactEmail,
                          String contactPhone,
                          String vatNumber,
                          String country) {
        return new Account(
                null, // DB vergibt UUID
                accountName,
                contactName,
                contactEmail,
                contactPhone,
                vatNumber,
                country
        );
    }
}
