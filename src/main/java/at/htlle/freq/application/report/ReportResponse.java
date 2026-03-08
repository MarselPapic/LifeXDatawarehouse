package at.htlle.freq.application.report;

/**
 * Contains the aggregated data of a generated support end report.
 */
public record ReportResponse(
        ReportTable table,
        ReportSummary summary,
        String generatedAt
) {
    /**
     * Creates a response without a summary section.
     *
     * @param table generated report table.
     * @param generatedAt timestamp string for report generation.
     */
    public ReportResponse(ReportTable table, String generatedAt) {
        this(table, null, generatedAt);
    }
}
