package at.htlle.freq.application.report;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

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
 * Aggregates reporting data for software support end dates and renders exports.
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
     * Generates a report for software support end dates based on the provided filter.
     *
     * @param filter report filter including date range and preset metadata
     * @return prepared report data including table rows
     */
    public ReportResponse getReport(ReportFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("""
            SELECT
                a.AccountName,
                p.ProjectName,
                s.SiteName,
                sw.Name,
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
        """);

        if (filter.from() != null) {
            sql.append(" AND sw.SupportEndDate >= :from");
            params.put("from", filter.from());
        }
        if (filter.to() != null) {
            sql.append(" AND sw.SupportEndDate <= :to");
            params.put("to", filter.to());
        }

        sql.append(" AND LOWER(isw.Status) = 'installed'");

        sql.append(" ORDER BY sw.SupportEndDate, sw.Name, sw.Release, sw.Revision");

        List<Map<String, Object>> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> mapSupportRow(rs));

        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("project", "Project", "left"),
                new ReportColumn("site", "Site", "left"),
                new ReportColumn("installStatus", "Install status", "left"),
                new ReportColumn("name", "Software", "left"),
                new ReportColumn("release", "Release", "left"),
                new ReportColumn("revision", "Revision", "left"),
                new ReportColumn("supportStart", "Support start", "left"),
                new ReportColumn("supportEnd", "Support end", "left"),
                new ReportColumn("daysRemaining", "Days remaining", "right")
        );

        ReportTable table = new ReportTable(columns, freezeRows(rows),
                "Installed software support end dates", "No deployments found for the selected range.");

        return new ReportResponse(table, DATE_TIME_FMT.format(LocalDateTime.now()));
    }

    /**
     * Serialises a report into CSV format.
     *
     * @param report previously generated report
     * @return CSV content as a string
     */
    public String renderCsv(ReportResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Report;Support end dates\n");
        sb.append("Generated at;").append(report.generatedAt()).append('\n');
        sb.append('\n');

        List<ReportColumn> columns = report.table().columns();
        if (columns.isEmpty()) {
            return sb.toString();
        }

        sb.append(columns.stream().map(col -> escapeCsv(col.label())).collect(java.util.stream.Collectors.joining(";"))).append('\n');
        for (Map<String, Object> row : report.table().rows()) {
            String line = columns.stream()
                    .map(col -> escapeCsv(valueToString(row.get(col.key()))))
                    .collect(java.util.stream.Collectors.joining(";"));
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * Maps the supplied input into a Support Row representation.
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

    /**
     * Formats the Date for presentation or export.
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

    /**
     * Escapes the CSV for safe output.
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
