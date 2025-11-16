package at.htlle.freq.application.report;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void supportReportMapsDateInformation() throws SQLException {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ReportService service = new ReportService(jdbc);

        LocalDate startDate = LocalDate.of(2023, 1, 10);
        LocalDate endDate = LocalDate.now().plusDays(15);

        ResultSet row = mock(ResultSet.class);
        when(row.getString("Name")).thenReturn("Core");
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

        ReportFilter filter = new ReportFilter(LocalDate.now(), LocalDate.now().plusDays(30), "next30");
        ReportResponse response = service.getReport(filter);

        Map<String, Object> firstRow = response.table().rows().get(0);
        assertEquals("Core", firstRow.get("name"));
        assertEquals("2024", firstRow.get("release"));
        assertEquals("2", firstRow.get("revision"));
        assertEquals("10.01.2023", firstRow.get("supportStart"));
        assertNotEquals("—", firstRow.get("daysRemaining"));
    }

    @Test
    void csvRenderIncludesSupportHeadings() {
        ReportService service = new ReportService(null);
        List<ReportColumn> columns = List.of(
                new ReportColumn("name", "Software", "left"),
                new ReportColumn("supportEnd", "Support end", "left")
        );
        Map<String, Object> row = Map.of(
                "name", "Core",
                "supportEnd", "01.01.2025"
        );
        ReportTable table = new ReportTable(columns, List.of(row), "", "");
        ReportResponse response = new ReportResponse(table, "2024-01-01 10:00");

        String csv = service.renderCsv(response);

        String[] lines = csv.split("\n");
        assertEquals("Report;Support end dates", lines[0]);
        assertEquals("Software;Support end", lines[3]);
        assertEquals("Core;01.01.2025", lines[4]);
    }
}
