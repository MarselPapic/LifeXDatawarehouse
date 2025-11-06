package at.htlle.freq.application.report;

import java.time.LocalDate;

/**
 * Transports filter criteria for report retrieval.
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
     * Checks whether both a start and an end date have been provided.
     *
     * @return {@code true} when both date values are present
     */
    public boolean hasDateRange() {
        return from != null && to != null;
    }
}
