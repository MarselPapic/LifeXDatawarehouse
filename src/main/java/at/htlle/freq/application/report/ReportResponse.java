package at.htlle.freq.application.report;

import java.util.List;

public record ReportResponse(
        ReportType type,
        List<Kpi> kpis,
        ReportTable table,
        List<ChartSlice> chart,
        String chartTitle,
        String generatedAt
) {}
