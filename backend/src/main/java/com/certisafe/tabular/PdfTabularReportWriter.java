package com.certisafe.tabular;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generador nativo de informes ejecutivos en formato PDF 1.4 para las exportaciones del backend.
 *
 * <p>Construye un documento vectorial con cabecera institucional CertiSafe, proposito e
 * introduccion, guia de criterios en panel crema, estadisticas con graficas de barras
 * interpretativas y el detalle tabular de los registros exportados.</p>
 */
final class PdfTabularReportWriter {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PdfTabularReportWriter() {
    }

    static byte[] write(List<String> headers, List<List<String>> rows) {
        List<String> pageStreams = new ArrayList<>();
        String nowStr = LocalDateTime.now().format(TS_FMT);

        // Pagina 1: Portada ejecutiva, Proposito, Seccion A y Grafica Interpretativa
        StringBuilder p1 = new StringBuilder();
        drawHeader(p1, "Informe Ejecutivo de Exportación");

        // Panel izquierdo crema (x=40..280, y=80..735)
        rectFill(p1, 40, 80, 240, 655, 0.949, 0.933, 0.894);
        text(p1, "Criterios y Lineamientos del Informe", 54, 710, 11.5, true);
        text(p1, "1. Propósito de Auditoría SST:", 54, 682, 8.8, true);
        text(p1, "Documento oficial generado desde CertiSafe", 54, 669, 8.2, false);
        text(p1, "para control de cumplimiento en IndustrialCore.", 54, 657, 8.2, false);

        text(p1, "2. Integridad de la Información:", 54, 632, 8.8, true);
        text(p1, "Los datos reflejan exactamente los filtros", 54, 619, 8.2, false);
        text(p1, "activos al momento de la exportación.", 54, 607, 8.2, false);

        text(p1, "3. Gráficas Interpretativas:", 54, 582, 8.8, true);
        text(p1, "Muestran la distribución porcentual real", 54, 569, 8.2, false);
        text(p1, "sobre el total de registros procesados.", 54, 557, 8.2, false);

        // Gráfica de barras en la mitad inferior del panel izquierdo
        text(p1, "Gráfica Interpretativa de Distribución", 54, 490, 10.0, true);
        int statCol = headers.size() > 3 ? 3 : 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (List<String> r : rows) {
            String key = statCol < r.size() && r.get(statCol) != null && !r.get(statCol).isBlank()
                    ? r.get(statCol)
                    : "General";
            counts.merge(key, 1, Integer::sum);
        }

        int barY = 462;
        int total = Math.max(1, rows.size());
        int idx = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (idx++ >= 5) {
                break;
            }
            double pct = (entry.getValue() * 100.0) / total;
            text(p1, truncate(entry.getKey(), 24), 54, barY, 8.2, true);
            text(p1, String.format(Locale.US, "%d (%.1f%%)", entry.getValue(), pct), 205, barY, 8.0, false);
            rectFill(p1, 54, barY - 10, 210, 7, 0.85, 0.83, 0.78);
            rectFill(p1, 54, barY - 10, Math.max(3, (pct / 100.0) * 210), 7, 0.18, 0.49, 0.20);
            barY -= 32;
        }

        // Columna derecha: Proposito + Seccion A + Resumen de Filas
        text(p1, "Propósito e Introducción", 300, 715, 11.5, true);
        text(p1, "Este informe consolida la información actual exportada", 300, 696, 8.6, false);
        text(p1, "desde la plataforma CertiSafe (IndustrialCore SST),", 300, 684, 8.6, false);
        text(p1, "incluyendo indicadores estadísticos y detalle tabular.", 300, 672, 8.6, false);

        line(p1, 300, 650, 555, 650, 1.5);
        text(p1, "Sección A: Información Actual del Exportado", 300, 628, 11.5, true);

        drawFormField(p1, 300, 585, "Total de Registros Exportados", rows.size() + " filas procesadas");
        drawFormField(p1, 300, 525, "Columnas Incluidas", headers.size() + " campos (" + truncate(String.join(", ", headers), 35) + ")");
        drawFormField(p1, 300, 465, "Fecha y Hora de Emisión", nowStr);

