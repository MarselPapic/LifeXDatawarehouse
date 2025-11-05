package at.htlle.freq.application.report;

import java.util.Locale;

/**
 * Unterstützte Report-Kategorien für das Reporting-Frontend.
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

    /**
     * Liefert das lokalisierte Label.
     *
     * @return Anzeigename des Report-Typs
     */
    public String label() {
        return label;
    }

    /**
     * Liefert die Beschreibung für UI-Tooltips.
     *
     * @return erläuternder Beschreibungstext
     */
    public String description() {
        return description;
    }

    /**
     * Ermittelt den passenden Report-Typ aus einem Request-Parameter.
     *
     * @param value Name oder Label
     * @return passender Report-Typ
     * @throws IllegalArgumentException bei unbekannten Werten
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
     * Gibt den Enum-Namen in Kleinschreibung zurück (z. B. für CSS- oder Template-Keys).
     *
     * @return kleingeschriebener Enum-Name
     */
    public String toLowerCase() {
        return name().toLowerCase(Locale.ROOT);
    }
}
