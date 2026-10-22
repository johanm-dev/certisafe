package com.certisafe.auth.dto;

import com.certisafe.domain.Role;
import com.certisafe.security.UserPrincipal;

import java.util.List;

/**
 * Datos del usuario autenticado que el frontend necesita para pintar el menú
 * según rol y permisos. No expone nunca el hash de la contraseña.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String role,
        List<String> permissions
) {

    public static AuthenticatedUser from(UserPrincipal principal, String firstName, String lastName, Role role) {
        return new AuthenticatedUser(
                principal.getId(),
                principal.getEmail(),
                firstName,
                lastName,
                firstName + " " + lastName,
                role == null ? principal.getRoleName() : role.getName(),
                principal.permissionNames()
        );
    }
}
