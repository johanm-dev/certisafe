package com.certisafe.tabular;

import com.certisafe.common.error.BusinessException;

import java.util.Locale;

/**
 * Formatos tabulares soportados: CSV y Excel (.xlsx).
 *
 * <p>RF-017, RF-018 y RF-019 piden los dos formatos; el tipo se elige con el parametro
 * {@code format} de la peticion o con la extension del archivo subido.</p>
 */
public enum TabularFormat {

    CSV("csv", "text/csv;charset=UTF-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    TabularFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static TabularFormat from(String value) {
        if (value == null || value.isBlank()) {
            return CSV;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "csv" -> CSV;
            case "xlsx", "excel" -> XLSX;
            default -> throw new BusinessException(
                    "Formato no soportado: " + value + ". Use csv o xlsx");
        };
    }
}
