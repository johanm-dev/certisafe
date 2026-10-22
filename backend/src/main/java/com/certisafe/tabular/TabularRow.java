package com.certisafe.tabular;

import java.util.Map;

/**
 * Una fila de un archivo tabular.
 *
 * @param number numero de fila tal como se ve en Excel o en el editor de texto (el encabezado
 *               es la fila 1). Es lo que el reporte de errores le muestra al ADMIN.
 * @param values valores indexados por encabezado ya normalizado (minusculas, sin tildes).
 */
public record TabularRow(int number, Map<String, String> values) {

    public String get(String header) {
        String value = values.get(header);
        return value == null ? "" : value.trim();
    }
}
