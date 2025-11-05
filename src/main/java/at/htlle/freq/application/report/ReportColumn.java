package at.htlle.freq.application.report;

/**
 * Definiert eine Spaltenkonfiguration für tabellarische Report-Daten.
 */
public record ReportColumn(
        String key,
        String label,
        String align
) {}
