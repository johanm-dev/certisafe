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
 * Tabla {@code audit_logs}: rastro de las operaciones sensibles (RNF-013).
 *
 * <p>La tabla existe en el esquema desde el principio y no se usaba. RNF-013 pide registrar
 * los cambios de estado de los talleres, la emision de certificaciones, los cambios de
 * instructor y las inscripciones; cada uno de esos hechos ya tiene su tabla propia
 * ({@code status_history}, {@code certificates}, {@code workshop_assignments},
 * {@code enrollments}) con autor y fecha, que es donde se consultan.</p>
 *
 * <p>Aqui se guardan los hechos que <b>no</b> tienen otra tabla donde vivir y que las HU
 * nombran expresamente: el cambio de contrasena (HU-18) y la revocacion de una certificacion
 * (HU-19). {@code old_values} y {@code new_values} son {@code jsonb} porque cada accion
 * guarda campos distintos.</p>
 *
 * <p>No hereda de {@link AuditableEntity}: la tabla solo tiene {@code created_at}, y una fila
 * de auditoria no se modifica nunca.</p>
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quien hizo la accion. La FK es {@code ON DELETE SET NULL}: el rastro sobrevive al usuario. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void initTimestamps() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
