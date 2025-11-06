package at.htlle.freq.application.report;

import java.util.List;
import java.util.Map;

/**
 * Bundles selectable options for the reporting front end.
 */
public record ReportOptions(
        List<ReportTypeInfo> types,
        List<VariantOption> variants,
        Map<String, String> periods,
        List<StatusOption> installStatuses
) {}
