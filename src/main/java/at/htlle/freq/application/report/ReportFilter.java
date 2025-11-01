package at.htlle.freq.application.report;

import java.time.LocalDate;

public record ReportFilter(
        ReportType type,
        String period,
        LocalDate from,
        LocalDate to,
        String query,
        String variantCode
) {
    public boolean hasDateRange() {
        return from != null && to != null;
    }
}
