package at.htlle.freq.application.report;

/**
 * Beschreibt eine Deployment-Variante als auswählbare Option.
 */
public record VariantOption(
        String code,
        String label,
        boolean active
) {}
