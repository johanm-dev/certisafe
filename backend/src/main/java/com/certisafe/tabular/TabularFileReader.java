package com.certisafe.tabular;

import com.certisafe.common.error.BusinessException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee archivos .csv y .xlsx y los convierte en filas con encabezados normalizados.
 *
 * <p>Decisiones que no son obvias:</p>
 * <ul>
 *   <li>El delimitador del CSV se detecta: Excel en espanol guarda con {@code ;}, mientras
 *       que la plantilla que descarga el ADMIN usa {@code ,}. Aceptar solo uno obligaria al
 *       usuario a convertir el archivo a mano.</li>
 *   <li>El CSV se lee como UTF-8 y, si los bytes no son UTF-8 validos, como Windows-1252,
 *       que es lo que produce Excel en Windows al "Guardar como CSV".</li>
 *   <li>Los encabezados se normalizan (minusculas, sin tildes, espacios a {@code _}) para que
 *       "Correo electronico" y "correo_electronico" sean la misma columna.</li>
 * </ul>
 */
@Component
public class TabularFileReader {

    private static final char[] CANDIDATE_DELIMITERS = {',', ';', '\t'};
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    public TabularData read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    "El archivo está vacío. Cargue un archivo .xlsx o .csv con datos");
        }
        String extension = extensionOf(file.getOriginalFilename());
        try {
            return switch (extension) {
                case "csv" -> readCsv(file.getBytes());
                case "xlsx" -> readXlsx(file.getInputStream());
                default -> throw new BusinessException(
                        "Formato no permitido. Solo se aceptan archivos .xlsx o .csv");
            };
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ CSV

    private TabularData readCsv(byte[] bytes) {
        String text = decode(bytes);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw new BusinessException("El archivo no contiene filas de datos");
        }

        char delimiter = detectDelimiter(text);
        List<String> headers = null;
        List<TabularRow> rows = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(new StringReader(text), CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setQuote('"')
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build())) {

            for (CSVRecord record : parser) {
                if (headers == null) {
                    headers = normalizeAll(record.toList());
                    continue;
                }
                Map<String, String> values = index(headers, record.toList());
                if (allBlank(values)) {
                    continue;
                }
                rows.add(new TabularRow((int) record.getRecordNumber(), values));
            }
        } catch (IOException | IllegalStateException e) {
            throw new BusinessException("No se pudo interpretar el CSV: " + e.getMessage());
        }

        return validate(headers, rows);
    }

    // ------------------------------------------------------------------ XLSX

    private TabularData readXlsx(InputStream input) throws IOException {
        Workbook workbook;
        try {
            workbook = new XSSFWorkbook(input);
        } catch (RuntimeException e) {
            // POI lanza NotOfficeXmlFileException cuando el archivo no es un libro OOXML.
            throw new BusinessException("El archivo no es un libro .xlsx válido");
        }

        try (Workbook open = workbook) {
            if (open.getNumberOfSheets() == 0) {
                throw new BusinessException("El libro no tiene hojas");
            }
            Sheet sheet = open.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = open.getCreationHelper().createFormulaEvaluator();

            int first = sheet.getFirstRowNum();
            int last = sheet.getLastRowNum();
            if (last <= first) {
                throw new BusinessException("El archivo no contiene filas de datos");
            }

            Row headerRow = sheet.getRow(first);
            List<String> headers = new ArrayList<>();
            if (headerRow != null) {
                for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                    headers.add(normalizeHeader(cellText(headerRow.getCell(column), formatter, evaluator)));
                }
            }

            List<TabularRow> rows = new ArrayList<>();
            for (int index = first + 1; index <= last; index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++) {
                    String header = headers.get(column);
                    if (!header.isEmpty()) {
                        values.put(header, cellText(row.getCell(column), formatter, evaluator));
                    }
                }
                if (allBlank(values)) {
                    continue;
                }
                rows.add(new TabularRow(index + 1, values));
            }

            return validate(headers, rows);
        }
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime value = cell.getLocalDateTimeCellValue();
            return value == null ? "" : value.format(DATE_TIME);
        }
        String text = formatter.formatCellValue(cell, evaluator);
        return text == null ? "" : text.trim();
    }

    // ------------------------------------------------------------------ comun

    private TabularData validate(List<String> headers, List<TabularRow> rows) {
        if (headers == null || headers.stream().allMatch(String::isBlank)) {
            throw new BusinessException(
                    "El archivo no tiene encabezados. Descargue la plantilla para ver el formato esperado");
        }
        if (rows.isEmpty()) {
            throw new BusinessException("El archivo no contiene filas de datos");
        }
        return new TabularData(List.copyOf(headers), List.copyOf(rows));
    }

    private static Map<String, String> index(List<String> headers, List<String> values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header.isEmpty()) {
                continue;
            }
            map.put(header, i < values.size() ? values.get(i).trim() : "");
        }
        return map;
    }

    private static List<String> normalizeAll(List<String> headers) {
        return headers.stream().map(TabularFileReader::normalizeHeader).toList();
    }

    /** "Correo Electrónico " -> "correo_electronico". */
    static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return text;
    }

    private static boolean allBlank(Map<String, String> values) {
        return values.values().stream().allMatch(value -> value == null || value.isBlank());
    }

    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        char best = ',';
        int bestCount = 0;
        for (char candidate : CANDIDATE_DELIMITERS) {
            int count = 0;
            for (int i = 0; i < firstLine.length(); i++) {
                if (firstLine.charAt(i) == candidate) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }
        return best;
    }

    private static String decode(byte[] bytes) {
        int offset = hasBom(bytes) ? 3 : 0;
        String utf8 = decodeStrict(bytes, offset, StandardCharsets.UTF_8);
        return utf8 != null ? utf8 : new String(bytes, offset, bytes.length - offset, WINDOWS_1252);
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    private static String decodeStrict(byte[] bytes, int offset, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }
}
