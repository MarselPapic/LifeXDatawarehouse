package at.htlle.freq.application.report;

/**
 * KPI summary for installed software support risk.
 */
public record ReportSummary(
        long totalDeployments,
        long overdue,
        long dueIn30Days,
        long dueIn90Days,
        long distinctAccounts,
        long distinctSites
) {}