        // Vista previa de los primeros registros en la pagina 1
        text(p1, "Sección B: Detalle de Registros Exportados", 300, 405, 11.0, true);
        int previewY = 382;
        int limit = Math.min(rows.size(), 12);
        for (int i = 0; i < limit; i++) {
            List<String> r = rows.get(i);
            String summary = (i + 1) + ". " + truncate(String.join(" | ", r), 46);
            text(p1, summary, 300, previewY, 7.8, false);
            previewY -= 18;
        }

        // Lineas de firma
        line(p1, 300, 120, 420, 120, 0.8);
        text(p1, "Firma Responsable HSE", 300, 108, 7.8, false);
        line(p1, 435, 120, 555, 120, 0.8);
        text(p1, "Fecha (DD/MM/YYYY)", 435, 108, 7.8, false);

        drawFooter(p1, 1, 1);
        pageStreams.add(p1.toString());

        return assemblePdf(pageStreams);
    }

    private static void drawHeader(StringBuilder sb, String title) {
        rectFill(sb, 42, 772, 20, 26, 0.11, 0.12, 0.14);
        text(sb, "CertiSafe · IndustrialCore", 70, 788, 9.5, true);
        text(sb, "Seguridad y Salud en el Trabajo", 70, 776, 8.2, false);
        text(sb, title, 310, 780, 16.0, true);
        line(sb, 40, 756, 555, 756, 2.0);
    }

    private static void drawFooter(StringBuilder sb, int page, int total) {
        line(sb, 40, 48, 555, 48, 0.5);
        text(sb, "CertiSafe · IndustrialCore SST - Informe Oficial PDF (Página " + page + " de " + total + ")", 40, 34, 8.0, false);
    }

    private static void drawFormField(StringBuilder sb, double x, double y, String label, String value) {
        text(sb, truncate(value, 45), x, y + 6, 9.0, true);
        line(sb, x, y, 555, y, 0.8);
        text(sb, label, x, y - 12, 7.8, false);
    }

    private static void rectFill(StringBuilder sb, double x, double y, double w, double h,
                                 double r, double g, double b) {
        sb.append(String.format(Locale.US, "q %.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f Q\n", r, g, b, x, y, w, h));
    }

    private static void line(StringBuilder sb, double x1, double y1, double x2, double y2, double width) {
        sb.append(String.format(Locale.US, "q %.2f w 0.15 0.15 0.15 RG %.2f %.2f m %.2f %.2f l S Q\n", width, x1, y1, x2, y2));
    }

    private static void text(StringBuilder sb, String text, double x, double y, double size, boolean bold) {
        String font = bold ? "/F2" : "/F1";
        sb.append(String.format(Locale.US, "BT %s %.1f Tf 0.12 0.12 0.14 rg %.2f %.2f Td (%s) Tj ET\n",
                font, size, x, y, escapePdf(text)));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String clean = s.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 2) + "..";
    }

    private static String escapePdf(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' || c == '(' || c == ')') {
                out.append('\\').append(c);
            } else if (c <= 255) {
                out.append(c);
            } else {
                out.append('-');
            }
        }
        return out.toString();
    }

    private static byte[] assemblePdf(List<String> streams) {
        List<String> objects = new ArrayList<>();
        int nextIdStart = 5;
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < streams.size(); i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(nextIdStart + i * 2).append(" 0 R");
        }

        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        objects.add("2 0 obj\n<< /Type /Pages /Kids [ " + kids + " ] /Count " + streams.size() + " >>\nendobj\n");
        objects.add("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");
        objects.add("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n");

        for (int i = 0; i < streams.size(); i++) {
            int pageId = nextIdStart + i * 2;
            int contentId = pageId + 1;
            String s = streams.get(i);
            objects.add(pageId + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595.28 841.89] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + contentId + " 0 R >>\nendobj\n");
            objects.add(contentId + " 0 obj\n<< /Length " + s.length() + " >>\nstream\n" + s + "\nendstream\nendobj\n");
        }

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (String obj : objects) {
            offsets.add(pdf.length());
            pdf.append(obj);
        }
        int xrefStart = pdf.length();
        int totalObjs = objects.size() + 1;
        pdf.append("xref\n0 ").append(totalObjs).append("\n0000000000 65535 f \n");
        for (int i = 1; i < totalObjs; i++) {
            pdf.append(String.format(Locale.US, "%010d 00000 n \n", offsets.get(i)));
        }
        pdf.append("trailer\n<< /Size ").append(totalObjs).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xrefStart).append("\n%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
