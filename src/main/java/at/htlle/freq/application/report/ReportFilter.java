package at.htlle.freq.application.report;

import at.htlle.freq.domain.ArchiveState;

import java.time.LocalDate;

/**
 * Transports filter criteria for support end reports.
 */
public record ReportFilter(
        LocalDate from,
        LocalDate to,
        String preset,
        ArchiveState archiveState
) {
    /**
     * Checks whether both a start and an end date have been provided.
     *
     * @return {@code true} when both date values are present
     */
    public boolean hasDateRange() {
        return from != null && to != null;
    }

    /**
     * Returns the effective archive state, defaulting to ACTIVE.
     *
     * @return effective archive state.
     */
    public ArchiveState effectiveArchiveState() {
        return archiveState == null ? ArchiveState.ACTIVE : archiveState;
    }
}
