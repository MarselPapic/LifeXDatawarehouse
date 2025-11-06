package at.htlle.freq.application.report;

import java.util.Locale;

/**
 * Supported report categories for the reporting front end.
 */
public enum ReportType {
    DIFFERENCE("Difference", "Comparison of target and actual configurations"),
    MAINTENANCE("Maintenance", "Planned and ongoing maintenance windows"),
    CONFIGURATION("Configuration", "Configuration overview by site"),
    INVENTORY("Inventory", "Asset distribution by category");

    private final String label;
    private final String description;

    ReportType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * Returns the localized label.
     *
     * @return display name of the report type
     */
    public String label() {
        return label;
    }

    /**
     * Returns the description used for UI tooltips.
     *
     * @return explanatory description text
     */
    public String description() {
        return description;
    }

    /**
     * Resolves the matching report type from a request parameter.
     *
     * @param value name or label
     * @return matching report type
     * @throws IllegalArgumentException for unknown values
     */
    public static ReportType fromParameter(String value) {
        if (value == null || value.isBlank()) {
            return DIFFERENCE;
        }
        String normalized = value.trim();
        for (ReportType type : values()) {
            if (type.name().equalsIgnoreCase(normalized) || type.label.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown report type: " + value);
    }

    /**
     * Returns the enum name in lower case (e.g. for CSS or template keys).
     *
     * @return enum name in lower case
     */
    public String toLowerCase() {
        return name().toLowerCase(Locale.ROOT);
    }
}
