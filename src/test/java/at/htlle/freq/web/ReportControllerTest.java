package at.htlle.freq.web;

import at.htlle.freq.application.report.ReportFilter;
import at.htlle.freq.application.report.ReportResponse;
import at.htlle.freq.application.report.ReportService;
import at.htlle.freq.application.report.ReportTable;
import at.htlle.freq.application.report.ReportView;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    @Test
    void quarterPresetExtendsToQuarterEnd() {
        LocalDate fixedToday = LocalDate.of(2024, 8, 15);
        ReportFilter filter = captureFilterForPreset("quarter", fixedToday);
        assertThat(filter.preset()).isEqualTo("quarter");
        assertThat(filter.from()).isEqualTo(LocalDate.of(2024, 7, 1));
        assertThat(filter.to()).isEqualTo(LocalDate.of(2024, 9, 30));
    }

    @Test
    void next90PresetCoversNinetyDaysFromToday() {
        LocalDate fixedToday = LocalDate.of(2024, 2, 10);
        ReportFilter filter = captureFilterForPreset("next90", fixedToday);

        assertThat(filter).isNotNull();
        assertThat(filter.preset()).isEqualTo("next90");
        assertThat(filter.from()).isEqualTo(fixedToday);
        assertThat(filter.to()).isEqualTo(fixedToday.plusDays(89));
    }

    @Test
    void next180PresetCoversOneHundredEightyDaysFromToday() {
        LocalDate fixedToday = LocalDate.of(2024, 2, 10);
        ReportFilter filter = captureFilterForPreset("next180", fixedToday);

        assertThat(filter).isNotNull();
        assertThat(filter.preset()).isEqualTo("next180");
        assertThat(filter.from()).isEqualTo(fixedToday);
        assertThat(filter.to()).isEqualTo(fixedToday.plusDays(179));
    }

    @Test
    void defaultViewResolvesToSupportEnd() {
        ReportService reportService = mock(ReportService.class);
        ReportTable table = new ReportTable(Collections.emptyList(), Collections.emptyList(), "", "");
        when(reportService.getReport(any(ReportView.class), any(ReportFilter.class)))
                .thenReturn(new ReportResponse(table, ""));

        ReportController controller = new ReportController(reportService);
        controller.getReportData(null, null, "next30", null);

        org.mockito.Mockito.verify(reportService).getReport(eq(ReportView.SUPPORT_END), any(ReportFilter.class));
    }

    @Test
    void unknownViewIsRejectedWithBadRequest() {
        ReportService reportService = mock(ReportService.class);
        ReportController controller = new ReportController(reportService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getReportData(null, null, "next30", "invalid-view"));
        assertThat(ex.getStatusCode().value()).isEqualTo(400);
    }

    private ReportFilter captureFilterForPreset(String preset, LocalDate today) {
        ReportService reportService = mock(ReportService.class);
        AtomicReference<ReportFilter> captured = new AtomicReference<>();
        when(reportService.getReport(any(ReportView.class), any(ReportFilter.class))).thenAnswer(invocation -> {
            ReportFilter filter = invocation.getArgument(1);
            captured.set(filter);
            ReportTable table = new ReportTable(Collections.emptyList(), Collections.emptyList(), "", "");
            return new ReportResponse(table, "");
        });

        ReportController controller = new ReportController(reportService);
        try (MockedStatic<LocalDate> mockedLocalDate = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            mockedLocalDate.when(LocalDate::now).thenReturn(today);
            controller.getReportData(null, null, preset, null);
        }

        return captured.get();
    }
}
