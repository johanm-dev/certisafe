package com.certisafe.user.dto;

import com.certisafe.domain.Role;
import com.certisafe.domain.User;

import java.time.OffsetDateTime;

/** Representación pública de un usuario. Nunca incluye {@code password_hash}. */
public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        RoleResponse role,
        Boolean active,
        OffsetDateTime lastAccess,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.fullName(),
                user.getPhone(),
                RoleResponse.from(user.getRole()),
                user.getActive(),
                user.getLastAccess(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public record RoleResponse(Long id, String name, String description) {

        public static RoleResponse from(Role role) {
            return role == null ? null : new RoleResponse(role.getId(), role.getName(), role.getDescription());
        }
    }
}
