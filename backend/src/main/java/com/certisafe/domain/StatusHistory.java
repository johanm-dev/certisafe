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
 * Tabla {@code status_history}: una fila por cada transición de estado de un taller (RNF-016).
 *
 * <p>Ojo: {@code previous_status} y {@code new_status} son {@code VARCHAR(50)} en la base, NO
 * el tipo nativo {@code workshop_status}. Se guardan como texto a propósito: el historial debe
 * sobrevivir aunque el catálogo de estados cambie en el futuro.</p>
 */
@Entity
@Table(name = "status_history")
@Getter
@Setter
@NoArgsConstructor
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workshop_id", nullable = false)
    private Workshop workshop;

    @Column(name = "previous_status", length = 50)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "reason")
    private String reason;

    public static StatusHistory of(Workshop workshop, WorkshopStatus previous, WorkshopStatus current,
                                   User changedBy, String reason, OffsetDateTime changedAt) {
        StatusHistory history = new StatusHistory();
        history.setWorkshop(workshop);
        history.setPreviousStatus(previous == null ? null : previous.name());
        history.setNewStatus(current.name());
        history.setChangedBy(changedBy);
        history.setReason(reason);
        history.setChangedAt(changedAt);
        return history;
    }

    @PrePersist
    void initTimestamps() {
        if (changedAt == null) {
            changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
