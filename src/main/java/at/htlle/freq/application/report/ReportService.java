package at.htlle.freq.application.report;

import at.htlle.freq.domain.ArchiveState;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates reporting data and renders exports.
 */
@Service
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final NumberFormat INT_FMT = NumberFormat.getIntegerInstance(Locale.GERMANY);

    /**
     * Creates the service with the required JDBC template.
     *
     * @param jdbc data access component for reporting queries
     */
    public ReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Generates the default support-end report.
     *
     * @param filter report filter including date range and preset metadata
     * @return prepared report data including table rows
     */
    public ReportResponse getReport(ReportFilter filter) {
        return getReport(ReportView.SUPPORT_END, filter);
    }

    /**
     * Generates a report for the selected view.
     *
     * @param view selected report view.
     * @param filter report filter.
     * @return report payload with table and KPI summary.
     */
    public ReportResponse getReport(ReportView view, ReportFilter filter) {
        ReportTable table = switch (view) {
            case SUPPORT_END -> buildSupportEndTable(filter);
            case LIFECYCLE_STATUS -> buildLifecycleStatusTable(filter);
            case ACCOUNT_RISK -> buildAccountRiskTable(filter);
        };
        ReportSummary summary = getSummary(filter);
        return new ReportResponse(table, summary, DATE_TIME_FMT.format(LocalDateTime.now()));
    }

    /**
     * Aggregates KPI metrics for installed software support risks.
     *
     * @param filter report filter.
     * @return summary metrics.
     */
    public ReportSummary getSummary(ReportFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS totalDeployments,
                    SUM(CASE WHEN sw.SupportEndDate < CURRENT_DATE THEN 1 ELSE 0 END) AS overdue,
                    SUM(CASE WHEN sw.SupportEndDate BETWEEN CURRENT_DATE AND DATEADD('DAY', 29, CURRENT_DATE) THEN 1 ELSE 0 END) AS dueIn30Days,
                    SUM(CASE WHEN sw.SupportEndDate BETWEEN CURRENT_DATE AND DATEADD('DAY', 89, CURRENT_DATE) THEN 1 ELSE 0 END) AS dueIn90Days,
                    COUNT(DISTINCT a.AccountID) AS distinctAccounts,
                    COUNT(DISTINCT s.SiteID) AS distinctSites
                FROM InstalledSoftware isw
                JOIN Software sw ON sw.SoftwareID = isw.SoftwareID
                JOIN Site s ON s.SiteID = isw.SiteID
                JOIN Project p ON p.ProjectID = s.ProjectID
                JOIN Account a ON a.AccountID = p.AccountID
                WHERE sw.SupportEndDate IS NOT NULL
                  AND LOWER(isw.Status) = 'installed'
                """);
        appendArchiveStateClause(sql, filter, "isw", "sw", "s", "p", "a");
        appendSupportEndRangeClause(sql, params, filter);

        Map<String, Object> row = jdbc.queryForMap(sql.toString(), params);
        return new ReportSummary(
                toLong(row.get("totalDeployments")),
                toLong(row.get("overdue")),
                toLong(row.get("dueIn30Days")),
                toLong(row.get("dueIn90Days")),
                toLong(row.get("distinctAccounts")),
                toLong(row.get("distinctSites"))
        );
    }

    /**
     * Serialises a report into CSV format.
     *
     * @param report previously generated report
     * @return CSV content as a string
     */
    public String renderCsv(ReportResponse report) {
        StringBuilder sb = new StringBuilder();
        String caption = report.table() != null ? valueToString(report.table().caption()) : "";
        sb.append("Report;").append(escapeCsv(caption)).append('\n');
        sb.append("Generated at;").append(escapeCsv(report.generatedAt())).append('\n');

        if (report.summary() != null) {
            sb.append("Total deployments;").append(report.summary().totalDeployments()).append('\n');
            sb.append("Overdue;").append(report.summary().overdue()).append('\n');
            sb.append("Due in 30 days;").append(report.summary().dueIn30Days()).append('\n');
            sb.append("Due in 90 days;").append(report.summary().dueIn90Days()).append('\n');
            sb.append("Distinct accounts;").append(report.summary().distinctAccounts()).append('\n');
            sb.append("Distinct sites;").append(report.summary().distinctSites()).append('\n');
        }
        sb.append('\n');

        if (report.table() == null || report.table().columns().isEmpty()) {
            return sb.toString();
        }

        List<ReportColumn> columns = report.table().columns();
        sb.append(columns.stream().map(col -> escapeCsv(col.label())).collect(Collectors.joining(";"))).append('\n');
        for (Map<String, Object> row : report.table().rows()) {
            String line = columns.stream()
                    .map(col -> escapeCsv(valueToString(row.get(col.key()))))
                    .collect(Collectors.joining(";"));
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * Renders the report as a simple PDF document.
     *
     * @param report report payload.
     * @return PDF bytes.
     */
    public byte[] renderPdf(ReportResponse report) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float margin = 42f;
            float leading = 14f;
            float yStart = page.getMediaBox().getHeight() - margin;
            float y = yStart;

            PDPageContentStream stream = new PDPageContentStream(doc, page);
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 10);
            stream.newLineAtOffset(margin, yStart);

            for (String line : buildPdfLines(report)) {
                if (y - leading < margin) {
                    stream.endText();
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    stream = new PDPageContentStream(doc, page);
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 10);
                    stream.newLineAtOffset(margin, yStart);
                    y = yStart;
                }
                stream.showText(sanitizePdf(line));
                stream.newLineAtOffset(0, -leading);
                y -= leading;
            }

            stream.endText();
            stream.close();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render PDF report", ex);
        }
    }

    /**
     * Renders the report as XLSX.
     *
     * @param report report payload.
     * @return XLSX bytes.
     */
    public byte[] renderExcel(ReportResponse report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("LifeX Report");
            int rowIdx = 0;

            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.createCell(0).setCellValue("Report");
            titleRow.createCell(1).setCellValue(report.table() != null ? valueToString(report.table().caption()) : "");

            Row generatedRow = sheet.createRow(rowIdx++);
            generatedRow.createCell(0).setCellValue("Generated at");
            generatedRow.createCell(1).setCellValue(valueToString(report.generatedAt()));

            if (report.summary() != null) {
                rowIdx = writeSummaryRow(sheet, rowIdx, "Total deployments", report.summary().totalDeployments());
                rowIdx = writeSummaryRow(sheet, rowIdx, "Overdue", report.summary().overdue());
                rowIdx = writeSummaryRow(sheet, rowIdx, "Due in 30 days", report.summary().dueIn30Days());
                rowIdx = writeSummaryRow(sheet, rowIdx, "Due in 90 days", report.summary().dueIn90Days());
                rowIdx = writeSummaryRow(sheet, rowIdx, "Distinct accounts", report.summary().distinctAccounts());
                rowIdx = writeSummaryRow(sheet, rowIdx, "Distinct sites", report.summary().distinctSites());
            }

            rowIdx++;
            if (report.table() != null && report.table().columns() != null) {
                List<ReportColumn> columns = report.table().columns();
                Row headerRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < columns.size(); i++) {
                    headerRow.createCell(i).setCellValue(valueToString(columns.get(i).label()));
                }

                for (Map<String, Object> data : report.table().rows()) {
                    Row row = sheet.createRow(rowIdx++);
                    for (int i = 0; i < columns.size(); i++) {
                        ReportColumn column = columns.get(i);
                        row.createCell(i).setCellValue(valueToString(data.get(column.key())));
                    }
                }

                for (int i = 0; i < columns.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render XLSX report", ex);
        }
    }

    private ReportTable buildSupportEndTable(ReportFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("""
            SELECT
                a.AccountName,
                p.ProjectName,
                s.SiteName,
                sw.Name,
                sw.Version,
                sw.Release,
                sw.Revision,
                sw.SupportStartDate,
                sw.SupportEndDate,
                isw.Status
            FROM InstalledSoftware isw
            JOIN Software sw ON sw.SoftwareID = isw.SoftwareID
            JOIN Site s ON s.SiteID = isw.SiteID
            JOIN Project p ON p.ProjectID = s.ProjectID
            JOIN Account a ON a.AccountID = p.AccountID
            WHERE sw.SupportEndDate IS NOT NULL
              AND LOWER(isw.Status) = 'installed'
        """);
        appendArchiveStateClause(sql, filter, "isw", "sw", "s", "p", "a");
        appendSupportEndRangeClause(sql, params, filter);
        sql.append(" ORDER BY sw.SupportEndDate, sw.Name, sw.Version, sw.Release, sw.Revision");

        List<Map<String, Object>> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> mapSupportRow(rs));
        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("project", "Project", "left"),
                new ReportColumn("site", "Site", "left"),
                new ReportColumn("installStatus", "Install status", "left"),
                new ReportColumn("name", "Software", "left"),
                new ReportColumn("version", "Version", "left"),
                new ReportColumn("release", "Release", "left"),
                new ReportColumn("revision", "Revision", "left"),
                new ReportColumn("supportStart", "Support start", "left"),
                new ReportColumn("supportEnd", "Support end", "left"),
                new ReportColumn("daysRemaining", "Days remaining", "right")
        );
        return new ReportTable(columns, freezeRows(rows),
                ReportView.SUPPORT_END.title(), "No deployments found for the selected range.");
    }

    private ReportTable buildLifecycleStatusTable(ReportFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(sw.SupportPhase, 'Unknown') AS SupportPhase,
                    COALESCE(isw.Status, 'Unknown') AS InstallStatus,
                    COUNT(*) AS Deployments,
                    MIN(sw.SupportEndDate) AS NextSupportEnd,
                    MAX(sw.SupportEndDate) AS LastSupportEnd
                FROM InstalledSoftware isw
                JOIN Software sw ON sw.SoftwareID = isw.SoftwareID
                JOIN Site s ON s.SiteID = isw.SiteID
                JOIN Project p ON p.ProjectID = s.ProjectID
                JOIN Account a ON a.AccountID = p.AccountID
                WHERE sw.SupportEndDate IS NOT NULL
                """);
        appendArchiveStateClause(sql, filter, "isw", "sw", "s", "p", "a");
        appendSupportEndRangeClause(sql, params, filter);
        sql.append("""
                 GROUP BY COALESCE(sw.SupportPhase, 'Unknown'), COALESCE(isw.Status, 'Unknown')
                 ORDER BY COUNT(*) DESC, SupportPhase, InstallStatus
                """);

        List<Map<String, Object>> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("supportPhase", rs.getString("SupportPhase"));
            row.put("installStatus", rs.getString("InstallStatus"));
            row.put("deployments", INT_FMT.format(rs.getLong("Deployments")));
            row.put("nextSupportEnd", formatSqlDate(rs, "NextSupportEnd"));
            row.put("lastSupportEnd", formatSqlDate(rs, "LastSupportEnd"));
            return row;
        });

        List<ReportColumn> columns = List.of(
                new ReportColumn("supportPhase", "Support phase", "left"),
                new ReportColumn("installStatus", "Install status", "left"),
                new ReportColumn("deployments", "Deployments", "right"),
                new ReportColumn("nextSupportEnd", "Next support end", "left"),
                new ReportColumn("lastSupportEnd", "Last support end", "left")
        );
        return new ReportTable(columns, freezeRows(rows),
                ReportView.LIFECYCLE_STATUS.title(), "No status rows found for the selected range.");
    }

    private ReportTable buildAccountRiskTable(ReportFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.AccountName,
                    COUNT(*) AS Deployments,
                    SUM(CASE WHEN sw.SupportEndDate < CURRENT_DATE THEN 1 ELSE 0 END) AS Overdue,
                    SUM(CASE WHEN sw.SupportEndDate BETWEEN CURRENT_DATE AND DATEADD('DAY', 29, CURRENT_DATE) THEN 1 ELSE 0 END) AS DueIn30,
                    SUM(CASE WHEN sw.SupportEndDate BETWEEN CURRENT_DATE AND DATEADD('DAY', 89, CURRENT_DATE) THEN 1 ELSE 0 END) AS DueIn90,
                    MIN(sw.SupportEndDate) AS NextSupportEnd
                FROM InstalledSoftware isw
                JOIN Software sw ON sw.SoftwareID = isw.SoftwareID
                JOIN Site s ON s.SiteID = isw.SiteID
                JOIN Project p ON p.ProjectID = s.ProjectID
                JOIN Account a ON a.AccountID = p.AccountID
                WHERE sw.SupportEndDate IS NOT NULL
                  AND LOWER(isw.Status) = 'installed'
                """);
        appendArchiveStateClause(sql, filter, "isw", "sw", "s", "p", "a");
        appendSupportEndRangeClause(sql, params, filter);
        sql.append("""
                 GROUP BY a.AccountID, a.AccountName
                 ORDER BY DueIn30 DESC, DueIn90 DESC, Deployments DESC, a.AccountName
                """);

        List<Map<String, Object>> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("account", rs.getString("AccountName"));
            row.put("deployments", INT_FMT.format(rs.getLong("Deployments")));
            row.put("overdue", INT_FMT.format(rs.getLong("Overdue")));
            row.put("dueIn30", INT_FMT.format(rs.getLong("DueIn30")));
            row.put("dueIn90", INT_FMT.format(rs.getLong("DueIn90")));
            row.put("nextSupportEnd", formatSqlDate(rs, "NextSupportEnd"));
            return row;
        });

        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("deployments", "Deployments", "right"),
                new ReportColumn("overdue", "Overdue", "right"),
                new ReportColumn("dueIn30", "Due in 30 days", "right"),
                new ReportColumn("dueIn90", "Due in 90 days", "right"),
                new ReportColumn("nextSupportEnd", "Next support end", "left")
        );
        return new ReportTable(columns, freezeRows(rows),
                ReportView.ACCOUNT_RISK.title(), "No account risk rows found for the selected range.");
    }

    private int writeSummaryRow(Sheet sheet, int rowIdx, String label, long value) {
        Row row = sheet.createRow(rowIdx++);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIdx;
    }

    private void appendSupportEndRangeClause(StringBuilder sql, Map<String, Object> params, ReportFilter filter) {
        if (filter == null) {
            return;
        }
        if (filter.from() != null) {
            sql.append(" AND sw.SupportEndDate >= :from");
            params.put("from", filter.from());
        }
        if (filter.to() != null) {
            sql.append(" AND sw.SupportEndDate <= :to");
            params.put("to", filter.to());
        }
    }

    private void appendArchiveStateClause(StringBuilder sql, ReportFilter filter, String... aliases) {
        ArchiveState archiveState = filter == null ? ArchiveState.ACTIVE : filter.effectiveArchiveState();
        if (archiveState == ArchiveState.ALL || aliases == null || aliases.length == 0) {
            return;
        }
        if (archiveState == ArchiveState.ACTIVE) {
            for (String alias : aliases) {
                sql.append(" AND ").append(alias).append(".IsArchived = FALSE");
            }
            return;
        }
        List<String> archivedChecks = new LinkedList<>();
        for (String alias : aliases) {
            archivedChecks.add(alias + ".IsArchived = TRUE");
        }
        sql.append(" AND (").append(String.join(" OR ", archivedChecks)).append(")");
    }

    /**
     * Maps the supplied input into a Support Row representation.
     *
     * @param rs rs.
     * @return the computed result.
     * @throws SQLException if the operation cannot be completed.
     */
    private Map<String, Object> mapSupportRow(ResultSet rs) throws SQLException {
        LocalDate supportStart = getLocalDate(rs, "SupportStartDate");
        LocalDate supportEnd = getLocalDate(rs, "SupportEndDate");
        Long daysRemaining = supportEnd != null ? ChronoUnit.DAYS.between(LocalDate.now(), supportEnd) : null;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("account", rs.getString("AccountName"));
        row.put("project", rs.getString("ProjectName"));
        row.put("site", rs.getString("SiteName"));
        row.put("installStatus", rs.getString("Status"));
        row.put("name", rs.getString("Name"));
        row.put("version", rs.getString("Version"));
        row.put("release", rs.getString("Release"));
        row.put("revision", rs.getString("Revision"));
        row.put("supportStart", supportStart != null ? formatDate(supportStart) : "n/a");
        row.put("supportEnd", supportEnd != null ? formatDate(supportEnd) : "n/a");
        row.put("daysRemaining", daysRemaining != null ? INT_FMT.format(daysRemaining) : "n/a");
        return row;
    }

    /**
     * Copies rows into a mutable list to avoid downstream side effects.
     *
     * @param rows rows to copy.
     * @return mutable list containing the same row maps.
     */
    private List<Map<String, Object>> freezeRows(List<Map<String, Object>> rows) {
        return new LinkedList<>(rows);
    }

    /**
     * Retrieves a {@link LocalDate} from a SQL date column.
     *
     * @param rs result set.
     * @param column column name to read.
     * @return date value or null when the column is SQL NULL.
     * @throws SQLException when JDBC access fails.
     */
    private LocalDate getLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value != null ? value.toLocalDate() : null;
    }

    private String formatSqlDate(ResultSet rs, String column) throws SQLException {
        LocalDate value = getLocalDate(rs, column);
        return value != null ? formatDate(value) : "n/a";
    }

    /**
     * Formats the Date for presentation or export.
     *
     * @param date date.
     * @return the computed result.
     */
    private String formatDate(LocalDate date) {
        return DATE_FMT.format(date);
    }

    /**
     * Formats values for CSV output, with numeric formatting for numbers.
     *
     * @param value value to format.
     * @return string representation suitable for export.
     */
    private String valueToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return INT_FMT.format(number);
        }
        return value.toString();
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString().trim());
    }

    private List<String> buildPdfLines(ReportResponse report) {
        List<String> lines = new LinkedList<>();
        String caption = report.table() != null ? valueToString(report.table().caption()) : "";
        lines.add("LifeX Report: " + caption);
        lines.add("Generated at: " + valueToString(report.generatedAt()));
        lines.add("");

        if (report.summary() != null) {
            lines.add("Summary");
            lines.add("Total deployments: " + report.summary().totalDeployments());
            lines.add("Overdue: " + report.summary().overdue());
            lines.add("Due in 30 days: " + report.summary().dueIn30Days());
            lines.add("Due in 90 days: " + report.summary().dueIn90Days());
            lines.add("Distinct accounts: " + report.summary().distinctAccounts());
            lines.add("Distinct sites: " + report.summary().distinctSites());
            lines.add("");
        }

        if (report.table() == null || report.table().columns().isEmpty()) {
            return lines;
        }

        List<ReportColumn> columns = report.table().columns();
        lines.add(columns.stream().map(ReportColumn::label).collect(Collectors.joining(" | ")));
        for (Map<String, Object> row : report.table().rows()) {
            String line = columns.stream()
                    .map(col -> valueToString(row.get(col.key())))
                    .collect(Collectors.joining(" | "));
            lines.add(line);
        }
        return lines;
    }

    private String sanitizePdf(String raw) {
        if (raw == null || raw.isBlank()) {
            return " ";
        }
        String compact = raw.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        String ascii = compact.replaceAll("[^\\x20-\\x7E]", "?");
        if (ascii.length() > 120) {
            return ascii.substring(0, 120);
        }
        return ascii;
    }

    /**
     * Escapes the CSV for safe output.
     *
     * @param value value.
     * @return the computed result.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(";") || value.contains("\n") || value.contains("\"");
        String escaped = value.replace("\"", "\"\"");
        if (needsQuotes) {
            return '"' + escaped + '"';
        }
        return escaped;
    }
}
