package at.htlle.freq.application.report;

/**
 * Describes a deployment variant as a selectable option.
 */
public record VariantOption(
        String code,
        String label,
        boolean active
) {}
