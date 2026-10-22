package com.certisafe.user;

import com.certisafe.common.PageResponse;
import com.certisafe.security.UserPrincipal;
import com.certisafe.user.dto.StatusUpdateRequest;
import com.certisafe.user.dto.UserCreateRequest;
import com.certisafe.user.dto.UserResponse;
import com.certisafe.user.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de usuarios (RF de módulo USERS).
 * Cada endpoint está protegido por el permiso granular sembrado en la tabla permissions.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuarios", description = "CRUD de usuarios con paginación obligatoria")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users.read')")
    @Operation(summary = "Listar usuarios (paginado)",
            description = "Filtros opcionales: search, roleId, active. Ordenación con sort=campo,direccion.")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return userService.list(search, roleId, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users.read')")
    @Operation(summary = "Obtener un usuario por id")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('users.create')")
    @Operation(summary = "Crear usuario")
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users.update')")
    @Operation(summary = "Actualizar usuario", description = "No modifica la contraseña.")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('users.delete')")
    @Operation(summary = "Activar / desactivar usuario",
            description = "Borrado lógico. No permite desactivar el propio usuario ni al último ADMIN activo.")
    public UserResponse changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody StatusUpdateRequest request,
                                     @AuthenticationPrincipal UserPrincipal actor) {
        return userService.changeStatus(id, request.active(), actor == null ? null : actor.getId());
    }
}
