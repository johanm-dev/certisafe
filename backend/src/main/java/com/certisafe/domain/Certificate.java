package com.certisafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Tabla {@code certificates}. Certificacion de un operario por un taller (RF-013).
 *
 * <p>No hereda de {@link AuditableEntity}: la tabla {@code certificates} solo tiene
 * {@code created_at}, no {@code updated_at}. Heredar arrastraria una columna que no existe y
 * {@code ddl-auto=validate} no dejaria arrancar.</p>
 *
 * <p>{@code enrollment_id} es {@code UNIQUE}: una inscripcion tiene como mucho una
 * certificacion. Esa restriccion es lo que impide emitir dos veces el mismo certificado si
 * la asistencia se marca como "asistio" mas de una vez.</p>
 */
@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** El operario certificado. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "workshop_id", nullable = false)
    private Workshop workshop;

    /** La inscripcion que origino la certificacion. */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "enrollment_id", nullable = false, unique = true)
    private Enrollment enrollment;

    /**
     * Quien avala la certificacion: el instructor asignado al taller o, si el taller no tiene
     * instructor, quien registro la asistencia. La columna es {@code NOT NULL} porque un
     * certificado sin responsable no es verificable.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    /** Numero visible, p. ej. {@code CS-2026-000042}. Unico. */
    @Column(name = "certificate_number", nullable = false, length = 50)
    private String certificateNumber;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void initTimestamps() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (issuedAt == null) {
            issuedAt = now;
        }
    }

    /** Estado de la certificacion en un instante dado. */
    public CertificateStatus statusAt(OffsetDateTime now) {
        if (revoked) {
            return CertificateStatus.REVOCADA;
        }
        return expiresAt.isBefore(now) ? CertificateStatus.VENCIDA : CertificateStatus.VIGENTE;
    }

    /** Anula la certificacion dejando rastro de cuando y por que. */
    public void revoke(String reason, OffsetDateTime when) {
        this.revoked = true;
        this.revokedAt = when;
        this.revocationReason = reason;
    }

    /**
     * Devuelve la certificacion a la vida.
     *
     * <p>Solo se usa cuando la revocacion fue automatica, es decir, cuando lo que estaba mal
     * eran los datos de asistencia y no el certificado. Una revocacion hecha por una persona
     * no se deshace marcando asistencia: eso la volveria reversible por la puerta de atras.</p>
     */
    public void reactivate() {
        this.revoked = false;
        this.revokedAt = null;
        this.revocationReason = null;
    }
}
