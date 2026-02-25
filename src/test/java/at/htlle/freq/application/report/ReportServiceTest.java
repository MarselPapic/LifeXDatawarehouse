package at.htlle.freq.application.report;

import at.htlle.freq.domain.ArchiveState;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        ReportService service = createService(jdbc);

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
    void pdfAndExcelExportsProduceBinaryContent() {
        ReportService service = createService(null);
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

    @Test
    void xlsxAppliesFunctionalFormattingForHeaderAndData() throws IOException {
        ReportService service = createService(null);
        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("deployments", "Deployments", "right")
        );
        List<Map<String, Object>> rows = List.of(
                Map.of("account", "EuroCom", "deployments", "4"),
                Map.of("account", "Atlas Services", "deployments", "12")
        );
        ReportSummary summary = new ReportSummary(16, 1, 2, 3, 2, 5);
        ReportResponse response = new ReportResponse(
                new ReportTable(columns, rows, "Account risk overview", ""),
                summary,
                "2026-02-25 09:14"
        );

        byte[] xlsx = service.renderExcel(response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowIndex = 9;

            Row headerRow = sheet.getRow(headerRowIndex);
            assertNotNull(headerRow);
            assertEquals("Account", headerRow.getCell(0).getStringCellValue());
            Font headerFont = workbook.getFontAt(headerRow.getCell(0).getCellStyle().getFontIndexAsInt());
            assertTrue(headerFont.getBold());

            PaneInformation pane = sheet.getPaneInformation();
            assertNotNull(pane);
            assertEquals(headerRowIndex + 1, pane.getHorizontalSplitTopRow());

            assertTrue(sheet instanceof XSSFSheet);
            XSSFSheet xssfSheet = (XSSFSheet) sheet;
            assertTrue(xssfSheet.getCTWorksheet().isSetAutoFilter());

            Row firstDataRow = sheet.getRow(headerRowIndex + 1);
            assertEquals("EuroCom", firstDataRow.getCell(0).getStringCellValue());
            assertEquals(HorizontalAlignment.RIGHT, firstDataRow.getCell(1).getCellStyle().getAlignment());
            assertEquals(CellType.NUMERIC, firstDataRow.getCell(1).getCellType());
            assertEquals(4d, firstDataRow.getCell(1).getNumericCellValue());
            assertTrue(sheet.getColumnWidth(0) <= 45 * 256);
            assertTrue(sheet.getColumnWidth(1) <= 45 * 256);
        }
    }

    @Test
    void xlsxSummaryValuesAreWrittenAsNumericCells() throws IOException {
        ReportService service = createService(null);
        List<ReportColumn> columns = List.of(new ReportColumn("account", "Account", "left"));
        ReportSummary summary = new ReportSummary(9, 1, 4, 9, 8, 9);
        ReportResponse response = new ReportResponse(
                new ReportTable(columns, List.of(Map.of("account", "Harbor")), "Support end", ""),
                summary,
                "2026-02-25 09:14"
        );

        byte[] xlsx = service.renderExcel(response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row summaryRow = sheet.getRow(2);
            assertNotNull(summaryRow);
            assertEquals("Total deployments", summaryRow.getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, summaryRow.getCell(1).getCellType());
            assertEquals(9d, summaryRow.getCell(1).getNumericCellValue());
        }
    }

    @Test
    void pdfContainsStructuredContentWithoutPipeSeparatedDump() throws IOException {
        ReportService service = createService(null);
        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("deployments", "Deployments", "right")
        );
        ReportSummary summary = new ReportSummary(4, 1, 2, 3, 1, 2);
        ReportTable table = new ReportTable(columns, List.of(Map.of("account", "EuroCom", "deployments", "4")),
                "Account risk overview", "");
        ReportResponse response = new ReportResponse(table, summary, "2026-02-25 09:14");

        byte[] pdf = service.renderPdf(response);
        String text = extractText(pdf);
        String normalized = text.toLowerCase();

        assertTrue(text.contains("LifeX Report"));
        assertTrue(text.contains("Account risk overview"));
        assertTrue(text.contains("Summary"));
        assertTrue(normalized.contains("total deployments"));
        assertTrue(text.contains("Account"));
        assertTrue(text.contains("EuroCom"));
        assertFalse(text.contains("Account | Deployments"));
    }

    @Test
    void pdfKeepsUnicodeCharactersReadable() throws IOException {
        ReportService service = createService(null);
        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("site", "Site", "left")
        );
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("account", "M\u00fcnchen S\u00fcd AG");
        row.put("site", "Linz Mitte - \u00d6sterreich");
        row.put("note", "ignored");
        ReportTable table = new ReportTable(columns, List.of(row), "Unicode check", "");
        ReportResponse response = new ReportResponse(table, new ReportSummary(1, 0, 0, 0, 1, 1), "2026-02-25 09:14");

        byte[] pdf = service.renderPdf(response);
        String text = extractText(pdf);

        assertTrue(text.contains("M\u00fcnchen S\u00fcd AG"));
        assertTrue(text.contains("Linz Mitte - \u00d6sterreich"));
    }

    @Test
    void wideTablesRenderInLandscapeOrientation() throws IOException {
        ReportService service = createService(null);
        List<ReportColumn> columns = List.of(
                new ReportColumn("account", "Account", "left"),
                new ReportColumn("project", "Project", "left"),
                new ReportColumn("site", "Site", "left"),
                new ReportColumn("installStatus", "Install status", "left"),
                new ReportColumn("name", "Software", "left"),
                new ReportColumn("version", "Version", "left"),
                new ReportColumn("release", "Release", "left"),
                new ReportColumn("revision", "Revision", "left"),
                new ReportColumn("supportStart", "Support start", "left"),
                new ReportColumn("supportEnd", "Support end", "left"),
                new ReportColumn("daysRemaining", "Days", "right")
        );
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("account", "Harbor Network 08");
        row.put("project", "Project Harbor 08");
        row.put("site", "Harbor 08 Hub 2");
        row.put("installStatus", "Installed");
        row.put("name", "Analytics Suite");
        row.put("version", "2024.1.8");
        row.put("release", "2024.1");
        row.put("revision", "8");
        row.put("supportStart", "13.07.2024");
        row.put("supportEnd", "13.07.2025");
        row.put("daysRemaining", "20");
        ReportTable table = new ReportTable(columns, List.of(row), "Installed software support end dates", "");
        ReportResponse response = new ReportResponse(table, new ReportSummary(9, 0, 4, 9, 8, 9), "2026-02-25 09:14");

        byte[] pdf = service.renderPdf(response);

        try (PDDocument document = PDDocument.load(pdf)) {
            PDRectangle mediaBox = document.getPage(0).getMediaBox();
            assertTrue(mediaBox.getWidth() > mediaBox.getHeight());
        }
    }

    @Test
    void headerRepeatsAcrossPagesForLongTables() throws IOException {
        ReportService service = createService(null);
        List<ReportColumn> columns = List.of(
                new ReportColumn("colA", "HdrColA", "left"),
                new ReportColumn("colB", "HdrColB", "right")
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 350; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("colA", "entry-" + i);
            row.put("colB", String.valueOf(i));
            rows.add(row);
        }
        ReportTable table = new ReportTable(columns, rows, "Long table", "");
        ReportResponse response = new ReportResponse(table, new ReportSummary(350, 10, 30, 90, 50, 60), "2026-02-25 09:14");

        byte[] pdf = service.renderPdf(response);

        try (PDDocument document = PDDocument.load(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            PDFTextStripper pageTwoStripper = new PDFTextStripper();
            pageTwoStripper.setStartPage(2);
            pageTwoStripper.setEndPage(2);
            String pageTwoText = pageTwoStripper.getText(document);
            assertTrue(pageTwoText.contains("HdrColA"));
        }
    }

    private ReportService createService(NamedParameterJdbcTemplate jdbc) {
        return new ReportService(jdbc, new PdfReportRenderer());
    }

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

}
