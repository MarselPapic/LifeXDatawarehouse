package at.htlle.freq.domain;

import java.util.Arrays;

/**
 * Enumeration of lifecycle states tracked for {@link InstalledSoftware}
 * records. The enum exposes both a database value and a localized label and
 * provides helper methods for conversions used in the ingestion pipeline.
 */
public enum InstalledSoftwareStatus {
    OFFERED("Offered", "Angeboten"),
    INSTALLED("Installed", "Installiert"),
    REJECTED("Rejected", "Abgelehnt"),
    OUTDATED("Outdated", "Veraltet");

    private final String dbValue;
    private final String label;

    InstalledSoftwareStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    /**
     * Returns the database value used for persistence.
     *
     * @return database value string.
     */
    public String dbValue() {
        return dbValue;
    }

    /**
     * Returns the localized display label.
     *
     * @return label text.
     */
    public String label() {
        return label;
    }

    /**
     * Parses a status value into the matching enum constant.
     *
     * @param value status value from requests or storage.
     * @return matching enum, defaults to {@link #OFFERED} when blank.
     */
    public static InstalledSoftwareStatus from(String value) {
        if (value == null || value.isBlank()) {
            return OFFERED;
        }
        return Arrays.stream(values())
                .filter(status -> status.dbValue.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported installed software status: " + value));
    }
}
