package at.htlle.freq.web;

import at.htlle.freq.application.report.ReportResponse;
import at.htlle.freq.application.report.ReportSummary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

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
        assertThat(pdf.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);

        ResponseEntity<ByteArrayResource> xlsx = reportController.exportXlsx(null, null, "next90", "account-risk");
        assertThat(xlsx.getBody()).isNotNull();
        assertThat(xlsx.getBody().contentLength()).isGreaterThan(0);
    }

    @Test
    void seededSummaryCoversOverdueThirtyAndNinetyDayRiskBuckets() {
        String from = LocalDate.now().minusDays(30).toString();
        String to = LocalDate.now().plusDays(180).toString();

        ReportSummary summary = reportController.getSummary(from, to, "custom");

        assertThat(summary.totalDeployments()).isGreaterThan(0);
        assertThat(summary.overdue()).isGreaterThan(0);
        assertThat(summary.dueIn30Days()).isGreaterThan(0);
        assertThat(summary.dueIn90Days()).isGreaterThan(summary.dueIn30Days());
        assertThat(summary.distinctAccounts()).isGreaterThan(0);
        assertThat(summary.distinctSites()).isGreaterThan(0);
    }

    @Test
    void tableCanBeEmptyForOutOfRangeWindowWhileSummaryRemainsPopulated() {
        String from = LocalDate.now().plusYears(5).toString();
        String to = LocalDate.now().plusYears(5).plusDays(20).toString();

        ReportResponse response = reportController.getReportData(from, to, "custom", "support-end");
        assertThat(response.table()).isNotNull();
        assertThat(response.table().rows()).isEmpty();

        ReportSummary summary = reportController.getSummary(from, to, "custom");
        assertThat(summary.totalDeployments()).isGreaterThan(0);
        assertThat(summary.overdue()).isGreaterThan(0);
        assertThat(summary.dueIn30Days()).isGreaterThan(0);
        assertThat(summary.dueIn90Days()).isGreaterThan(0);
    }
}
