package com.certisafe;

import com.certisafe.auth.AuthService;
import com.certisafe.auth.PasswordChangeThrottle;
import com.certisafe.certificate.CertificateService;
import com.certisafe.domain.Role;
import com.certisafe.domain.User;
import com.certisafe.enrollment.EnrollmentService;
import com.certisafe.repository.AuditLogRepository;
import com.certisafe.repository.PermissionRepository;
import com.certisafe.repository.RoleRepository;
import com.certisafe.repository.UserRepository;
import com.certisafe.security.UserPrincipal;
import jakarta.validation.Validator;
import com.certisafe.workshop.WorkshopService;
import com.certisafe.workshop.dto.WorkshopCreateRequest;
import com.certisafe.workshop.dto.WorkshopResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Utilidades comunes de las pruebas de reglas de negocio.
 *
 * <p>Cada prueba crea sus propios talleres, instructores y operarios en lugar de apoyarse en
 * los datos de demostracion, porque la base de la aplicacion esta viva mientras se escriben
 * las pruebas. Con la transaccion de prueba todo se deshace al terminar.</p>
 *
 * <p>No lleva <code>@SpringBootTest</code> ni <code>@Transactional</code>: son de cada clase
 * hija, y una de ellas (la de concurrencia) necesita lo contrario.</p>
 */
abstract class ReglasDeNegocioIT {

    /** La misma clave para todas las cuentas de prueba: BCrypt la hashea al crearlas. */
    protected static final String CLAVE_DE_PRUEBA = "Prueba123*";

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PermissionRepository permissionRepository;

    @Autowired
    protected WorkshopService workshopService;

    @Autowired
    protected EnrollmentService enrollmentService;

    @Autowired
    protected CertificateService certificateService;

    @Autowired
    protected AuthService authService;

    @Autowired
    protected PasswordChangeThrottle passwordChangeThrottle;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @Autowired
    protected Validator validator;

    protected String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected User crearAdministrador(String sufijo) {
        return crearUsuario("admin." + sufijo, Role.ADMIN);
    }

    protected User crearInstructor(String sufijo) {
        return crearUsuario("instructor." + sufijo, Role.INSTRUCTOR);
    }

    protected User crearOperario(String sufijo) {
        return crearUsuario("operario." + sufijo, Role.OPERATOR);
    }

    /** Cuenta que la prueba crea y que al cerrarse la transaccion deja de existir. */
    protected User crearUsuario(String nombreCorto, String nombreRol) {
        Role rol = roleRepository.findByName(nombreRol).orElseThrow();
        User usuario = new User();
        usuario.setEmail(nombreCorto + "@certisafe.test");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE_DE_PRUEBA));
        usuario.setFirstName("Prueba");
        usuario.setLastName(nombreCorto);
        usuario.setRole(rol);
        usuario.setActive(true);
        return userRepository.save(usuario);
    }

    /** Principal con los permisos del rol, igual que se construye en cada peticion. */
    protected UserPrincipal principal(Long userId) {
        User usuario = userRepository.findById(userId).orElseThrow();
        return UserPrincipal.of(usuario, permissionRepository.findByRoleId(usuario.getRole().getId()));
    }

    protected Long crearTaller(Long adminId, String sufijo, int cupos) {
        WorkshopResponse creado = workshopService.create(peticionTaller(sufijo, cupos), adminId);
        return creado.id();
    }

    protected static WorkshopCreateRequest peticionTaller(String sufijo, int cupos) {
        return new WorkshopCreateRequest(
                "Taller de prueba " + sufijo,
                "Descripcion de prueba",
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                120,
                "Sala de pruebas",
                cupos);
    }

    protected String statusDe(Long tallerId, Long adminId) {
        return workshopService.get(tallerId, principal(adminId)).status();
    }
}
