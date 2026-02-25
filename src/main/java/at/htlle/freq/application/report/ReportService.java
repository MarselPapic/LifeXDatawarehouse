package at.htlle.freq.application.report;

import at.htlle.freq.domain.ArchiveState;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
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

/**
 * Aggregates reporting data and renders exports.
 */
@Service
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final PdfReportRenderer pdfReportRenderer;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final NumberFormat INT_FMT = NumberFormat.getIntegerInstance(Locale.GERMANY);

    /**
     * Creates the service with the required JDBC template.
     *
     * @param jdbc data access component for reporting queries
     * @param pdfReportRenderer renderer for PDF export output
     */
    public ReportService(NamedParameterJdbcTemplate jdbc, PdfReportRenderer pdfReportRenderer) {
        this.jdbc = jdbc;
        this.pdfReportRenderer = pdfReportRenderer;
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
     * Renders the report as a simple PDF document.
     *
     * @param report report payload.
     * @return PDF bytes.
     */
    public byte[] renderPdf(ReportResponse report) {
        return pdfReportRenderer.render(report);
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
            ExcelStyles styles = createExcelStyles(workbook);
            int rowIdx = 0;

            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleLabel = titleRow.createCell(0);
            titleLabel.setCellValue("Report");
            titleLabel.setCellStyle(styles.labelStyle());
            Cell titleValue = titleRow.createCell(1);
            titleValue.setCellValue(report.table() != null ? valueToString(report.table().caption()) : "");
            titleValue.setCellStyle(styles.titleStyle());

            Row generatedRow = sheet.createRow(rowIdx++);
            Cell generatedLabel = generatedRow.createCell(0);
            generatedLabel.setCellValue("Generated at");
            generatedLabel.setCellStyle(styles.labelStyle());
            Cell generatedValue = generatedRow.createCell(1);
            generatedValue.setCellValue(valueToString(report.generatedAt()));
            generatedValue.setCellStyle(styles.textStyle());

            if (report.summary() != null) {
                rowIdx = writeSummaryRow(sheet, rowIdx, "Total deployments", report.summary().totalDeployments(), styles);
                rowIdx = writeSummaryRow(sheet, rowIdx, "Overdue", report.summary().overdue(), styles);
                rowIdx = writeSummaryRow(sheet, rowIdx, "Due in 30 days", report.summary().dueIn30Days(), styles);
                rowIdx = writeSummaryRow(sheet, rowIdx, "Due in 90 days", report.summary().dueIn90Days(), styles);
                rowIdx = writeSummaryRow(sheet, rowIdx, "Distinct accounts", report.summary().distinctAccounts(), styles);
                rowIdx = writeSummaryRow(sheet, rowIdx, "Distinct sites", report.summary().distinctSites(), styles);
            }

            rowIdx++;
            if (report.table() != null && report.table().columns() != null && !report.table().columns().isEmpty()) {
                List<ReportColumn> columns = report.table().columns();
                int headerRowIndex = rowIdx;
                Row headerRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < columns.size(); i++) {
                    Cell headerCell = headerRow.createCell(i);
                    headerCell.setCellValue(valueToString(columns.get(i).label()));
                    headerCell.setCellStyle(styles.headerStyle());
                }

                for (Map<String, Object> data : report.table().rows()) {
                    Row row = sheet.createRow(rowIdx++);
                    for (int i = 0; i < columns.size(); i++) {
                        ReportColumn column = columns.get(i);
                        writeDataCell(row, i, data.get(column.key()), column, styles);
                    }
                }

                int lastRowIndex = Math.max(headerRowIndex, rowIdx - 1);
                sheet.createFreezePane(0, headerRowIndex + 1);
                sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, lastRowIndex, 0, columns.size() - 1));
                autoSizeColumns(sheet, columns.size());
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

    private int writeSummaryRow(Sheet sheet, int rowIdx, String label, long value, ExcelStyles styles) {
        Row row = sheet.createRow(rowIdx++);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.labelStyle());
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.numberStyle());
        return rowIdx;
    }

    private void writeDataCell(Row row, int columnIndex, Object rawValue, ReportColumn column, ExcelStyles styles) {
        Cell cell = row.createCell(columnIndex);
        if (column != null && "right".equalsIgnoreCase(column.align())) {
            Long numeric = parseGermanInteger(rawValue);
            if (numeric != null) {
                cell.setCellValue(numeric);
                cell.setCellStyle(styles.numberStyle());
                return;
            }
            cell.setCellValue(valueToString(rawValue));
            cell.setCellStyle(styles.rightAlignedStyle());
            return;
        }
        cell.setCellValue(valueToString(rawValue));
        cell.setCellStyle(styles.textStyle());
    }

    private Long parseGermanInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return INT_FMT.parse(raw).longValue();
        } catch (ParseException ex) {
            return null;
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        int maxWidth = 45 * 256;
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
    }

    private ExcelStyles createExcelStyles(Workbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 12);

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setVerticalAlignment(VerticalAlignment.TOP);

        CellStyle labelStyle = workbook.createCellStyle();
        labelStyle.setFont(boldFont);
        labelStyle.setVerticalAlignment(VerticalAlignment.TOP);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(boldFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.LEFT);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(headerStyle);

        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setAlignment(HorizontalAlignment.LEFT);
        textStyle.setVerticalAlignment(VerticalAlignment.TOP);
        textStyle.setWrapText(true);
        applyBorder(textStyle);

        CellStyle rightAlignedStyle = workbook.createCellStyle();
        rightAlignedStyle.cloneStyleFrom(textStyle);
        rightAlignedStyle.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.cloneStyleFrom(rightAlignedStyle);
        numberStyle.setDataFormat(dataFormat.getFormat("#,##0"));

        return new ExcelStyles(titleStyle, labelStyle, headerStyle, textStyle, rightAlignedStyle, numberStyle);
    }

    private void applyBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
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
     * Formats values for presentation/export, with numeric formatting for numbers.
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

    private record ExcelStyles(
            CellStyle titleStyle,
            CellStyle labelStyle,
            CellStyle headerStyle,
            CellStyle textStyle,
            CellStyle rightAlignedStyle,
            CellStyle numberStyle
    ) {}
}
