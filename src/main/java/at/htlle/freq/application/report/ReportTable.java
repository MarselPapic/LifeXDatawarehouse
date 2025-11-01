package at.htlle.freq.application.report;

import java.util.List;
import java.util.Map;

public record ReportTable(
        List<ReportColumn> columns,
        List<Map<String, Object>> rows,
        String caption,
        String emptyMessage
) {}
