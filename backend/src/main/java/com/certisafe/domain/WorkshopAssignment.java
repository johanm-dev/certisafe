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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Tabla {@code workshop_assignments}: historial de instructores asignados a un taller.
 *
 * <p>No se sobreescribe la fila anterior: se marca {@code is_current = false} y se inserta
 * una nueva. Asi queda la trazabilidad de quien dictó cada taller y quien lo asignó.</p>
 */
@Entity
@Table(name = "workshop_assignments")
@Getter
@Setter
@NoArgsConstructor
public class WorkshopAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workshop_id", nullable = false)
    private Workshop workshop;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = true;

    @PrePersist
    void initTimestamps() {
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
