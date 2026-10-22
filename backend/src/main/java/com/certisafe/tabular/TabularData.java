package com.certisafe.tabular;

import java.util.List;

/**
 * Contenido de un archivo tabular ya leido.
 *
 * @param headers encabezados normalizados, en el orden del archivo
 * @param rows    filas de datos (las filas vacias se descartan)
 */
public record TabularData(List<String> headers, List<TabularRow> rows) {

    public boolean hasHeader(String header) {
        return headers.contains(header);
    }
}
