package com.certisafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Tabla {@code import_batches}: una fila por archivo procesado en la carga masiva
 * (RF-017 / RF-018).
 *
 * <p>Es el rastro de auditoria de la carga: quien subio el archivo, cuando, cuantas filas
 * traia y como termino. Los motivos por fila viven en {@link ImportError}.</p>
 */
@Entity
@Table(name = "import_batches")
@Getter
@Setter
@NoArgsConstructor
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre original del archivo (sin ruta). */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** {@code CSV} o {@code XLSX}. */
    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    /** Entidad importada: {@code USERS} o {@code WORKSHOPS}. */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** Filas de datos leidas del archivo (sin contar el encabezado). */
    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    /** Filas insertadas correctamente. */
    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    /** Filas rechazadas; siempre {@code total_rows = processed_rows + rejected_rows}. */
    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "batch_status")
    private BatchStatus status = BatchStatus.PROCESANDO;

    @PrePersist
    void initTimestamps() {
        if (uploadedAt == null) {
            uploadedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
