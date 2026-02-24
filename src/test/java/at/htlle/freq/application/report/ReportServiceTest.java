package at.htlle.freq.application.report;

import at.htlle.freq.domain.ArchiveState;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void supportReportMapsDateInformationAndSummary() throws SQLException {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ReportService service = new ReportService(jdbc);

        LocalDate startDate = LocalDate.of(2023, 1, 10);
        LocalDate endDate = LocalDate.now().plusDays(15);

        ResultSet row = mock(ResultSet.class);
        when(row.getString("AccountName")).thenReturn("EuroCom");
        when(row.getString("ProjectName")).thenReturn("Expansion");
        when(row.getString("SiteName")).thenReturn("Vienna Campus");
        when(row.getString("Status")).thenReturn("Installed");
        when(row.getString("Name")).thenReturn("Core");
        when(row.getString("Version")).thenReturn("2024.1.5");
        when(row.getString("Release")).thenReturn("2024");
        when(row.getString("Revision")).thenReturn("2");
        when(row.getDate("SupportStartDate")).thenReturn(Date.valueOf(startDate));
        when(row.getDate("SupportEndDate")).thenReturn(Date.valueOf(endDate));

        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Map<String, Object>> mapper = (RowMapper<Map<String, Object>>) invocation.getArgument(2);
            List<Map<String, Object>> mappedRows = new ArrayList<>();
            try {
                mappedRows.add(mapper.mapRow(row, 0));
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return mappedRows;
        });
        when(jdbc.queryForMap(anyString(), anyMap())).thenReturn(Map.of(
                "totalDeployments", 1L,
                "overdue", 0L,
                "dueIn30Days", 1L,
                "dueIn90Days", 1L,
                "distinctAccounts", 1L,
                "distinctSites", 1L
        ));

        ReportFilter filter = new ReportFilter(LocalDate.now(), LocalDate.now().plusDays(30), "next30", ArchiveState.ACTIVE);
        ReportResponse response = service.getReport(filter);

        Map<String, Object> firstRow = response.table().rows().get(0);
        assertEquals("EuroCom", firstRow.get("account"));
        assertEquals("Expansion", firstRow.get("project"));
        assertEquals("Vienna Campus", firstRow.get("site"));
        assertEquals("Installed", firstRow.get("installStatus"));
        assertEquals("Core", firstRow.get("name"));
        assertEquals("2024.1.5", firstRow.get("version"));
        assertEquals("2024", firstRow.get("release"));
        assertEquals("2", firstRow.get("revision"));
        assertEquals("10.01.2023", firstRow.get("supportStart"));
        assertNotNull(response.summary());
        assertEquals(1L, response.summary().totalDeployments());
    }

    @Test
    void csvRenderIncludesSupportHeadings() {
        ReportService service = new ReportService(null);
        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("project", "Project", "left"),
                new ReportColumn("site", "Site", "left"),
                new ReportColumn("installStatus", "Install status", "left"),
                new ReportColumn("name", "Software", "left"),
                new ReportColumn("version", "Version", "left"),
                new ReportColumn("supportEnd", "Support end", "left")
        );
        Map<String, Object> row = Map.of(
                "account", "EuroCom",
                "project", "Expansion",
                "site", "Vienna Campus",
                "installStatus", "Installed",
                "name", "Core",
                "version", "2024.1.5",
                "supportEnd", "01.01.2025"
        );
        ReportTable table = new ReportTable(columns, List.of(row), "Support end dates", "");
        ReportResponse response = new ReportResponse(table, "2024-01-01 10:00");

        String csv = service.renderCsv(response);

        String[] lines = csv.split("\n");
        assertEquals("Report;Support end dates", lines[0]);
        assertEquals("Account;Project;Site;Install status;Software;Version;Support end", lines[3]);
        assertEquals("EuroCom;Expansion;Vienna Campus;Installed;Core;2024.1.5;01.01.2025", lines[4]);
    }

    @Test
    void pdfAndExcelExportsProduceBinaryContent() {
        ReportService service = new ReportService(null);
        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("deployments", "Deployments", "right")
        );
        ReportTable table = new ReportTable(columns, List.of(Map.of("account", "EuroCom", "deployments", "4")),
                "Account risk overview", "");
        ReportSummary summary = new ReportSummary(4, 1, 2, 3, 1, 2);
        ReportResponse response = new ReportResponse(table, summary, "2026-02-22 14:00");

        byte[] pdf = service.renderPdf(response);
        byte[] xlsx = service.renderExcel(response);

        assertTrue(pdf.length > 0);
        assertTrue(xlsx.length > 0);
    }
}
