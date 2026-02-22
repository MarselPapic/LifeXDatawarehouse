package at.htlle.freq.web;

import at.htlle.freq.application.report.ReportResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReportControllerMaintenanceIntegrationTest {

    @Autowired
    private ReportController reportController;

    @Test
    void supportEndReportReturnsTable() {
        ReportResponse response = reportController.getReportData(null, null, "last30", null);
        assertThat(response.table()).isNotNull();
        assertThat(response.summary()).isNotNull();
    }

    @Test
    void accountRiskReportAndBinaryExportsAreAvailable() {
        ReportResponse risk = reportController.getReportData(null, null, "next90", "account-risk");
        assertThat(risk.table()).isNotNull();
        assertThat(risk.table().columns()).isNotEmpty();

        ResponseEntity<ByteArrayResource> pdf = reportController.exportPdf(null, null, "next90", "account-risk");
        assertThat(pdf.getBody()).isNotNull();
        assertThat(pdf.getBody().contentLength()).isGreaterThan(0);

        ResponseEntity<ByteArrayResource> xlsx = reportController.exportXlsx(null, null, "next90", "account-risk");
        assertThat(xlsx.getBody()).isNotNull();
        assertThat(xlsx.getBody().contentLength()).isGreaterThan(0);
    }
}
