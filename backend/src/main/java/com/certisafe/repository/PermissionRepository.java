package com.certisafe.repository;

import com.certisafe.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /** Permisos otorgados a un rol (fuente: tabla roles_permissions). */
    @Query("""
            select p from RolePermission rp
            join rp.permission p
            where rp.role.id = :roleId
            order by p.name
            """)
    List<Permission> findByRoleId(@Param("roleId") Long roleId);

    List<Permission> findAllByOrderByNameAsc();
}
