package com.certisafe.role;

import com.certisafe.repository.RoleRepository;
import com.certisafe.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catálogo de roles. Lo consume el formulario de usuarios del frontend para poblar
 * el selector de rol. No se permite crear/editar roles por API en esta versión:
 * los roles son fijos (ADMIN, INSTRUCTOR, OPERATOR) y vienen del esquema.
 */
@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "Catálogo de roles del sistema")
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users.read')")
    @Operation(summary = "Listar roles activos")
    public List<UserResponse.RoleResponse> list() {
        return roleRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .map(UserResponse.RoleResponse::from)
                .toList();
    }
}
