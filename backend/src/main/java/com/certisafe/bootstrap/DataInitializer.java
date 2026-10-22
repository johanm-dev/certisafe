package com.certisafe.bootstrap;

import com.certisafe.domain.Role;
import com.certisafe.domain.User;
import com.certisafe.repository.RoleRepository;
import com.certisafe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer usuario ADMIN cuando la tabla {@code users} está vacía.
 *
 * <p>El esquema {@code certisafe_schema.sql} NO siembra usuarios (a propósito: no se
 * versiona una contraseña). Sin esto no habría forma de entrar al sistema la primera vez.
 * Se desactiva con {@code certisafe.bootstrap.enabled=false}.</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final boolean enabled;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminFirstName;
    private final String adminLastName;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            @Value("${certisafe.bootstrap.enabled:true}") boolean enabled,
            @Value("${certisafe.bootstrap.admin-email:admin@certisafe.local}") String adminEmail,
            @Value("${certisafe.bootstrap.admin-password:Admin123*}") String adminPassword,
            @Value("${certisafe.bootstrap.admin-first-name:Administrador}") String adminFirstName,
            @Value("${certisafe.bootstrap.admin-last-name:CertiSafe}") String adminLastName,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminFirstName = adminFirstName;
        this.adminLastName = adminLastName;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Bootstrap de usuario administrador deshabilitado (certisafe.bootstrap.enabled=false).");
            return;
        }

        if (userRepository.count() > 0) {
            return;
        }

        Role adminRole = roleRepository.findByName(Role.ADMIN).orElseThrow(() -> new IllegalStateException(
                "No existe el rol ADMIN. Ejecute database/certisafe_schema.sql antes de arrancar la aplicación."));

        User admin = new User();
        admin.setEmail(adminEmail.trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName(adminFirstName);
        admin.setLastName(adminLastName);
        admin.setRole(adminRole);
        admin.setActive(true);
        userRepository.save(admin);

        log.warn("""

                ================================================================
                 Usuario administrador inicial creado
                   correo     : {}
                   contraseña : {}
                 CAMBIE ESTA CONTRASEÑA antes de cualquier entrega o despliegue.
                ================================================================
                """, admin.getEmail(), adminPassword);
    }
}
