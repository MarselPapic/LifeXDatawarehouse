package at.htlle.freq.application.report;

/**
 * Repräsentiert einen Segmentwert für ein Tortendiagramm inklusive optionalem Hinweis.
 */
public record ChartSlice(
        String label,
        double value,
        String hint
) {}
