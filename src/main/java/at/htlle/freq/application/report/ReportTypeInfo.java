package at.htlle.freq.application.report;

/**
 * Describes a selectable report type for the front end.
 */
public record ReportTypeInfo(
        String id,
        String label,
        String description
) {}
