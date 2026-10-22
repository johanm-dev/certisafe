package com.certisafe.tabular;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Genera archivos CSV o .xlsx a partir de encabezados y filas de texto (RF-019).
 *
 * <p>El CSV sale con BOM UTF-8: sin el, Excel en Windows abre las tildes como caracteres
 * raros. Las celdas del Excel se escriben como texto plano a proposito, para que un valor
 * como {@code 00123} no se convierta en {@code 123}.</p>
 */
@Component
public class TabularWriter {

    public byte[] write(List<String> headers, List<List<String>> rows, TabularFormat format) {
        return format == TabularFormat.XLSX ? writeXlsx(headers, rows) : writeCsv(headers, rows);
    }

    private byte[] writeCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder buffer = new StringBuilder("\uFEFF");
        try (CSVPrinter printer = new CSVPrinter(buffer,
                CSVFormat.DEFAULT.builder().setRecordSeparator("\r\n").build())) {
            printer.printRecord(headers);
            for (List<String> row : rows) {
                printer.printRecord(row);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el CSV", e);
        }
        return buffer.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] writeXlsx(List<String> headers, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Datos");

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.size(); column++) {
                Cell cell = headerRow.createCell(column);
                cell.setCellValue(headers.get(column));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(column, 22 * 256);
            }

            int rowIndex = 1;
            for (List<String> row : rows) {
                Row sheetRow = sheet.createRow(rowIndex++);
                for (int column = 0; column < row.size(); column++) {
                    String value = row.get(column);
                    sheetRow.createCell(column).setCellValue(value == null ? "" : value);
                }
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel", e);
        }
    }
}
