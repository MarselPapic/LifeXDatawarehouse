package at.htlle.freq.web;

import at.htlle.freq.application.report.ReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReportControllerMaintenanceIntegrationTest {

    @Autowired
    private ReportController reportController;

    @Test
    void maintenanceReportDefaultQuarterHasEntries() {
        ReportResponse response = reportController.getReportData("maintenance", null, null, null, null, null, null);
        assertThat(response.table().rows()).isNotEmpty();
    }
}
