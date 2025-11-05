package at.htlle.freq.web;

import at.htlle.freq.application.report.ReportFilter;
import at.htlle.freq.application.report.ReportOptions;
import at.htlle.freq.application.report.ReportResponse;
import at.htlle.freq.application.report.ReportService;
import at.htlle.freq.application.report.ReportType;
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

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/options")
    public ReportOptions getOptions() {
        return reportService.getOptions();
    }

    @GetMapping("/data")
    public ReportResponse getReportData(@RequestParam(name = "type", required = false) String type,
                                        @RequestParam(name = "period", required = false) String period,
                                        @RequestParam(name = "from", required = false) String from,
                                        @RequestParam(name = "to", required = false) String to,
                                        @RequestParam(name = "query", required = false) String query,
                                        @RequestParam(name = "variant", required = false) String variant,
                                        @RequestParam(name = "installStatus", required = false) String installStatus) {
        ReportFilter filter = buildFilter(type, period, from, to, query, variant, installStatus);
        return reportService.getReport(filter);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<ByteArrayResource> exportCsv(@RequestParam(name = "type", required = false) String type,
                                                       @RequestParam(name = "period", required = false) String period,
                                                       @RequestParam(name = "from", required = false) String from,
                                                       @RequestParam(name = "to", required = false) String to,
                                                       @RequestParam(name = "query", required = false) String query,
                                                       @RequestParam(name = "variant", required = false) String variant,
                                                       @RequestParam(name = "installStatus", required = false) String installStatus) {
        ReportFilter filter = buildFilter(type, period, from, to, query, variant, installStatus);
        ReportResponse response = reportService.getReport(filter);
        String csv = reportService.renderCsv(response);
        byte[] data = csv.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + buildFileName(filter.type(), "csv"))
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(data.length)
                .body(resource);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<ByteArrayResource> exportPdf(@RequestParam(name = "type", required = false) String type,
                                                       @RequestParam(name = "period", required = false) String period,
                                                       @RequestParam(name = "from", required = false) String from,
                                                       @RequestParam(name = "to", required = false) String to,
                                                       @RequestParam(name = "query", required = false) String query,
                                                       @RequestParam(name = "variant", required = false) String variant,
                                                       @RequestParam(name = "installStatus", required = false) String installStatus) {
        ReportFilter filter = buildFilter(type, period, from, to, query, variant, installStatus);
        ReportResponse response = reportService.getReport(filter);
        byte[] pdf = reportService.renderPdf(response);
        ByteArrayResource resource = new ByteArrayResource(pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + buildFileName(filter.type(), "pdf"))
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(resource);
    }

    private ReportFilter buildFilter(String typeParam, String periodParam, String from, String to,
                                     String query, String variant, String installStatus) {
        ReportType type;
        try {
            type = ReportType.fromParameter(typeParam);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        String normalizedPeriod = (periodParam == null || periodParam.isBlank())
                ? "quarter"
                : periodParam.trim().toLowerCase(Locale.ROOT);
        DateRange range = resolveRange(normalizedPeriod, from, to);

        return new ReportFilter(
                type,
                normalizedPeriod,
                range.from,
                range.to,
                sanitize(query),
                sanitize(variant),
                sanitize(installStatus)
        );
    }

    private DateRange resolveRange(String period, String fromStr, String toStr) {
        if (period == null || period.isBlank() || "all".equals(period)) {
            return new DateRange(null, null);
        }
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "last7" -> new DateRange(today.minusDays(6), today);
            case "last30" -> new DateRange(today.minusDays(29), today);
            case "quarter" -> {
                int quarter = (today.getMonthValue() - 1) / 3;
                int startMonth = quarter * 3 + 1;
                LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
                LocalDate end = start.plusMonths(3).minusDays(1);
                yield new DateRange(start, end);
            }
            case "custom" -> {
                if (fromStr == null || toStr == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zeitraum 'custom' benötigt from/to");
                }
                try {
                    LocalDate from = LocalDate.parse(fromStr.trim());
                    LocalDate to = LocalDate.parse(toStr.trim());
                    if (from.isAfter(to)) {
                        LocalDate tmp = from;
                        from = to;
                        to = tmp;
                    }
                    yield new DateRange(from, to);
                } catch (Exception ex) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiges Datumsformat", ex);
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannter Zeitraum: " + period);
        };
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildFileName(ReportType type, String extension) {
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now());
        return "lifex-report-" + type.toLowerCase() + "-" + ts + "." + extension;
    }

    private record DateRange(LocalDate from, LocalDate to) {}
}
