package at.htlle.freq.web;

import at.htlle.freq.application.report.ReportFilter;
import at.htlle.freq.application.report.ReportResponse;
import at.htlle.freq.application.report.ReportSummary;
import at.htlle.freq.application.report.ReportService;
import at.htlle.freq.application.report.ReportView;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * REST controller for reporting endpoints.
 *
 * <p>Delegates report generation and export to {@link ReportService}.</p>
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    /**
     * Creates a controller that delegates report generation to {@link ReportService}.
     *
     * @param reportService service that builds report data and exports.
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Returns support end report data as JSON.
     *
     * <p>Path: {@code GET /api/reports/data}</p>
 * <p>Query parameters: {@code from}, {@code to}, {@code preset}, {@code view} (all optional).</p>
     *
     * @param from   start date (ISO-8601)
     * @param to     end date (ISO-8601)
     * @param preset date range shortcut (e.g. {@code last30})
     * @param view report view key ({@code support-end}, {@code lifecycle-status}, {@code account-risk})
     * @return 200 OK with {@link ReportResponse} as JSON.
     */
    @GetMapping("/data")
    public ReportResponse getReportData(@RequestParam(name = "from", required = false) String from,
                                        @RequestParam(name = "to", required = false) String to,
                                        @RequestParam(name = "preset", required = false) String preset,
                                        @RequestParam(name = "view", required = false) String view) {
        ReportFilter filter = buildFilter(preset, from, to);
        ReportView reportView = resolveView(view);
        return reportService.getReport(reportView, filter);
    }

    /**
     * Returns KPI summary values for support risk reporting.
     *
     * @param from start date (ISO-8601)
     * @param to end date (ISO-8601)
     * @param preset date range shortcut
     * @return summary KPI payload.
     */
    @GetMapping("/summary")
    public ReportSummary getSummary(@RequestParam(name = "from", required = false) String from,
                                    @RequestParam(name = "to", required = false) String to,
                                    @RequestParam(name = "preset", required = false) String preset) {
        ReportFilter filter = buildFilter(preset, from, to);
        return reportService.getSummary(filter);
    }

    /**
     * Exports the report as a CSV file.
     *
     * <p>Path: {@code GET /api/reports/export/csv}</p>
     * <p>Query parameters match {@link #getReportData(String, String, String, String)}.</p>
     *
     * @return 200 OK with a CSV file ({@code text/csv}).
     */
    @GetMapping("/export/csv")
    public ResponseEntity<ByteArrayResource> exportCsv(@RequestParam(name = "from", required = false) String from,
                                                       @RequestParam(name = "to", required = false) String to,
                                                       @RequestParam(name = "preset", required = false) String preset,
                                                       @RequestParam(name = "view", required = false) String view) {
        ReportFilter filter = buildFilter(preset, from, to);
        ReportView reportView = resolveView(view);
        ReportResponse response = reportService.getReport(reportView, filter);
        String csv = reportService.renderCsv(response);
        byte[] data = csv.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + buildFileName(reportView, "csv"))
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(data.length)
                .body(resource);
    }

    /**
     * Exports the report as a PDF file.
     *
     * @param from start date (ISO-8601)
     * @param to end date (ISO-8601)
     * @param preset date range shortcut
     * @param view report view key
     * @return 200 OK with a PDF attachment.
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<ByteArrayResource> exportPdf(@RequestParam(name = "from", required = false) String from,
                                                       @RequestParam(name = "to", required = false) String to,
                                                       @RequestParam(name = "preset", required = false) String preset,
                                                       @RequestParam(name = "view", required = false) String view) {
        ReportFilter filter = buildFilter(preset, from, to);
        ReportView reportView = resolveView(view);
        ReportResponse response = reportService.getReport(reportView, filter);
        byte[] data = reportService.renderPdf(response);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + buildFileName(reportView, "pdf"))
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(data.length)
                .body(resource);
    }

    /**
     * Exports the report as an XLSX file.
     *
     * @param from start date (ISO-8601)
     * @param to end date (ISO-8601)
     * @param preset date range shortcut
     * @param view report view key
     * @return 200 OK with an XLSX attachment.
     */
    @GetMapping("/export/xlsx")
    public ResponseEntity<ByteArrayResource> exportXlsx(@RequestParam(name = "from", required = false) String from,
                                                         @RequestParam(name = "to", required = false) String to,
                                                         @RequestParam(name = "preset", required = false) String preset,
                                                         @RequestParam(name = "view", required = false) String view) {
        ReportFilter filter = buildFilter(preset, from, to);
        ReportView reportView = resolveView(view);
        ReportResponse response = reportService.getReport(reportView, filter);
        byte[] data = reportService.renderExcel(response);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + buildFileName(reportView, "xlsx"))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(resource);
    }

    /**
     * Builds a {@link ReportFilter} from preset and custom date ranges.
     *
     * @param preset preset label (for example {@code last30}).
     * @param from custom range start date string.
     * @param to custom range end date string.
     * @return filter for report generation.
     */
    private ReportFilter buildFilter(String preset, String from, String to) {
        String normalizedPreset = preset == null ? null : preset.trim().toLowerCase(Locale.ROOT);
        DateRange range = resolveRange(normalizedPreset, from, to);
        return new ReportFilter(range.from, range.to, normalizedPreset);
    }

    /**
     * Resolves the date range from a preset or explicit date parameters.
     *
     * @param preset preset label or {@code custom}.
     * @param fromStr custom start date string.
     * @param toStr custom end date string.
     * @return resolved date range.
     */
    private DateRange resolveRange(String preset, String fromStr, String toStr) {
        if (preset == null || preset.isBlank()) {
            return parseCustomRange(fromStr, toStr);
        }
        LocalDate today = LocalDate.now();
        return switch (preset) {
            case "last7" -> new DateRange(today.minusDays(6), today);
            case "last30" -> new DateRange(today.minusDays(29), today);
            case "next30" -> new DateRange(today, today.plusDays(29));
            case "next90" -> new DateRange(today, today.plusDays(89));
            case "next180" -> new DateRange(today, today.plusDays(179));
            case "quarter" -> {
                int quarter = (today.getMonthValue() - 1) / 3;
                int startMonth = quarter * 3 + 1;
                LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
                LocalDate end = start.plusMonths(3).minusDays(1);
                yield new DateRange(start, end);
            }
            case "custom" -> parseCustomRange(fromStr, toStr);
            default -> throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown preset: " + preset);
        };
    }

    /**
     * Parses a custom date range from string inputs.
     *
     * @param fromStr start date string.
     * @param toStr end date string.
     * @return date range with normalized ordering.
     */
    private DateRange parseCustomRange(String fromStr, String toStr) {
        if (fromStr == null && toStr == null) {
            return new DateRange(null, null);
        }
        try {
            LocalDate from = fromStr != null ? LocalDate.parse(fromStr.trim()) : null;
            LocalDate to = toStr != null ? LocalDate.parse(toStr.trim()) : null;
            if (from != null && to != null && from.isAfter(to)) {
                LocalDate tmp = from;
                from = to;
                to = tmp;
            }
            return new DateRange(from, to);
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid date format", ex);
        }
    }

    /**
     * Builds the export file name for the report.
     *
     * @param extension file extension such as {@code csv}.
     * @return formatted file name including timestamp.
     */
    private String buildFileName(ReportView view, String extension) {
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now());
        return "lifex-" + view.queryValue() + "-" + ts + "." + extension;
    }

    private ReportView resolveView(String rawView) {
        try {
            return ReportView.fromQuery(rawView);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * Component that provides Date Range behavior.
     */
    private record DateRange(LocalDate from, LocalDate to) {}
}
