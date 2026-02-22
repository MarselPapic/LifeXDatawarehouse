package at.htlle.freq.application.report;

/**
 * Contains the aggregated data of a generated support end report.
 */
public record ReportResponse(
        ReportTable table,
        ReportSummary summary,
        String generatedAt
) {
    public ReportResponse(ReportTable table, String generatedAt) {
        this(table, null, generatedAt);
    }
}
