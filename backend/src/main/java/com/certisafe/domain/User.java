package com.certisafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Tabla {@code users}. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** Hash BCrypt costo 12. Nunca se devuelve al cliente. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", length = 30)
    private String phone;

    /**
     * EAGER a proposito: el rol siempre se necesita para construir la respuesta y las
     * autoridades. Evita LazyInitializationException al mapear a DTO fuera de transaccion.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "last_access")
    private OffsetDateTime lastAccess;

    /** Intentos de inicio de sesion fallidos consecutivos (RNF-015). Se reinicia al acertar. */
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    /** Si esta en el futuro, la cuenta esta bloqueada temporalmente (RNF-015). */
    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
