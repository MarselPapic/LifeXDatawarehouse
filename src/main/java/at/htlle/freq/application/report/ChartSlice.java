package at.htlle.freq.application.report;

/**
 * Represents a pie chart segment value including an optional hint.
 */
public record ChartSlice(
        String label,
        double value,
        String hint
) {}
