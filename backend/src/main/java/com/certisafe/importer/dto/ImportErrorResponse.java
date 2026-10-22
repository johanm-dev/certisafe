package com.certisafe.importer.dto;

import java.util.Map;

/**
 * Una fila rechazada del reporte de carga masiva (RF-017 CA2).
 *
 * @param rowNumber numero de fila tal como se ve en Excel o en el editor de texto
 * @param reason    motivo del rechazo
 * @param data      datos crudos de la fila, para corregir el archivo sin buscarla a mano
 */
public record ImportErrorResponse(int rowNumber, String reason, Map<String, String> data) {
}
