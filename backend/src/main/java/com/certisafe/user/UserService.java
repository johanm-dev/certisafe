package com.certisafe.user;

import com.certisafe.common.PageResponse;
import com.certisafe.common.error.BusinessException;
import com.certisafe.common.error.NotFoundException;
import com.certisafe.domain.Role;
import com.certisafe.domain.User;
import com.certisafe.repository.RoleRepository;
import com.certisafe.repository.UserRepository;
import com.certisafe.user.dto.UserCreateRequest;
import com.certisafe.user.dto.UserResponse;
import com.certisafe.user.dto.UserUpdateRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Listado paginado con filtros opcionales.
     *
     * <p>La paginacion la resuelve Spring Data ({@link Pageable}); el tamaño maximo de
     * pagina lo impone {@code spring.data.web.pageable.max-page-size} en application.yml.</p>
     */
    public PageResponse<UserResponse> list(String search, Long roleId, Boolean active, Pageable pageable) {
        return PageResponse.of(userRepository.findAll(filter(search, roleId, active), pageable),
                UserResponse::from);
    }

    /**
     * Todas las filas que cumplen los filtros, sin paginar. Solo lo usa la exportacion
     * (RF-019), que es ADMIN-only: por eso no aplica el alcance por rol que si tiene el listado.
     */
    @Transactional(readOnly = true)
    public List<User> findAllForExport(String search, Long roleId, Boolean active) {
        return userRepository.findAll(filter(search, roleId, active),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Specification<User> filter(String search, Long roleId, Boolean active) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("firstName")), like),
                    cb.like(cb.lower(root.get("lastName")), like)));
        }
        if (roleId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role").get("id"), roleId));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return spec;
    }

    public UserResponse get(Long id) {
        return UserResponse.from(findOrFail(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw BusinessException.conflict("Ya existe un usuario con el correo " + email);
        }
        Role role = requireActiveRole(request.roleId());

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(trimToNull(request.phone()));
        user.setRole(role);
        user.setActive(request.active() == null || request.active());

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findOrFail(id);
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw BusinessException.conflict("Ya existe otro usuario con el correo " + email);
        }

        Role role = requireActiveRole(request.roleId());
        boolean wouldDeactivate = !Boolean.TRUE.equals(request.active());
        boolean wouldChangeRole = !user.getRole().getId().equals(role.getId());

        if (isLastActiveAdmin(user) && (wouldDeactivate || wouldChangeRole)) {
            throw BusinessException.conflict(
                    "No se puede desactivar ni cambiar de rol al último administrador activo del sistema");
        }

        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(trimToNull(request.phone()));
        user.setRole(role);
        user.setActive(request.active());

        return UserResponse.from(userRepository.save(user));
    }

    /** Activar o desactivar (borrado lógico). No se borran filas físicamente. */
    @Transactional
    public UserResponse changeStatus(Long id, boolean active, Long actorId) {
        User user = findOrFail(id);

        if (!active && id.equals(actorId)) {
            throw BusinessException.conflict("No puede desactivar su propio usuario");
        }
        if (!active && isLastActiveAdmin(user)) {
            throw BusinessException.conflict("No se puede desactivar al último administrador activo del sistema");
        }

        user.setActive(active);
        return UserResponse.from(userRepository.save(user));
    }

    private User findOrFail(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("Usuario", id));
    }

    private Role requireActiveRole(Long roleId) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> NotFoundException.of("Rol", roleId));
        if (!Boolean.TRUE.equals(role.getActive())) {
            throw new BusinessException("El rol " + role.getName() + " está inactivo");
        }
        return role;
    }

    /** Un ADMIN activo que además es el único ADMIN activo: no se puede dejar el sistema sin administrador. */
    private boolean isLastActiveAdmin(User user) {
        if (!Role.ADMIN.equals(user.getRole().getName()) || !Boolean.TRUE.equals(user.getActive())) {
            return false;
        }
        return userRepository.countByRoleIdAndActiveTrue(user.getRole().getId()) <= 1;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
