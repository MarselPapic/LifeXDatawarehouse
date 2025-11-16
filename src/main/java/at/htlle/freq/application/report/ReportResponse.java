package at.htlle.freq.application.report;

/**
 * Contains the aggregated data of a generated support end report.
 */
public record ReportResponse(
        ReportTable table,
        String generatedAt
) {}
