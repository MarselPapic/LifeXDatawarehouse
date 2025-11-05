package at.htlle.freq.application.report;

/**
 * Beschreibt eine Kennzahl mit Schlüssel, Anzeige-Label, Wert und optionalem Hinweis.
 */
public record Kpi(
        String key,
        String label,
        String value,
        String hint
) {}
