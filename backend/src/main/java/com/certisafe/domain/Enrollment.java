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
 * Tabla {@code enrollments}. Inscripcion de un operario a un taller (RF-015).
 *
 * <p>La pareja (usuario, taller) es {@code UNIQUE} en la base: esa restriccion es la ultima
 * linea de defensa contra la doble inscripcion, aunque el servicio ya la compruebe antes.</p>
 *
 * <p>El aforo NO se comprueba aqui. Contar las inscripciones de un taller y decidir si cabe
 * una mas exige bloquear el taller y consultar su repositorio; eso vive en
 * {@code EnrollmentService}. Meterlo en la entidad obligaria a la entidad a conocer la base
 * de datos, que es justo lo que {@code AuditableEntity} y el resto del dominio evitan.</p>
 */
@Entity
@Table(name = "enrollments")
@Getter
@Setter
@NoArgsConstructor
public class Enrollment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** El operario inscrito. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "workshop_id", nullable = false)
    private Workshop workshop;

    @Column(name = "enrolled_at", nullable = false)
    private OffsetDateTime enrolledAt;

    /**
     * {@code attendance_status} es un ENUM NATIVO de PostgreSQL, no un VARCHAR. Por eso
     * {@link SqlTypes#NAMED_ENUM} + {@link EnumType#STRING}, igual que en {@link Workshop}.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "attendance_status", nullable = false, columnDefinition = "attendance_status")
    private AttendanceStatus attendanceStatus = AttendanceStatus.PENDIENTE;

    /**
     * Momento en que se registro la asistencia; {@code null} mientras siga pendiente.
     *
     * <p>El diagrama de clases documenta {@code assignedAt} junto al metodo
     * {@code registerAttendance(estado)}, asi que ambas cosas van juntas: la fecha solo
     * existe si hay asistencia registrada.</p>
     */
    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @PrePersist
    void initTimestamps() {
        if (enrolledAt == null) {
            enrolledAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (attendanceStatus == null) {
            attendanceStatus = AttendanceStatus.PENDIENTE;
        }
    }

    /**
     * Registra la asistencia (RF-012).
     *
     * <p>Volver a {@code PENDIENTE} limpia la marca de tiempo a proposito: una inscripcion
     * pendiente no tiene asistencia registrada, y conservar la fecha anterior dejaria en el
     * historial un dato que ya no es cierto. Ese historial se usa para auditorias, asi que no
     * puede contener una asistencia que se deshizo.</p>
     */
    public void registerAttendance(AttendanceStatus estado) {
        this.attendanceStatus = estado;
        this.assignedAt = estado.isRegistered() ? OffsetDateTime.now(ZoneOffset.UTC) : null;
    }
}
