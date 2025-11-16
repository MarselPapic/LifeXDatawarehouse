package at.htlle.freq.web;

import at.htlle.freq.application.report.ReportFilter;
import at.htlle.freq.application.report.ReportResponse;
import at.htlle.freq.application.report.ReportService;
import at.htlle.freq.application.report.ReportTable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    private ReportFilter captureFilterForPreset(String preset, LocalDate today) {
        ReportService reportService = mock(ReportService.class);
        AtomicReference<ReportFilter> captured = new AtomicReference<>();
        when(reportService.getReport(any())).thenAnswer(invocation -> {
            ReportFilter filter = invocation.getArgument(0);
            captured.set(filter);
            ReportTable table = new ReportTable(Collections.emptyList(), Collections.emptyList(), "", "");
            return new ReportResponse(table, "");
        });

        ReportController controller = new ReportController(reportService);
        try (MockedStatic<LocalDate> mockedLocalDate = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            mockedLocalDate.when(LocalDate::now).thenReturn(today);
            controller.getReportData(null, null, preset);
        }

        return captured.get();
    }
}
