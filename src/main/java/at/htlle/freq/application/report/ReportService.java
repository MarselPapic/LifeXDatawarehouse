package at.htlle.freq.application.report;

import at.htlle.freq.domain.InstalledSoftwareStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates reporting data, prepares it for the UI and exports, and renders CSV/PDF outputs.
 */
@Service
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final NumberFormat INT_FMT = NumberFormat.getIntegerInstance(Locale.GERMANY);
    private static final NumberFormat PERCENT_FMT;

    static {
        PERCENT_FMT = NumberFormat.getPercentInstance(Locale.GERMANY);
        // Percentages appear with max. one decimal to match finance reporting rules.
        PERCENT_FMT.setMinimumFractionDigits(0);
        PERCENT_FMT.setMaximumFractionDigits(1);
    }

    /**
     * Creates the service with the required JDBC template.
     *
     * @param jdbc data access component for reporting queries
     */
    public ReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Loads the selectable report filter options.
     * <p>
     * Reads variants from {@code DeploymentVariant} and augments them with {@link ReportType}
     * definitions as well as {@link InstalledSoftwareStatus} values to drive the frontend UI.
     * </p>
     */
    public ReportOptions getOptions() {
        List<VariantOption> variants = jdbc.query(
                "SELECT VariantCode, VariantName, IsActive FROM DeploymentVariant ORDER BY VariantName",
                (rs, rowNum) -> new VariantOption(
                        rs.getString("VariantCode"),
                        rs.getString("VariantName"),
                        rs.getBoolean("IsActive")
                )
        );

        List<ReportTypeInfo> types = Arrays.stream(ReportType.values())
                .map(t -> new ReportTypeInfo(t.name(), t.label(), t.description()))
                .toList();

        List<StatusOption> installStatuses = Arrays.stream(InstalledSoftwareStatus.values())
                .map(status -> new StatusOption(status.dbValue(), status.label()))
                .toList();

        return new ReportOptions(types, variants, defaultPeriods(), installStatuses);
    }

    private Map<String, String> defaultPeriods() {
        Map<String, String> periods = new LinkedHashMap<>();
        periods.put("last7", "Last 7 days");
        periods.put("last30", "Last 30 days");
        periods.put("quarter", "This quarter");
        periods.put("custom", "Custom");
        return periods;
    }

    /**
     * Generates a report based on the provided filter criteria.
     *
     * @param filter report filter including type, period, and additional parameters
     * @return prepared report data including table and KPIs
     * Dispatches to a specialised builder based on the requested {@link ReportType}.
     * <p>
     * Each builder hits dedicated tables (e.g. {@code InstalledSoftware}, {@code UpgradePlan},
     * {@code Server}/{@code Clients} or inventory aggregates) and applies the filter's optional
     * query text, variant selection, status constraint and date range.
     * </p>
     */
    public ReportResponse getReport(ReportFilter filter) {
        return switch (filter.type()) {
            case DIFFERENCE -> buildDifferenceReport(filter);
            case MAINTENANCE -> buildMaintenanceReport(filter);
            case CONFIGURATION -> buildConfigurationReport(filter);
            case INVENTORY -> buildInventoryReport(filter);
        };
    }

    /**
     * Serialises a report into CSV format.
     *
     * @param report previously generated report
     * @return CSV content as a string
     * Converts a {@link ReportResponse} table into the semicolon-separated CSV export.
     * <p>
     * Keeps the column layout defined by the builder and escapes values so that controlling can
     * import the file into SAP/Excel without lossy conversions.
     * </p>
     */
    public String renderCsv(ReportResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Report;").append(report.type().label()).append('\n');
        sb.append("Generated at;").append(report.generatedAt()).append('\n');
        sb.append('\n');

        List<ReportColumn> columns = report.table().columns();
        if (columns.isEmpty()) {
            return sb.toString();
        }

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
     * Renders a report as PDF using PDFBox.
     *
     * @param report previously generated report
     * @return byte array of the PDF document
     * Renders the headline KPIs, table snippet and chart summary of a report into an A4 PDF.
     * <p>
     * The PDF writer enforces the reporting guideline that only the first 45 table rows should be
     * shown; additional data is referenced via the CSV export hint.
     * </p>
     */
    public byte[] renderPdf(ReportResponse report) {
        try (PDDocument document = new PDDocument(); PdfPageWriter writer = new PdfPageWriter(document)) {
            writer.writeLine(PDType1Font.HELVETICA_BOLD, 16, "LifeX Report – " + report.type().label());
            writer.writeLine(PDType1Font.HELVETICA, 10, "Generated on " + report.generatedAt());
            writer.blankLine();

            writer.writeLine(PDType1Font.HELVETICA_BOLD, 12, "KPIs");
            if (report.kpis().isEmpty()) {
                writer.writeLine(PDType1Font.HELVETICA, 10, "No KPIs available.");
            } else {
                for (Kpi kpi : report.kpis()) {
                    String hint = (kpi.hint() != null && !kpi.hint().isBlank()) ? " (" + kpi.hint() + ")" : "";
                    writer.writeLine(PDType1Font.HELVETICA, 10, "• " + kpi.label() + ": " + kpi.value() + hint);
                }
            }

            writer.blankLine();
            writer.writeLine(PDType1Font.HELVETICA_BOLD, 12, report.table().caption());
            if (report.table().rows().isEmpty()) {
                writer.writeLine(PDType1Font.HELVETICA, 10, report.table().emptyMessage());
            } else {
                String header = report.table().columns().stream()
                        .map(ReportColumn::label)
                        .collect(Collectors.joining(" | "));
                writer.writeLine(PDType1Font.HELVETICA, 10, header);
                writer.writeLine(PDType1Font.HELVETICA, 10, "-".repeat(Math.min(header.length(), 100)));

                int printed = 0;
                for (Map<String, Object> row : report.table().rows()) {
                    String rowLine = report.table().columns().stream()
                            .map(col -> valueToString(row.get(col.key())))
                            .collect(Collectors.joining(" | "));
                    writer.writeLine(PDType1Font.HELVETICA, 10, rowLine);
                    printed++;
                    if (printed >= 45) {
                        // Hard limit ensures the PDF stays within the 45-row specification for printouts.
                        writer.writeLine(PDType1Font.HELVETICA_OBLIQUE, 9, "… more records available in the CSV export …");
                        break;
                    }
                }
            }

            if (!report.chart().isEmpty()) {
                writer.blankLine();
                writer.writeLine(PDType1Font.HELVETICA_BOLD, 12, report.chartTitle());
                for (ChartSlice slice : report.chart()) {
                    writer.writeLine(PDType1Font.HELVETICA, 10,
                            "• " + slice.label() + ": " + formatInt((long) slice.value()));
                }
            }

            writer.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to render PDF export", e);
        }
    }

    /**
     * Compiles the target-versus-actual comparison by analysing installed software versus the latest releases.
     * <p>
     * Joins {@code InstalledSoftware}, {@code Site}, {@code Project}, {@code DeploymentVariant},
     * {@code Software} plus helper CTEs for planned upgrades and allows filtering by variant,
     * install status, free-text query and {@code Project.CreateDateTime} range.
     * </p>
     */
    private ReportResponse buildDifferenceReport(ReportFilter filter) {
        LocalDate now = LocalDate.now();
        Map<String, Object> params = new LinkedHashMap<>();

        StringBuilder sql = new StringBuilder("""
            WITH latest AS (
                SELECT Name, MAX(SupportStartDate) AS max_start
                FROM Software
                GROUP BY Name
            ),
            latest_details AS (
                SELECT s.Name,
                       s.Release AS target_release,
                       s.Revision AS target_revision,
                       s.SupportEndDate AS target_support_end
                FROM Software s
                JOIN latest l ON l.Name = s.Name
                WHERE (s.SupportStartDate = l.max_start OR (l.max_start IS NULL AND s.SupportEndDate IS NOT NULL))
            ),
            next_plan AS (
                SELECT SiteID, SoftwareID, MIN(PlannedWindowStart) AS next_window
                FROM UpgradePlan
                GROUP BY SiteID, SoftwareID
            )
            SELECT
                p.ProjectName,
                p.ProjectSAPID,
                p.CreateDateTime,
                dv.VariantCode,
                s.SiteName,
                sw.Name AS software_name,
                sw.Release AS current_release,
                sw.Revision AS current_revision,
                sw.SupportEndDate AS support_end,
                ins.Status AS install_status,
                ld.target_release,
                ld.target_revision,
                ld.target_support_end,
                np.next_window
            FROM InstalledSoftware ins
            JOIN Site s ON s.SiteID = ins.SiteID
            JOIN Project p ON p.ProjectID = s.ProjectID
            JOIN DeploymentVariant dv ON dv.VariantID = p.DeploymentVariantID
            JOIN Software sw ON sw.SoftwareID = ins.SoftwareID
            LEFT JOIN latest_details ld ON ld.Name = sw.Name
            LEFT JOIN next_plan np ON np.SiteID = ins.SiteID AND np.SoftwareID = ins.SoftwareID
            WHERE 1=1
        """);

        applyQueryFilter(filter, params, sql, "p", "s");
        if (filter.variantCode() != null && !filter.variantCode().isBlank()) {
            sql.append(" AND dv.VariantCode = :variantCode");
            params.put("variantCode", filter.variantCode());
        }
        if (filter.installStatus() != null && !filter.installStatus().isBlank()) {
            sql.append(" AND ins.Status = :installStatus");
            params.put("installStatus", filter.installStatus());
        }
        if (filter.hasDateRange()) {
            sql.append(" AND p.CreateDateTime BETWEEN :from AND :to");
            params.put("from", filter.from());
            params.put("to", filter.to());
        }
        sql.append(" ORDER BY p.ProjectName, s.SiteName, sw.Name");

        List<Map<String, Object>> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> mapDifferenceRow(rs, now));

        long outdated = rows.stream().filter(r -> "Deviation".equals(r.get("compliance"))).count();
        long critical = rows.stream().filter(r -> "Critical".equals(r.get("severity"))).count();
        long expiring = rows.stream().filter(r -> "Expiry <45 days".equals(r.get("severity"))).count();
        int total = rows.size();
        double compliance = total == 0 ? 1.0 : (double) (total - outdated) / total;

        List<Kpi> kpis = List.of(
                new Kpi("records", "Records", formatInt(total), null),
                new Kpi("outdated", "Deviations", formatInt(outdated), outdated > 0 ? "review" : "OK"),
                new Kpi("critical", "Critical / Expiring", formatInt(critical + expiring), critical > 0 ? "act immediately" : "monitor"),
                new Kpi("compliance", "Compliance", formatPercent(compliance), null)
        );

        List<ReportColumn> columns = List.of(
                new ReportColumn("project", "Project", "left"),
                new ReportColumn("site", "Site", "left"),
                new ReportColumn("variant", "Variant", "left"),
                new ReportColumn("software", "Software", "left"),
                new ReportColumn("currentVersion", "Current version", "left"),
                new ReportColumn("targetVersion", "Target version", "left"),
                new ReportColumn("supportEnd", "Support end", "left"),
                new ReportColumn("status", "Installation status", "left"),
                new ReportColumn("compliance", "Match status", "left"),
                new ReportColumn("severity", "Severity", "left"),
                new ReportColumn("notes", "Note", "left")
        );

        List<String> severityOrder = List.of("Critical", "Warning", "Monitor", "Expiry <45 days", "OK");
        Map<String, Long> severityCounts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String severity = Objects.toString(row.get("severity"), "OK");
            severityCounts.merge(severity, 1L, Long::sum);
        }
        List<ChartSlice> chart = severityOrder.stream()
                .filter(severityCounts::containsKey)
                .map(label -> new ChartSlice(label, severityCounts.get(label).doubleValue(), null))
                .collect(Collectors.toList());

        ReportTable table = new ReportTable(columns, freezeRows(rows),
                "Target vs. actual comparison by site", "No deviations in the selected period.");

        return new ReportResponse(filter.type(), kpis, table, chart, "Distribution by severity",
                DATE_TIME_FMT.format(LocalDateTime.now()));
    }

    private Map<String, Object> mapDifferenceRow(ResultSet rs, LocalDate now) throws SQLException {
        String projectName = rs.getString("ProjectName");
        String projectSap = rs.getString("ProjectSAPID");
        String siteName = rs.getString("SiteName");
        String variant = rs.getString("VariantCode");
        String software = rs.getString("software_name");
        String currentRelease = rs.getString("current_release");
        String currentRevision = rs.getString("current_revision");
        String targetRelease = rs.getString("target_release");
        String targetRevision = rs.getString("target_revision");
        LocalDate supportEnd = getLocalDate(rs, "support_end");
        String installStatusRaw = rs.getString("install_status");
        InstalledSoftwareStatus installStatusEnum;
        try {
            installStatusEnum = InstalledSoftwareStatus.from(installStatusRaw);
        } catch (IllegalArgumentException ex) {
            installStatusEnum = InstalledSoftwareStatus.OFFERED;
        }
        String installStatus = installStatusEnum.dbValue();
        LocalDate targetSupport = getLocalDate(rs, "target_support_end");
        LocalDate nextWindow = getLocalDate(rs, "next_window");

        boolean upToDate = targetRelease == null ||
                (Objects.equals(currentRelease, targetRelease) && Objects.equals(currentRevision, targetRevision));
        String compliance = upToDate ? "Up to date" : "Deviation";
        String severity;
        if (!upToDate) {
            if (supportEnd != null && supportEnd.isBefore(now)) {
                severity = "Critical";
            } else if (supportEnd != null && supportEnd.isBefore(now.plusDays(45))) {
                severity = "Warning";
            } else {
                severity = "Monitor";
            }
        } else if (supportEnd != null && supportEnd.isBefore(now.plusDays(45))) {
            severity = "Expiry <45 days";
        } else {
            severity = "OK";
        }

        StringBuilder notes = new StringBuilder();
        if (upToDate) {
            notes.append("Configuration at target level");
        } else {
            notes.append("Deviation from target level");
        }
        if (nextWindow != null) {
            notes.append("; Upgrade planned from ").append(formatDate(nextWindow));
        }
        if (targetSupport != null) {
            notes.append("; Target support until ").append(formatDate(targetSupport));
        }

        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("project", projectName + " (" + projectSap + ")");
        row.put("site", siteName);
        row.put("variant", variant);
        row.put("software", software);
        row.put("currentVersion", versionString(currentRelease, currentRevision));
        row.put("targetVersion", versionString(targetRelease, targetRevision));
        row.put("supportEnd", supportEnd != null ? formatDate(supportEnd) : "—");
        row.put("status", installStatus);
        row.put("compliance", compliance);
        row.put("severity", severity);
        row.put("notes", notes.toString());
        return row;
    }

    /**
     * Builds the maintenance windows and upgrades view from the planning tables.
     * <p>
     * Uses {@code UpgradePlan} joined with {@code Site}, {@code Project}, {@code DeploymentVariant}
     * and {@code Software} to highlight overdue and upcoming tasks. Supports query, variant and
     * {@code PlannedWindowStart} date filters.
     * </p>
     */
    private ReportResponse buildMaintenanceReport(ReportFilter filter) {
        LocalDate now = LocalDate.now();
        Map<String, Object> params = new LinkedHashMap<>();

        StringBuilder sql = new StringBuilder("""
            SELECT
                p.ProjectName,
                p.ProjectSAPID,
                dv.VariantCode,
                s.SiteName,
                sw.Name AS software_name,
                up.PlannedWindowStart,
                up.PlannedWindowEnd,
                up.Status,
                sw.SupportEndDate
            FROM UpgradePlan up
            JOIN Site s ON s.SiteID = up.SiteID
            JOIN Project p ON p.ProjectID = s.ProjectID
            JOIN DeploymentVariant dv ON dv.VariantID = p.DeploymentVariantID
            JOIN Software sw ON sw.SoftwareID = up.SoftwareID
            WHERE 1=1
        """);

        applyQueryFilter(filter, params, sql, "p", "s");
        if (filter.variantCode() != null && !filter.variantCode().isBlank()) {
            sql.append(" AND dv.VariantCode = :variantCode");
            params.put("variantCode", filter.variantCode());
        }
        if (filter.hasDateRange()) {
            sql.append(" AND up.PlannedWindowStart BETWEEN :from AND :to");
            params.put("from", filter.from());
            params.put("to", filter.to());
        }
        sql.append(" ORDER BY up.PlannedWindowStart");

        int[] totals = new int[4]; // Array indices represent overdue, due soon, completed, and total counts.
        List<Map<String, Object>> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            LocalDate start = getLocalDate(rs, "PlannedWindowStart");
            LocalDate end = getLocalDate(rs, "PlannedWindowEnd");
            LocalDate supportEnd = getLocalDate(rs, "SupportEndDate");
            String status = translateStatus(rs.getString("Status"));
            long daysUntil = start != null ? ChronoUnit.DAYS.between(now, start) : 0;

            String severity;
            if ("Completed".equals(status) || "Canceled".equals(status)) {
                severity = status;
            } else if (start != null && start.isBefore(now)) {
                severity = "Overdue";
                totals[0]++;
            } else if (start != null && start.isBefore(now.plusDays(7))) {
                severity = "Due ≤7 days";
                totals[1]++;
            } else {
                severity = "Scheduled";
            }
            if ("Completed".equals(status)) {
                totals[2]++;
            }

            totals[3]++;

            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("project", rs.getString("ProjectName") + " (" + rs.getString("ProjectSAPID") + ")");
            row.put("site", rs.getString("SiteName"));
            row.put("variant", rs.getString("VariantCode"));
            row.put("software", rs.getString("software_name"));
            if (start != null && end != null) {
                row.put("window", formatDate(start) + " – " + formatDate(end));
            } else {
                row.put("window", "—");
            }
            row.put("status", status);
            row.put("days", start != null ? formatSigned(daysUntil) : "—");
            row.put("severity", severity);
            StringBuilder notes = new StringBuilder();
            if (start != null) {
                notes.append(daysUntil >= 0 ? "Starts in " + daysUntil + " days" : "Started " + Math.abs(daysUntil) + " days ago");
            }
            if (supportEnd != null) {
                if (!notes.isEmpty()) {
                    notes.append("; ");
                }
                notes.append("Support end ").append(formatDate(supportEnd));
            }
            row.put("notes", notes.isEmpty() ? "" : notes.toString());
            return row;
        });

        List<Kpi> kpis = List.of(
                new Kpi("total", "Maintenance windows", formatInt(rows.size()), null),
                new Kpi("overdue", "Overdue", formatInt(totals[0]), totals[0] > 0 ? "prioritize" : "OK"),
                new Kpi("soon", "Due ≤7 days", formatInt(totals[1]), totals[1] > 0 ? "plan" : ""),
                new Kpi("done", "Completed", formatInt(totals[2]), null)
        );

        List<ReportColumn> columns = List.of(
                new ReportColumn("project", "Project", "left"),
                new ReportColumn("site", "Site", "left"),
                new ReportColumn("variant", "Variant", "left"),
                new ReportColumn("software", "Software", "left"),
                new ReportColumn("window", "Maintenance window", "left"),
                new ReportColumn("status", "Status", "left"),
                new ReportColumn("days", "Days", "right"),
                new ReportColumn("severity", "Priority", "left"),
                new ReportColumn("notes", "Note", "left")
        );

        Map<String, Long> statusCounts = rows.stream()
                .collect(Collectors.groupingBy(r -> Objects.toString(r.get("status"), ""), LinkedHashMap::new, Collectors.counting()));
        List<ChartSlice> chart = statusCounts.entrySet().stream()
                .map(e -> new ChartSlice(e.getKey(), e.getValue().doubleValue(), null))
                .collect(Collectors.toList());

        ReportTable table = new ReportTable(columns, freezeRows(rows),
                "Maintenance windows & upgrades", "No maintenance activity in the selected period.");

        return new ReportResponse(filter.type(), kpis, table, chart, "Status overview",
                DATE_TIME_FMT.format(LocalDateTime.now()));
    }

    /**
     * Aggregates configuration KPIs per site for the configuration overview.
     * <p>
     * Summarises {@code Server}, {@code Clients}, {@code Radio}, {@code AudioDevice} and
     * {@code PhoneIntegration} counts alongside {@code Site}/{@code Project}/{@code DeploymentVariant}
     * metadata while honouring the common variant, query and date filters.
     * </p>
     */
    private ReportResponse buildConfigurationReport(ReportFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();

        StringBuilder sql = new StringBuilder("""
            WITH srv AS (
                SELECT SiteID,
                       COUNT(*) AS server_count,
                       SUM(CASE WHEN HighAvailability THEN 1 ELSE 0 END) AS ha_servers,
                       LISTAGG(DISTINCT ServerOS, ', ') WITHIN GROUP (ORDER BY ServerOS) AS server_os
                FROM Server
                GROUP BY SiteID
            ),
            cli AS (
                SELECT SiteID,
                       COUNT(*) AS client_count,
                       SUM(CASE WHEN InstallType = 'LOCAL' THEN 1 ELSE 0 END) AS local_clients,
                       SUM(CASE WHEN InstallType = 'BROWSER' THEN 1 ELSE 0 END) AS browser_clients,
                       LISTAGG(DISTINCT ClientOS, ', ') WITHIN GROUP (ORDER BY ClientOS) AS client_os
                FROM Clients
                GROUP BY SiteID
            ),
            radio AS (
                SELECT SiteID,
                       COUNT(*) AS radio_count,
                       LISTAGG(DISTINCT COALESCE(DigitalStandard, Mode), ', ') WITHIN GROUP (ORDER BY COALESCE(DigitalStandard, Mode)) AS radio_modes
                FROM Radio
                GROUP BY SiteID
            ),
            comm AS (
                SELECT c.SiteID,
                       COUNT(DISTINCT ad.AudioDeviceID) AS audio_count,
                       COUNT(DISTINCT ph.PhoneIntegrationID) AS phone_count
                FROM Clients c
                LEFT JOIN AudioDevice ad ON ad.ClientID = c.ClientID
                LEFT JOIN PhoneIntegration ph ON ph.ClientID = c.ClientID
                GROUP BY c.SiteID
            )
            SELECT
                p.ProjectName,
                p.ProjectSAPID,
                dv.VariantCode,
                s.SiteName,
                s.FireZone,
                s.TenantCount,
                COALESCE(srv.server_count, 0) AS server_count,
                COALESCE(srv.ha_servers, 0) AS ha_servers,
                COALESCE(srv.server_os, '') AS server_os,
                COALESCE(cli.client_count, 0) AS client_count,
                COALESCE(cli.local_clients, 0) AS local_clients,
                COALESCE(cli.browser_clients, 0) AS browser_clients,
                COALESCE(cli.client_os, '') AS client_os,
                COALESCE(radio.radio_count, 0) AS radio_count,
                COALESCE(radio.radio_modes, '') AS radio_modes,
                COALESCE(comm.audio_count, 0) AS audio_count,
                COALESCE(comm.phone_count, 0) AS phone_count,
                p.CreateDateTime
            FROM Site s
            JOIN Project p ON p.ProjectID = s.ProjectID
            JOIN DeploymentVariant dv ON dv.VariantID = p.DeploymentVariantID
            LEFT JOIN srv ON srv.SiteID = s.SiteID
            LEFT JOIN cli ON cli.SiteID = s.SiteID
            LEFT JOIN radio ON radio.SiteID = s.SiteID
            LEFT JOIN comm ON comm.SiteID = s.SiteID
            WHERE 1=1
        """);

        applyQueryFilter(filter, params, sql, "p", "s");
        if (filter.variantCode() != null && !filter.variantCode().isBlank()) {
            sql.append(" AND dv.VariantCode = :variantCode");
            params.put("variantCode", filter.variantCode());
        }
        if (filter.hasDateRange()) {
            sql.append(" AND p.CreateDateTime BETWEEN :from AND :to");
            params.put("from", filter.from());
            params.put("to", filter.to());
        }
        sql.append(" ORDER BY s.SiteName");

        int[] totals = new int[4]; // Array indices represent servers, high-availability servers, total clients, and local clients.
        List<Map<String, Object>> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            int serverCount = rs.getInt("server_count");
            int haServers = rs.getInt("ha_servers");
            int clientCount = rs.getInt("client_count");
            int localClients = rs.getInt("local_clients");
            int browserClients = rs.getInt("browser_clients");
            int radioCount = rs.getInt("radio_count");
            int audioCount = rs.getInt("audio_count");
            int phoneCount = rs.getInt("phone_count");

            totals[0] += serverCount;
            totals[1] += haServers;
            totals[2] += clientCount;
            totals[3] += localClients;

            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("site", rs.getString("SiteName"));
            row.put("project", rs.getString("ProjectName") + " (" + rs.getString("ProjectSAPID") + ")");
            row.put("variant", rs.getString("VariantCode"));
            row.put("servers", serverCount + " (HA " + haServers + ")");
            row.put("clients", formatInt(clientCount));
            row.put("installations", "LOCAL " + localClients + " · BROWSER " + browserClients);
            row.put("radios", radioCount == 0 ? "0" : radioCount + " (" + nullToDash(rs.getString("radio_modes")) + ")");
            row.put("communications", "Audio " + audioCount + " · Phone " + phoneCount);
            row.put("serverOs", nullToDash(rs.getString("server_os")));
            row.put("clientOs", nullToDash(rs.getString("client_os")));
            return row;
        });

        int totalSites = rows.size();
        int totalServers = totals[0];
        int totalClients = totals[2];
        int localClients = totals[3];
        int browserClients = Math.max(0, totalClients - localClients);
        double haShare = totalServers == 0 ? 0.0 : (double) totals[1] / totalServers;

        List<Kpi> kpis = List.of(
                new Kpi("sites", "Sites", formatInt(totalSites), null),
                new Kpi("servers", "Servers total", formatInt(totalServers), "HA " + formatInt(totals[1])),
                new Kpi("clients", "Clients total", formatInt(totalClients), "LOCAL " + formatInt(localClients)),
                new Kpi("haShare", "HA share", formatPercent(haShare), null)
        );

        List<ReportColumn> columns = List.of(
                new ReportColumn("site", "Site", "left"),
                new ReportColumn("project", "Project", "left"),
                new ReportColumn("variant", "Variant", "left"),
                new ReportColumn("servers", "Servers", "left"),
                new ReportColumn("clients", "Clients", "right"),
                new ReportColumn("installations", "Installations", "left"),
                new ReportColumn("radios", "Radio", "left"),
                new ReportColumn("communications", "Communication", "left"),
                new ReportColumn("serverOs", "Server OS", "left"),
                new ReportColumn("clientOs", "Client OS", "left")
        );

        List<ChartSlice> chart = List.of(
                new ChartSlice("LOCAL", localClients, null),
                new ChartSlice("BROWSER", browserClients, null)
        );

        ReportTable table = new ReportTable(columns, freezeRows(rows),
                "Configuration overview per site", "No sites in the selected period.");

        return new ReportResponse(filter.type(), kpis, table, chart, "Client installations",
                DATE_TIME_FMT.format(LocalDateTime.now()));
    }

    /**
     * Creates the asset inventory overview across all filtered sites.
     * <p>
     * Resolves site IDs via {@code Site}, {@code Project} and {@code DeploymentVariant} joins before
     * aggregating asset quantities from category tables ({@code Server}, {@code Clients},
     * {@code Radio}, {@code AudioDevice}, {@code PhoneIntegration}, {@code InstalledSoftware}).
     * Applies variant, status, query and optional date filters.
     * </p>
     */
    private ReportResponse buildInventoryReport(ReportFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder siteSql = new StringBuilder("""
            SELECT s.SiteID
            FROM Site s
            JOIN Project p ON p.ProjectID = s.ProjectID
            JOIN DeploymentVariant dv ON dv.VariantID = p.DeploymentVariantID
            WHERE 1=1
        """);

        applyQueryFilter(filter, params, siteSql, "p", "s");
        if (filter.variantCode() != null && !filter.variantCode().isBlank()) {
            siteSql.append(" AND dv.VariantCode = :variantCode");
            params.put("variantCode", filter.variantCode());
        }
        if (filter.hasDateRange()) {
            siteSql.append(" AND p.CreateDateTime BETWEEN :from AND :to");
            params.put("from", filter.from());
            params.put("to", filter.to());
        }

        List<UUID> siteIds = jdbc.query(siteSql.toString(), params,
                (rs, rowNum) -> rs.getObject("SiteID", UUID.class));

        if (siteIds.isEmpty()) {
            ReportTable table = new ReportTable(List.of(
                    new ReportColumn("type", "Category", "left"),
                    new ReportColumn("label", "Description", "left"),
                    new ReportColumn("count", "Quantity", "right"),
                    new ReportColumn("sites", "Sites", "right")
            ), List.of(), "Inventory overview", "No inventory data in the selected period.");

            List<Kpi> kpis = List.of(
                    new Kpi("assets", "Assets total", "0", null),
                    new Kpi("servers", "Servers", "0", null),
                    new Kpi("clients", "Clients", "0", null),
                    new Kpi("software", "Software packages", "0", null)
            );
            return new ReportResponse(filter.type(), kpis, table, List.of(), "Assets by category",
                    DATE_TIME_FMT.format(LocalDateTime.now()));
        }

        Map<String, Object> assetParams = new HashMap<>();
        assetParams.put("siteIds", siteIds);

        List<InventoryEntry> entries = new ArrayList<>();
        entries.addAll(queryInventory("Server",
                "SELECT COALESCE(ServerBrand, 'Unknown') AS label, COUNT(*) AS qty, COUNT(DISTINCT SiteID) AS sites " +
                        "FROM Server WHERE SiteID IN (:siteIds) GROUP BY COALESCE(ServerBrand, 'Unknown') ORDER BY label",
                assetParams));
        entries.addAll(queryInventory("Client",
                "SELECT COALESCE(ClientBrand, 'Unknown') AS label, COUNT(*) AS qty, COUNT(DISTINCT SiteID) AS sites " +
                        "FROM Clients WHERE SiteID IN (:siteIds) GROUP BY COALESCE(ClientBrand, 'Unknown') ORDER BY label",
                assetParams));
        entries.addAll(queryInventory("Radio",
                "SELECT COALESCE(RadioBrand, 'Unknown') AS label, COUNT(*) AS qty, COUNT(DISTINCT SiteID) AS sites " +
                        "FROM Radio WHERE SiteID IN (:siteIds) GROUP BY COALESCE(RadioBrand, 'Unknown') ORDER BY label",
                assetParams));
        entries.addAll(queryInventory("AudioDevice",
                "SELECT COALESCE(ad.AudioDeviceBrand, 'Unknown') AS label, COUNT(*) AS qty, COUNT(DISTINCT c.SiteID) AS sites " +
                        "FROM AudioDevice ad JOIN Clients c ON c.ClientID = ad.ClientID " +
                        "WHERE c.SiteID IN (:siteIds) GROUP BY COALESCE(ad.AudioDeviceBrand, 'Unknown') ORDER BY label",
                assetParams));
        entries.addAll(queryInventory("PhoneIntegration",
                "SELECT COALESCE(ph.PhoneBrand, 'Unknown') AS label, COUNT(*) AS qty, COUNT(DISTINCT c.SiteID) AS sites " +
                        "FROM PhoneIntegration ph JOIN Clients c ON c.ClientID = ph.ClientID " +
                        "WHERE c.SiteID IN (:siteIds) GROUP BY COALESCE(ph.PhoneBrand, 'Unknown') ORDER BY label",
                assetParams));
        Map<String, Object> softwareParams = new HashMap<>(assetParams);
        StringBuilder softwareSql = new StringBuilder(
                "SELECT sw.Name AS label, COUNT(*) AS qty, COUNT(DISTINCT ins.SiteID) AS sites " +
                        "FROM InstalledSoftware ins JOIN Software sw ON sw.SoftwareID = ins.SoftwareID " +
                        "WHERE ins.SiteID IN (:siteIds)"
        );
        if (filter.installStatus() != null && !filter.installStatus().isBlank()) {
            softwareSql.append(" AND ins.Status = :installStatus");
            softwareParams.put("installStatus", filter.installStatus());
        }
        softwareSql.append(" GROUP BY sw.Name ORDER BY sw.Name");
        entries.addAll(queryInventory("Software", softwareSql.toString(), softwareParams));

        entries.sort(Comparator.comparing(InventoryEntry::type).thenComparing(InventoryEntry::label));

        List<Map<String, Object>> rows = entries.stream().map(entry -> {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("type", translateType(entry.type()));
            row.put("label", entry.label());
            row.put("count", formatInt(entry.quantity()));
            row.put("sites", formatInt(entry.siteCount()));
            return row;
        }).collect(Collectors.toList());

        int totalAssets = entries.stream().mapToInt(InventoryEntry::quantity).sum();
        int totalServers = sumByType(entries, "Server");
        int totalClients = sumByType(entries, "Client");
        int totalSoftware = sumByType(entries, "Software");

        List<Kpi> kpis = List.of(
                new Kpi("assets", "Assets total", formatInt(totalAssets), null),
                new Kpi("servers", "Servers", formatInt(totalServers), null),
                new Kpi("clients", "Clients", formatInt(totalClients), null),
                new Kpi("software", "Software packages", formatInt(totalSoftware), null)
        );

        Map<String, Integer> typeTotals = new LinkedHashMap<>();
        for (InventoryEntry entry : entries) {
            typeTotals.merge(translateType(entry.type()), entry.quantity(), Integer::sum);
        }
        List<ChartSlice> chart = typeTotals.entrySet().stream()
                .map(e -> new ChartSlice(e.getKey(), e.getValue().doubleValue(), null))
                .collect(Collectors.toList());

        List<ReportColumn> columns = List.of(
                new ReportColumn("type", "Category", "left"),
                new ReportColumn("label", "Description", "left"),
                new ReportColumn("count", "Quantity", "right"),
                new ReportColumn("sites", "Sites", "right")
        );

        ReportTable table = new ReportTable(columns, freezeRows(rows),
                "Inventory overview", "No inventory data in the selected period.");

        return new ReportResponse(filter.type(), kpis, table, chart, "Assets by category",
                DATE_TIME_FMT.format(LocalDateTime.now()));
    }

    private List<Map<String, Object>> freezeRows(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                .collect(Collectors.toUnmodifiableList());
    }

    private void applyQueryFilter(ReportFilter filter, Map<String, Object> params,
                                  StringBuilder sql, String projectAlias, String siteAlias) {
        if (filter.query() != null && !filter.query().isBlank()) {
            params.put("query", "%" + filter.query().trim().toLowerCase(Locale.ROOT) + "%");
            sql.append(" AND (LOWER(").append(projectAlias).append(".ProjectName) LIKE :query")
               .append(" OR LOWER(").append(projectAlias).append(".ProjectSAPID) LIKE :query")
               .append(" OR LOWER(").append(siteAlias).append(".SiteName) LIKE :query)");
        }
    }

    private LocalDate getLocalDate(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return null;
    }

    private String formatDate(LocalDate date) {
        return date != null ? DATE_FMT.format(date) : "";
    }

    private String formatInt(long value) {
        return INT_FMT.format(value);
    }

    private String formatPercent(double value) {
        return PERCENT_FMT.format(value);
    }

    private String versionString(String release, String revision) {
        if (release == null && revision == null) {
            return "—";
        }
        if (revision == null || revision.isBlank()) {
            return release != null ? release : "—";
        }
        if (release == null || release.isBlank()) {
            return revision;
        }
        return release + " / " + revision;
    }

    private String formatSigned(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private List<InventoryEntry> queryInventory(String type, String sql, Map<String, Object> params) {
        return jdbc.query(sql, params, (rs, rowNum) -> new InventoryEntry(
                type,
                rs.getString("label"),
                rs.getInt("qty"),
                rs.getInt("sites")
        ));
    }

    private int sumByType(List<InventoryEntry> entries, String type) {
        return entries.stream()
                .filter(entry -> entry.type().equals(type))
                .mapToInt(InventoryEntry::quantity)
                .sum();
    }

    private String translateType(String type) {
        return switch (type) {
            case "Server" -> "Servers";
            case "Client" -> "Clients";
            case "Radio" -> "Radio devices";
            case "AudioDevice" -> "Audio devices";
            case "PhoneIntegration" -> "Telephony";
            case "Software" -> "Software";
            default -> type;
        };
    }

    private String translateStatus(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "Planned" -> "Planned";
            case "Approved" -> "Approved";
            case "InProgress" -> "In progress";
            case "Done" -> "Completed";
            case "Canceled" -> "Canceled";
            default -> status;
        };
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(";") || value.contains("\"") || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? '"' + escaped + '"' : escaped;
    }

    private String valueToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return formatInt(number.longValue());
        }
        return value.toString();
    }

    private record InventoryEntry(String type, String label, int quantity, int siteCount) {}

    private static class PdfPageWriter implements AutoCloseable {
        private final PDDocument document;
        private PDPageContentStream content;
        private PDPage page;
        private final float margin = 40f;
        private float yPosition;

        PdfPageWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            yPosition = page.getMediaBox().getHeight() - margin;
        }

        void writeLine(PDType1Font font, float size, String text) throws IOException {
            if (text == null) {
                text = "";
            }
            List<String> lines = wrapText(text, 110);
            for (String line : lines) {
                ensureSpace(size + 6);
                content.beginText();
                content.setFont(font, size);
                content.newLineAtOffset(margin, yPosition);
                content.showText(line);
                content.endText();
                yPosition -= size + 6;
            }
        }

        void blankLine() throws IOException {
            ensureSpace(12);
            yPosition -= 12;
        }

        private void ensureSpace(float required) throws IOException {
            if (yPosition - required < margin) {
                newPage();
            }
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                content.close();
            }
        }

        private List<String> wrapText(String text, int maxChars) {
            if (text.length() <= maxChars) {
                return List.of(text);
            }
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                if (current.length() + word.length() + 1 > maxChars) {
                    if (current.length() > 0) {
                        lines.add(current.toString().trim());
                        current.setLength(0);
                    }
                }
                current.append(word).append(' ');
            }
            if (current.length() > 0) {
                lines.add(current.toString().trim());
            }
            return lines;
        }
    }
}
