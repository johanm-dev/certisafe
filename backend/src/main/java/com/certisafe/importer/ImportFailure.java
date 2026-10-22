package com.certisafe.importer;

import java.util.Map;

/**
 * Una fila rechazada, antes de guardarla en {@code import_errors}.
 *
 * @param rowNumber numero de fila del archivo (el encabezado es la 1)
 * @param reason    motivo legible para el ADMIN
 * @param data      datos crudos de la fila
 */
public record ImportFailure(int rowNumber, String reason, Map<String, String> data) {
}
