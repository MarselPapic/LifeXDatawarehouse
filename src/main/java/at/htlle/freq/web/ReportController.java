package at.htlle.freq.web;

import at.htlle.freq.application.report.ReportFilter;
import at.htlle.freq.application.report.ReportResponse;
import at.htlle.freq.application.report.ReportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
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

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Returns support end report data as JSON.
     *
     * <p>Path: {@code GET /api/reports/data}</p>
     * <p>Query parameters: {@code from}, {@code to}, {@code preset} (all optional).</p>
     *
     * @param from   start date (ISO-8601)
     * @param to     end date (ISO-8601)
     * @param preset date range shortcut (e.g. {@code last30})
     * @return 200 OK with {@link ReportResponse} as JSON.
     */
    @GetMapping("/data")
    public ReportResponse getReportData(@RequestParam(name = "from", required = false) String from,
                                        @RequestParam(name = "to", required = false) String to,
                                        @RequestParam(name = "preset", required = false) String preset) {
        ReportFilter filter = buildFilter(preset, from, to);
        return reportService.getReport(filter);
    }

    /**
     * Exports the report as a CSV file.
     *
     * <p>Path: {@code GET /api/reports/export/csv}</p>
     * <p>Query parameters match {@link #getReportData(String, String, String)}.</p>
     *
     * @return 200 OK with a CSV file ({@code text/csv}).
     */
    @GetMapping("/export/csv")
    public ResponseEntity<ByteArrayResource> exportCsv(@RequestParam(name = "from", required = false) String from,
                                                       @RequestParam(name = "to", required = false) String to,
                                                       @RequestParam(name = "preset", required = false) String preset) {
        ReportFilter filter = buildFilter(preset, from, to);
        ReportResponse response = reportService.getReport(filter);
        String csv = reportService.renderCsv(response);
        byte[] data = csv.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + buildFileName("csv"))
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(data.length)
                .body(resource);
    }

    private ReportFilter buildFilter(String preset, String from, String to) {
        String normalizedPreset = preset == null ? null : preset.trim().toLowerCase(Locale.ROOT);
        DateRange range = resolveRange(normalizedPreset, from, to);
        return new ReportFilter(range.from, range.to, normalizedPreset);
    }

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

    private String buildFileName(String extension) {
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now());
        return "lifex-support-end-" + ts + "." + extension;
    }

    private record DateRange(LocalDate from, LocalDate to) {}
}
