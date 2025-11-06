package at.htlle.freq.application.report;

/**
 * Describes a KPI with key, display label, value, and optional hint.
 */
public record Kpi(
        String key,
        String label,
        String value,
        String hint
) {}
