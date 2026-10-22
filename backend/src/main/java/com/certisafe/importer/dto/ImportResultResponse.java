package com.certisafe.importer.dto;

import com.certisafe.domain.BatchStatus;

import java.util.List;

/**
 * Resultado de una carga masiva (RF-017 / RF-018).
 *
 * @param batchId     id del lote en {@code import_batches}, para rastrearlo despues
 * @param entityType  {@code USERS} o {@code WORKSHOPS}
 * @param fileName    nombre original del archivo
 * @param fileType    {@code CSV} o {@code XLSX}
 * @param totalRows   filas de datos leidas
 * @param createdRows filas insertadas
 * @param rejectedRows filas rechazadas ({@code totalRows = createdRows + rejectedRows})
 * @param status      resultado del lote
 * @param errors      detalle de cada fila rechazada
 */
public record ImportResultResponse(
        Long batchId,
        String entityType,
        String fileName,
        String fileType,
        int totalRows,
        int createdRows,
        int rejectedRows,
        BatchStatus status,
        List<ImportErrorResponse> errors
) {
}
