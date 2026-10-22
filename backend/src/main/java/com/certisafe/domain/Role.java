package com.certisafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tabla {@code roles}. Roles del sistema: ADMIN, INSTRUCTOR, OPERATOR. */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public static final String ADMIN = "ADMIN";
    public static final String INSTRUCTOR = "INSTRUCTOR";
    public static final String OPERATOR = "OPERATOR";
}
