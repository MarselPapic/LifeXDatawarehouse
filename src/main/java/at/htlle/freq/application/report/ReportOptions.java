package at.htlle.freq.application.report;

import java.util.List;
import java.util.Map;

public record ReportOptions(
        List<ReportTypeInfo> types,
        List<VariantOption> variants,
        Map<String, String> periods
) {}
