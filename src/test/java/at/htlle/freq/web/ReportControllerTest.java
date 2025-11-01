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
    void quarterPeriodExtendsToQuarterEnd() {
        ReportService reportService = mock(ReportService.class);
        AtomicReference<ReportFilter> captured = new AtomicReference<>();
        when(reportService.getReport(any())).thenAnswer(invocation -> {
            ReportFilter filter = invocation.getArgument(0);
            captured.set(filter);
            ReportTable table = new ReportTable(Collections.emptyList(), Collections.emptyList(), "", "");
            return new ReportResponse(filter.type(), Collections.emptyList(), table, Collections.emptyList(), "", "");
        });

        ReportController controller = new ReportController(reportService);
        LocalDate fixedToday = LocalDate.of(2024, 8, 15);
        try (MockedStatic<LocalDate> mockedLocalDate = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            mockedLocalDate.when(LocalDate::now).thenReturn(fixedToday);
            controller.getReportData("maintenance", null, null, null, null, null);
        }

        ReportFilter filter = captured.get();
        assertThat(filter).isNotNull();
        assertThat(filter.period()).isEqualTo("quarter");
        assertThat(filter.from()).isEqualTo(LocalDate.of(2024, 7, 1));
        assertThat(filter.to()).isEqualTo(LocalDate.of(2024, 9, 30));
    }
}
