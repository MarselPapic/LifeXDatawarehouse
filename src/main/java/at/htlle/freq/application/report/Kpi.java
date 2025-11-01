package at.htlle.freq.application.report;

public record Kpi(
        String key,
        String label,
        String value,
        String hint
) {}
