package com.certisafe.workshop.dto;

import com.certisafe.domain.User;

/** Referencia ligera a un usuario, para no arrastrar el DTO completo de usuario en cada respuesta. */
public record PersonRef(Long id, String fullName, String email) {

    public static PersonRef from(User user) {
        return user == null ? null : new PersonRef(user.getId(), user.fullName(), user.getEmail());
    }
}
