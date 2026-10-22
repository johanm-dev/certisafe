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

/** Tabla {@code workshops}. */
@Entity
@Table(name = "workshops")
@Getter
@Setter
@NoArgsConstructor
public class Workshop extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "workshop_date", nullable = false)
    private OffsetDateTime workshopDate;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "location", nullable = false, length = 300)
    private String location;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    /**
     * {@code status} es un ENUM NATIVO de PostgreSQL ({@code workshop_status}), no un VARCHAR.
     * {@link SqlTypes#NAMED_ENUM} + {@link EnumType#STRING} le dicen a Hibernate que emita el
     * literal como {@code workshop_status} y no como texto plano.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "workshop_status")
    private WorkshopStatus status = WorkshopStatus.PROGRAMADO;

    /** Instructor titular. Es {@code ON DELETE SET NULL} en la base, por eso es opcional. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "instructor_id")
    private User instructor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "status_changed_at", nullable = false)
    private OffsetDateTime statusChangedAt;

    @PrePersist
    void initTimestamps() {
        if (statusChangedAt == null) {
            statusChangedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (status == null) {
            status = WorkshopStatus.PROGRAMADO;
        }
    }

    public boolean isFull(int enrolledCount) {
        return enrolledCount >= maxCapacity;
    }
}
