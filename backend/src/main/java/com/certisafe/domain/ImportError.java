package com.certisafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Map;

/**
 * Tabla {@code import_errors}: una fila rechazada de un lote de carga masiva, con el numero
 * de fila del archivo, el motivo y los datos crudos (RF-017 CA2).
 *
 * <p>Se guarda {@code row_data} completo para que el ADMIN pueda corregir el archivo sin
 * tener que abrirlo y buscar la fila.</p>
 */
@Entity
@Table(name = "import_errors")
@Getter
@Setter
@NoArgsConstructor
public class ImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    /** Numero de fila tal como se ve en Excel (el encabezado es la fila 1). */
    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "error_reason", nullable = false, columnDefinition = "text")
    private String errorReason;

    /** Datos de la fila tal como venian en el archivo, indexados por columna. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "row_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> rowData;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void initTimestamps() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
