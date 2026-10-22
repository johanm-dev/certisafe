package com.certisafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Columnas de auditoria temporales presentes en varias tablas.
 *
 * <p>La base de datos ya tiene {@code DEFAULT NOW()} y triggers para {@code updated_at},
 * pero se fijan tambien en Java para que la entidad recien persistida ya traiga las
 * fechas en la respuesta HTTP sin necesidad de releerla.</p>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
