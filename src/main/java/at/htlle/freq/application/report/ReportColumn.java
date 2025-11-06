package at.htlle.freq.application.report;

/**
 * Defines a column configuration for tabular report data.
 */
public record ReportColumn(
        String key,
        String label,
        String align
) {}
