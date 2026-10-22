package com.certisafe.security;

import com.certisafe.domain.Permission;
import com.certisafe.domain.User;
import com.certisafe.repository.PermissionRepository;
import com.certisafe.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Carga el usuario desde la base de datos en cada peticion autenticada.
 *
 * <p>Se consulta la BD a proposito (en lugar de confiar en los permisos que vienen dentro
 * del JWT) para que un cambio de rol o de permisos tenga efecto inmediato y no haya que
 * esperar a que expire el access token.</p>
 */
@Service
@Transactional(readOnly = true)
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public AppUserDetailsService(UserRepository userRepository, PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        List<Permission> permissions = permissionRepository.findByRoleId(user.getRole().getId());
        return UserPrincipal.of(user, permissions);
    }
}
