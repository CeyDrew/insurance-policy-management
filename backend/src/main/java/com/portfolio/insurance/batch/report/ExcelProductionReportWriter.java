package com.portfolio.insurance.batch.report;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Streams the production rows into an .xlsx file (SXSSF keeps only a window of rows in memory) and adds a
 * totals row when the step closes. The file is written to a temporary name and moved into place at the end,
 * so a failed run never leaves a half-written report behind.
 */
@Slf4j
class ExcelProductionReportWriter implements ItemStreamWriter<ProductionRow> {

    private static final String[] HEADERS = {
            "Ürün", "Acente", "Poliçe adedi", "İptal adedi", "Net prim", "Vergi", "Brüt prim", "İade"};
    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("tr"));
    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int FIRST_DATA_ROW = 4;

    private final Path target;
    private final YearMonth month;

    private SXSSFWorkbook workbook;
    private Sheet sheet;
    private CellStyle moneyStyle;
    private CellStyle boldMoneyStyle;
    private CellStyle boldStyle;
    private int nextRow = FIRST_DATA_ROW;
    private long totalPolicies;
    private long totalCancelled;
    private BigDecimal totalNet = BigDecimal.ZERO;
    private BigDecimal totalTax = BigDecimal.ZERO;
    private BigDecimal totalGross = BigDecimal.ZERO;
    private BigDecimal totalRefunds = BigDecimal.ZERO;

    ExcelProductionReportWriter(Path target, YearMonth month) {
        this.target = target;
        this.month = month;
    }

    static String fileName(YearMonth month) {
        return "uretim-raporu-%s.xlsx".formatted(month);
    }

    @Override
    public void open(ExecutionContext executionContext) {
        workbook = new SXSSFWorkbook(100);
        sheet = workbook.createSheet("Prim Üretimi");
        createStyles();

        Row title = sheet.createRow(0);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Aylık Prim Üretim Raporu - " + month.format(MONTH_TITLE));
        titleCell.setCellStyle(boldStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
        sheet.createRow(1).createCell(0).setCellValue("Oluşturulma: " + LocalDateTime.now().format(CREATED)
                + " · Kaynak: tanzim tarihi bu ayda olan poliçeler");

        Row header = sheet.createRow(3);
        CellStyle headerStyle = headerStyle();
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
        int[] widths = {28, 24, 14, 12, 18, 16, 18, 16};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, FIRST_DATA_ROW);
    }

    @Override
    public void write(Chunk<? extends ProductionRow> chunk) {
        for (ProductionRow item : chunk) {
            Row row = sheet.createRow(nextRow++);
            row.createCell(0).setCellValue(item.productName());
            row.createCell(1).setCellValue(item.agentName());
            row.createCell(2).setCellValue(item.policyCount());
            row.createCell(3).setCellValue(item.cancelledCount());
            money(row, 4, item.netPremium(), moneyStyle);
            money(row, 5, item.taxAmount(), moneyStyle);
            money(row, 6, item.grossPremium(), moneyStyle);
            money(row, 7, item.refunds(), moneyStyle);

            totalPolicies += item.policyCount();
            totalCancelled += item.cancelledCount();
            totalNet = totalNet.add(item.netPremium());
            totalTax = totalTax.add(item.taxAmount());
            totalGross = totalGross.add(item.grossPremium());
            totalRefunds = totalRefunds.add(item.refunds());
        }
    }

    @Override
    public void close() {
        if (workbook == null) {
            return;
        }
        try {
            if (nextRow == FIRST_DATA_ROW) {
                sheet.createRow(nextRow++).createCell(0).setCellValue("Bu ay tanzim edilmiş poliçe yok.");
            }
            Row totals = sheet.createRow(nextRow + 1);
            Cell label = totals.createCell(0);
            label.setCellValue("TOPLAM");
            label.setCellStyle(boldStyle);
            totals.createCell(2).setCellValue(totalPolicies);
            totals.createCell(3).setCellValue(totalCancelled);
            money(totals, 4, totalNet, boldMoneyStyle);
            money(totals, 5, totalTax, boldMoneyStyle);
            money(totals, 6, totalGross, boldMoneyStyle);
            money(totals, 7, totalRefunds, boldMoneyStyle);

            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "report-", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                workbook.write(out);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Production report written to {}", target);
        } catch (IOException e) {
            throw new ItemStreamException("Rapor dosyası yazılamadı: " + target, e);
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                log.warn("Could not close workbook", e);
            }
            workbook.dispose();
            workbook = null;
        }
    }

    private static void money(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private void createStyles() {
        short moneyFormat = workbook.createDataFormat().getFormat("#,##0.00 \"₺\"");
        Font bold = workbook.createFont();
        bold.setBold(true);

        moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(moneyFormat);

        boldMoneyStyle = workbook.createCellStyle();
        boldMoneyStyle.setDataFormat(moneyFormat);
        boldMoneyStyle.setFont(bold);

        boldStyle = workbook.createCellStyle();
        boldStyle.setFont(bold);
    }

    private CellStyle headerStyle() {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
