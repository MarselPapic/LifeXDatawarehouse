package at.htlle.freq.application.report;

/**
 * Beschreibt einen auswählbaren Report-Typ für das Frontend.
 */
public record ReportTypeInfo(
        String id,
        String label,
        String description
) {}
