package com.certisafe.exporter;

import com.certisafe.tabular.TabularFormat;

/**
 * Archivo generado por una exportacion (RF-019).
 *
 * @param content  bytes del archivo
 * @param fileName nombre sugerido, ya con la extension correcta
 * @param format   formato, para fijar el {@code Content-Type}
 */
public record ExportFile(byte[] content, String fileName, TabularFormat format) {
}
