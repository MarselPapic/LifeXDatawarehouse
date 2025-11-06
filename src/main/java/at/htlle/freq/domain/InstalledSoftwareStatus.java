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
    REJECTED("Rejected", "Abgelehnt");

    private final String dbValue;
    private final String label;

    InstalledSoftwareStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

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
