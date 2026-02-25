package at.htlle.freq.application.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders report data into a print-friendly PDF using HTML/CSS templates.
 */
@Component
public class PdfReportRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfReportRenderer.class);
    private static final float PAGE_MARGIN_PT = 36f;
    private static final float PORTRAIT_CONTENT_WIDTH_PT = PDRectangle.A4.getWidth() - (PAGE_MARGIN_PT * 2f);
    private static final float DEFAULT_COLUMN_WIDTH_PT = 84f;

    private static final Map<String, Float> COLUMN_WIDTH_HINTS_PT = Map.ofEntries(
            Map.entry("account", 118f),
            Map.entry("project", 108f),
            Map.entry("site", 98f),
            Map.entry("installStatus", 74f),
            Map.entry("name", 130f),
            Map.entry("version", 58f),
            Map.entry("release", 52f),
            Map.entry("revision", 52f),
            Map.entry("supportStart", 76f),
            Map.entry("supportEnd", 76f),
            Map.entry("daysRemaining", 56f),
            Map.entry("supportPhase", 108f),
            Map.entry("deployments", 62f),
            Map.entry("nextSupportEnd", 80f),
            Map.entry("lastSupportEnd", 80f),
            Map.entry("overdue", 62f),
            Map.entry("dueIn30", 68f),
            Map.entry("dueIn90", 68f)
    );

    private final String template;
    private final String stylesheet;

    public PdfReportRenderer() {
        this.template = loadResource("report/pdf/report-template.html");
        this.stylesheet = loadResource("report/pdf/report.css");
    }

    /**
     * Renders a report response as PDF bytes.
     *
     * @param report report payload.
     * @return rendered PDF as bytes.
     */
    public byte[] render(ReportResponse report) {
        long startedAt = System.nanoTime();
        ReportTable table = report != null ? report.table() : null;
        boolean landscape = useLandscape(table);
        String orientation = landscape ? "landscape" : "portrait";
        String title = table != null ? safeString(table.caption()) : "LifeX Report";

        try {
            String html = buildHtml(report, orientation);
            byte[] data = renderPdf(html);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            int rowCount = table != null && table.rows() != null ? table.rows().size() : 0;
            int columnCount = table != null && table.columns() != null ? table.columns().size() : 0;
            log.info("Rendered report PDF: title='{}', orientation={}, rows={}, columns={}, sizeBytes={}, durationMs={}",
                    title, orientation, rowCount, columnCount, data.length, durationMs);
            log.debug("PDF render details: generatedAt='{}'", report != null ? safeString(report.generatedAt()) : "");
            return data;
        } catch (Exception ex) {
            String message = "Failed to render PDF report (title=" + title + ", orientation=" + orientation + ")";
            log.error(message, ex);
            throw new IllegalStateException(message, ex);
        }
    }

    private byte[] renderPdf(String html) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    private boolean useLandscape(ReportTable table) {
        if (table == null || table.columns() == null || table.columns().isEmpty()) {
            return false;
        }
        double sum = 0d;
        for (ReportColumn column : table.columns()) {
            sum += widthHint(column);
        }
        return sum > PORTRAIT_CONTENT_WIDTH_PT;
    }

    private double widthHint(ReportColumn column) {
        if (column == null || column.key() == null) {
            return DEFAULT_COLUMN_WIDTH_PT;
        }
        return COLUMN_WIDTH_HINTS_PT.getOrDefault(column.key(), DEFAULT_COLUMN_WIDTH_PT);
    }

    private String buildHtml(ReportResponse report, String pageOrientation) {
        ReportTable table = report != null ? report.table() : null;
        ReportSummary summary = report != null ? report.summary() : null;

        Map<String, String> values = new LinkedHashMap<>();
        values.put("{{reportCss}}", stylesheet);
        values.put("{{pageOrientation}}", safeString(pageOrientation));
        values.put("{{reportTitle}}", escapeHtml(table != null ? table.caption() : "LifeX Report"));
        values.put("{{generatedAt}}", escapeHtml(report != null ? report.generatedAt() : ""));
        values.put("{{summarySection}}", buildSummarySection(summary));
        values.put("{{tableSection}}", buildTableSection(table));

        String html = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            html = html.replace(entry.getKey(), entry.getValue());
        }
        return html;
    }

    private String buildSummarySection(ReportSummary summary) {
        if (summary == null) {
            return "<p class=\"summary-empty\">No summary data available.</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<section class=\"summary\" aria-label=\"Summary\">");
        sb.append(summaryMetric("Total deployments", formatInteger(summary.totalDeployments()), "metric-primary"));
        sb.append(summaryMetric("Overdue", formatInteger(summary.overdue()), "metric-danger"));
        sb.append(summaryMetric("Due in 30 days", formatInteger(summary.dueIn30Days()), "metric-warning"));
        sb.append(summaryMetric("Due in 90 days", formatInteger(summary.dueIn90Days()), "metric-watch"));
        sb.append(summaryMetric("Distinct accounts", formatInteger(summary.distinctAccounts()), "metric-neutral"));
        sb.append(summaryMetric("Distinct sites", formatInteger(summary.distinctSites()), "metric-neutral"));
        sb.append("</section>");
        return sb.toString();
    }

    private String summaryMetric(String label, String value, String modifier) {
        StringBuilder sb = new StringBuilder();
        sb.append("<article class=\"summary-card ").append(modifier).append("\">");
        sb.append("<p class=\"summary-label\">").append(escapeHtml(label)).append("</p>");
        sb.append("<p class=\"summary-value\">").append(escapeHtml(value)).append("</p>");
        sb.append("</article>");
        return sb.toString();
    }

    private String buildTableSection(ReportTable table) {
        if (table == null || table.columns() == null || table.columns().isEmpty()) {
            return "<p class=\"table-empty\">No table definition available.</p>";
        }
        List<ReportColumn> columns = table.columns();
        if (table.rows() == null || table.rows().isEmpty()) {
            String message = safeString(table.emptyMessage());
            if (message.isBlank()) {
                message = "No rows found for the selected period.";
            }
            return "<p class=\"table-empty\">" + escapeHtml(message) + "</p>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"report-table\">");
        sb.append("<colgroup>");
        double sum = columns.stream().mapToDouble(this::widthHint).sum();
        for (ReportColumn column : columns) {
            double widthPct = (widthHint(column) / sum) * 100d;
            sb.append("<col style=\"width:")
                    .append(String.format(Locale.ROOT, "%.2f", widthPct))
                    .append("%\" />");
        }
        sb.append("</colgroup>");

        sb.append("<thead><tr>");
        for (ReportColumn column : columns) {
            sb.append("<th class=\"")
                    .append(alignmentClass(column))
                    .append("\">")
                    .append(escapeHtml(safeString(column.label())))
                    .append("</th>");
        }
        sb.append("</tr></thead>");

        sb.append("<tbody>");
        for (Map<String, Object> row : table.rows()) {
            sb.append("<tr>");
            for (ReportColumn column : columns) {
                Object rawValue = row != null ? row.get(column.key()) : null;
                String value = rawValue == null ? "" : rawValue.toString();
                sb.append("<td class=\"")
                        .append(alignmentClass(column))
                        .append("\">")
                        .append(escapeHtml(value))
                        .append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody>");
        sb.append("</table>");
        return sb.toString();
    }

    private String alignmentClass(ReportColumn column) {
        String align = column != null && column.align() != null ? column.align().trim().toLowerCase(Locale.ROOT) : "";
        if ("right".equals(align)) {
            return "align-right";
        }
        if ("center".equals(align)) {
            return "align-center";
        }
        return "align-left";
    }

    private String formatInteger(long value) {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(value);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String escaped = raw;
        escaped = escaped.replace("&", "&amp;");
        escaped = escaped.replace("<", "&lt;");
        escaped = escaped.replace(">", "&gt;");
        escaped = escaped.replace("\"", "&quot;");
        escaped = escaped.replace("'", "&#39;");
        return escaped;
    }

    private String loadResource(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing PDF template resource: " + classpathLocation);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read resource: " + classpathLocation, ex);
        }
    }
}
