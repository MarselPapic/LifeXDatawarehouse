package at.htlle.freq.application.report;

import java.time.LocalDate;

/**
 * Transportiert Filterkriterien für den Reportabruf.
 */
public record ReportFilter(
        ReportType type,
        String period,
        LocalDate from,
        LocalDate to,
        String query,
        String variantCode,
        String installStatus
) {
    /**
     * Prüft, ob sowohl ein Start- als auch ein Enddatum gesetzt wurde.
     *
     * @return {@code true}, wenn beide Datumswerte vorhanden sind
     */
    public boolean hasDateRange() {
        return from != null && to != null;
    }
}
