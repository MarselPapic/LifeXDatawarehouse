package at.htlle.freq.application.report;

import java.util.Locale;

/**
 * Supported report categories for the Reports UI.
 */
public enum ReportType {
    DIFFERENCE("Difference", "Vergleich von Soll- und Ist-Konfigurationen"),
    MAINTENANCE("Maintenance", "Geplante und laufende Wartungsfenster"),
    CONFIGURATION("Configuration", "Konfigurationsübersicht über Standorte"),
    INVENTORY("Inventory", "Inventarverteilung nach Asset-Klasse");

    private final String label;
    private final String description;

    ReportType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

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

    public String toLowerCase() {
        return name().toLowerCase(Locale.ROOT);
    }
}
