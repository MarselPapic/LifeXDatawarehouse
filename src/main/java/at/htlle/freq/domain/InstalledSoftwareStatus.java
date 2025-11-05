package at.htlle.freq.domain;

import java.util.Arrays;

public enum InstalledSoftwareStatus {
    ACTIVE("Active", "Aktiv"),
    PENDING("Pending", "Geplant"),
    RETIRED("Retired", "Außer Betrieb");

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
            return ACTIVE;
        }
        return Arrays.stream(values())
                .filter(status -> status.dbValue.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported installed software status: " + value));
    }
}
