package com.certisafe.security;

import com.certisafe.domain.Permission;
import com.certisafe.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Usuario autenticado que viaja en el SecurityContext.
 *
 * <p>Autoridades = {@code ROLE_<rol>} + cada permiso granular, p.ej. {@code users.create}.
 * Eso permite {@code @PreAuthorize("hasAuthority('users.create')")}.</p>
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String roleName;
    private final boolean active;

    /** Momento hasta el que la cuenta esta bloqueada (RNF-015). {@code null} = sin bloqueo. */
    private final OffsetDateTime lockedUntil;

    private final Collection<? extends GrantedAuthority> authorities;

    private UserPrincipal(Long id, String email, String password, String roleName,
                          boolean active, OffsetDateTime lockedUntil,
                          Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.roleName = roleName;
        this.active = active;
        this.lockedUntil = lockedUntil;
        this.authorities = authorities;
    }

    public static UserPrincipal of(User user, List<Permission> permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().getName(),
                Boolean.TRUE.equals(user.getActive()),
                user.getLockedUntil(),
                List.copyOf(authorities)
        );
    }

    public List<String> permissionNames() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .toList();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Lo consulta DaoAuthenticationProvider ANTES de comprobar la contrasena; si devuelve
     * false lanza LockedException, que AuthService traduce a 429 (RNF-015).
     */
    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || !lockedUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
