package com.certisafe.importer;

import com.certisafe.common.error.BusinessException;
import com.certisafe.domain.BatchStatus;
import com.certisafe.domain.ImportBatch;
import com.certisafe.domain.Role;
import com.certisafe.domain.User;
import com.certisafe.domain.Workshop;
import com.certisafe.importer.dto.ImportErrorResponse;
import com.certisafe.importer.dto.ImportResultResponse;
import com.certisafe.repository.RoleRepository;
import com.certisafe.repository.UserRepository;
import com.certisafe.security.UserPrincipal;
import com.certisafe.tabular.TabularData;
import com.certisafe.tabular.TabularFileReader;
import com.certisafe.tabular.TabularRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Carga masiva de operarios y talleres desde .xlsx o .csv (RF-017 y RF-018).
 *
 * <p>Reglas que aplica el servicio:</p>
 * <ul>
 *   <li>Se guardan las filas validas y se reportan las rechazadas con su numero de fila y el
 *       motivo (CA2). Un archivo con filas malas NO se descarta entero.</li>
 *   <li>Las filas se insertan por lotes de {@code certisafe.import.batch-size}; cada lote es
 *       una transaccion. Si un lote falla de forma inesperada se revierte completo y sus filas
 *       pasan al reporte (CA3).</li>
 *   <li>El lote y el reporte se guardan en transacciones aparte, para que sobrevivan al
 *       rollback que documenta el CA3.</li>
 *   <li>Las filas invalidas nunca llegan a la base: se validan antes, incluidos los correos
 *       repetidos dentro del archivo.</li>
 * </ul>
 *
 * <p><b>La contrasena es obligatoria en el archivo de usuarios.</b> No es un capricho: el
 * sistema no envia correos (RF-023 no esta implementado) y, si se generara una contrasena
 * aleatoria, el operario no tendria forma de conocerla. Se documenta como interpretacion en
 * el README, porque los documentos no enumeran las columnas del archivo.</p>
 */
@Service
public class ImportService {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SLASH_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SLASH_TIME_SECONDS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter SLASH_SHORT_TIME = DateTimeFormatter.ofPattern("d/M/yyyy H:mm");
    private static final DateTimeFormatter SLASH_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SLASH_SHORT_DAY = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final TabularFileReader reader;
    private final ImportBatchStore store;
    private final ImportBatchWriter writer;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final int maxRows;
    private final int batchSize;

    public ImportService(TabularFileReader reader,
                         ImportBatchStore store,
                         ImportBatchWriter writer,
                         UserRepository userRepository,
                         RoleRepository roleRepository,
                         PasswordEncoder passwordEncoder,
                         @Value("${certisafe.import.max-rows:5000}") int maxRows,
                         @Value("${certisafe.import.batch-size:500}") int batchSize) {
        this.reader = reader;
        this.store = store;
        this.writer = writer;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.maxRows = maxRows;
        this.batchSize = Math.max(1, batchSize);
    }

    // ------------------------------------------------------------------ usuarios

    public ImportResultResponse importUsers(MultipartFile file, UserPrincipal actor) {
        ImportEntityType type = ImportEntityType.USERS;
        TabularData data = prepare(file, type);

        Map<String, Role> roles = roleRepository.findAll().stream()
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .collect(Collectors.toMap(
                        role -> role.getName().toUpperCase(Locale.ROOT),
                        Function.identity()));

        ImportBatch batch = store.start(fileName(file), fileType(file), type.name(),
                data.rows().size(), actor.getId());

        Set<String> seenEmails = new HashSet<>();
        Map<String, Integer> firstRowByEmail = new HashMap<>();
        Run run = new Run();
        List<TabularRow> rows = data.rows();

        for (int start = 0; start < rows.size(); start += batchSize) {
            List<TabularRow> chunk = rows.subList(start, Math.min(start + batchSize, rows.size()));
            List<Prepared<User>> prepared = new ArrayList<>();
            List<ImportFailure> failures = new ArrayList<>();

            for (TabularRow row : chunk) {
                List<String> problems = new ArrayList<>();
                User user = buildUser(row, roles, problems, seenEmails, firstRowByEmail);
                if (problems.isEmpty()) {
                    prepared.add(new Prepared<>(row, user));
                    seenEmails.add(user.getEmail());
                    firstRowByEmail.putIfAbsent(user.getEmail(), row.number());
                } else {
                    failures.add(new ImportFailure(row.number(), String.join(". ", problems), row.values()));
                }
            }

            record(batch.getId(), failures, run);

            if (!prepared.isEmpty()) {
                try {
                    writer.writeUsers(prepared.stream().map(Prepared::entity).toList());
                    run.created += prepared.size();
                } catch (RuntimeException ex) {
                    run.unexpectedFailure = true;
                    List<ImportFailure> rolledBack = prepared.stream()
                            .map(item -> new ImportFailure(item.source().number(),
                                    "Error inesperado al guardar el lote: " + rootMessage(ex),
                                    item.source().values()))
                            .toList();
                    record(batch.getId(), rolledBack, run);
                }
            }
        }

        return finish(batch.getId(), type, file, rows.size(), run);
    }

    // ------------------------------------------------------------------ talleres

    public ImportResultResponse importWorkshops(MultipartFile file, UserPrincipal actor) {
        ImportEntityType type = ImportEntityType.WORKSHOPS;
        TabularData data = prepare(file, type);

        ImportBatch batch = store.start(fileName(file), fileType(file), type.name(),
                data.rows().size(), actor.getId());

        Map<String, User> instructors = new HashMap<>();
        Run run = new Run();
        List<TabularRow> rows = data.rows();

        for (int start = 0; start < rows.size(); start += batchSize) {
            List<TabularRow> chunk = rows.subList(start, Math.min(start + batchSize, rows.size()));
            List<Prepared<Workshop>> prepared = new ArrayList<>();
            List<ImportFailure> failures = new ArrayList<>();

            for (TabularRow row : chunk) {
                List<String> problems = new ArrayList<>();
                Workshop workshop = buildWorkshop(row, instructors, problems);
                if (problems.isEmpty()) {
                    prepared.add(new Prepared<>(row, workshop));
                } else {
                    failures.add(new ImportFailure(row.number(), String.join(". ", problems), row.values()));
                }
            }

            record(batch.getId(), failures, run);

            if (!prepared.isEmpty()) {
                try {
                    writer.writeWorkshops(prepared.stream().map(Prepared::entity).toList(), actor.getId());
                    run.created += prepared.size();
                } catch (RuntimeException ex) {
                    run.unexpectedFailure = true;
                    List<ImportFailure> rolledBack = prepared.stream()
                            .map(item -> new ImportFailure(item.source().number(),
                                    "Error inesperado al guardar el lote: " + rootMessage(ex),
                                    item.source().values()))
                            .toList();
                    record(batch.getId(), rolledBack, run);
                }
            }
        }

        return finish(batch.getId(), type, file, rows.size(), run);
    }

    // ------------------------------------------------------------------ validacion de usuarios

    private User buildUser(TabularRow row, Map<String, Role> roles, List<String> problems,
                           Set<String> seenEmails, Map<String, Integer> firstRowByEmail) {

        String rawEmail = row.get("correo");
        String email = normalizeEmail(rawEmail);
        if (email.isEmpty()) {
            problems.add("El correo electrónico es obligatorio");
        } else if (email.length() > 255) {
            problems.add("El correo no puede superar los 255 caracteres");
        } else if (!EMAIL.matcher(email).matches()) {
            problems.add("El correo \"" + rawEmail + "\" no es válido");
        } else if (seenEmails.contains(email)) {
            problems.add("Correo repetido en el archivo (ya aparece en la fila "
                    + firstRowByEmail.get(email) + ")");
        } else if (userRepository.existsByEmail(email)) {
            problems.add("Ya existe un usuario con el correo " + email);
        }

        String firstName = requiredText(row.get("nombres"), 100, "El nombre", problems);
        String lastName = requiredText(row.get("apellidos"), 100, "El apellido", problems);

        String password = row.get("contrasena");
        if (password.isBlank()) {
            problems.add("La contraseña es obligatoria");
        } else if (password.length() < 8 || password.length() > 128) {
            problems.add("La contraseña debe tener entre 8 y 128 caracteres");
        } else if (!PASSWORD.matcher(password).matches()) {
            problems.add("La contraseña debe incluir mayúscula, minúscula, dígito y carácter especial");
        }

        String phone = optionalText(row.get("telefono"), 30, "El teléfono", problems);
        Role role = resolveRole(row.get("rol"), roles, problems);

        if (!problems.isEmpty()) {
            return null;
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private Role resolveRole(String raw, Map<String, Role> roles, List<String> problems) {
        String value = raw == null ? "" : raw.trim();
        String canonical;
        if (value.isEmpty()) {
            canonical = Role.OPERATOR;
        } else {
            canonical = switch (key(value)) {
                case "admin", "administrador", "administradora" -> Role.ADMIN;
                case "instructor", "instructora", "docente" -> Role.INSTRUCTOR;
                case "operator", "operario", "operaria", "operador", "operadora" -> Role.OPERATOR;
                default -> null;
            };
        }
        if (canonical == null) {
            problems.add("Rol no válido: \"" + value + "\". Use ADMIN, INSTRUCTOR u OPERATOR");
            return null;
        }
        Role role = roles.get(canonical);
        if (role == null) {
            problems.add("El rol " + canonical + " no está activo");
        }
        return role;
    }

    // ------------------------------------------------------------------ validacion de talleres

    private Workshop buildWorkshop(TabularRow row, Map<String, User> instructors, List<String> problems) {
        String name = requiredText(row.get("nombre"), 200, "El nombre del taller", problems);
        String description = optionalText(row.get("descripcion"), 2000, "La descripción", problems);
        OffsetDateTime date = parseDate(row.get("fecha"), problems);
        Integer duration = parseInteger(row.get("duracion_minutos"), 1, 1440,
                "La duración (duracion_minutos)", problems);
        Integer capacity = parseInteger(row.get("aforo"), 1, 1000, "El aforo", problems);
        String location = requiredText(row.get("lugar"), 300, "El lugar", problems);
        User instructor = resolveInstructor(row.get("correo_instructor"), instructors, problems);

        if (!problems.isEmpty()) {
            return null;
        }

        Workshop workshop = new Workshop();
        workshop.setName(name);
        workshop.setDescription(description);
        workshop.setWorkshopDate(date);
        workshop.setDurationMinutes(duration);
        workshop.setMaxCapacity(capacity);
        workshop.setLocation(location);
        workshop.setInstructor(instructor);
        return workshop;
    }

    private User resolveInstructor(String raw, Map<String, User> cache, List<String> problems) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String email = normalizeEmail(value);
        if (!cache.containsKey(email)) {
            cache.put(email, userRepository.findByEmail(email).orElse(null));
        }
        User user = cache.get(email);
        if (user == null) {
            problems.add("Instructor no encontrado: " + email);
            return null;
        }
        if (!Role.INSTRUCTOR.equals(user.getRole().getName())) {
            problems.add("El usuario " + email + " no tiene rol INSTRUCTOR");
            return null;
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            problems.add("El instructor " + email + " está inactivo");
            return null;
        }
        return user;
    }

    private OffsetDateTime parseDate(String raw, List<String> problems) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            problems.add("La fecha es obligatoria");
            return null;
        }

        OffsetDateTime parsed = tryOffset(value);
        if (parsed == null) {
            parsed = tryLocalDateTime(value);
        }
        if (parsed == null) {
            parsed = tryLocalDate(value);
        }
        if (parsed == null) {
            problems.add("La fecha \"" + value + "\" no es válida. Use el formato aaaa-mm-dd hh:mm");
            return null;
        }
        if (!parsed.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            problems.add("La fecha del taller debe ser futura");
            return null;
        }
        return parsed;
    }

    private static OffsetDateTime tryOffset(String value) {
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static OffsetDateTime tryLocalDateTime(String value) {
        for (DateTimeFormatter formatter : List.of(DAY_TIME, DAY_TIME_SECONDS, SLASH_TIME,
                SLASH_TIME_SECONDS, SLASH_SHORT_TIME)) {
            try {
                return LocalDateTime.parse(value, formatter)
                        .atZone(ZoneId.systemDefault())
                        .toOffsetDateTime()
                        .withOffsetSameInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toOffsetDateTime()
                    .withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static OffsetDateTime tryLocalDate(String value) {
        for (DateTimeFormatter formatter : List.of(SLASH_DAY, SLASH_SHORT_DAY)) {
            try {
                return LocalDate.parse(value, formatter)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toOffsetDateTime()
                        .withOffsetSameInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toOffsetDateTime()
                    .withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Integer parseInteger(String raw, int min, int max, String label, List<String> problems) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            problems.add(label + " tiene un valor obligatorio");
            return null;
        }
        try {
            // Excel a veces entrega "30.0" cuando la celda es numerica.
            String normalized = value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
            int number = Integer.parseInt(normalized);
            if (number < min || number > max) {
                problems.add(label + " debe estar entre " + min + " y " + max);
                return null;
            }
            return number;
        } catch (NumberFormatException e) {
            problems.add(label + " debe ser un número entero");
            return null;
        }
    }

    // ------------------------------------------------------------------ infraestructura

    private TabularData prepare(MultipartFile file, ImportEntityType type) {
        TabularData data = reader.read(file);
        List<String> missing = type.requiredHeaders().stream()
                .filter(header -> !data.hasHeader(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("Faltan columnas obligatorias: " + String.join(", ", missing)
                    + ". Descargue la plantilla para ver el formato esperado");
        }
        if (data.rows().size() > maxRows) {
            throw new BusinessException("El archivo tiene " + data.rows().size()
                    + " filas y el máximo permitido es " + maxRows);
        }
        return data;
    }

    private void record(Long batchId, List<ImportFailure> failures, Run run) {
        if (failures.isEmpty()) {
            return;
        }
        store.addErrors(batchId, failures);
        failures.forEach(failure -> run.errors.add(new ImportErrorResponse(
                failure.rowNumber(), failure.reason(), failure.data())));
    }

    private ImportResultResponse finish(Long batchId, ImportEntityType type, MultipartFile file,
                                        int totalRows, Run run) {
        int rejected = totalRows - run.created;
        BatchStatus status;
        if (run.created == 0 && run.unexpectedFailure) {
            status = BatchStatus.FALLIDO;
        } else if (rejected == 0) {
            status = BatchStatus.COMPLETADO;
        } else {
            status = BatchStatus.CON_ERRORES;
        }
        store.finish(batchId, run.created, rejected, status);

        return new ImportResultResponse(batchId, type.name(), fileName(file), fileType(file),
                totalRows, run.created, rejected, status, List.copyOf(run.errors));
    }

    private static String requiredText(String raw, int max, String label, List<String> problems) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            problems.add(label + " es obligatorio");
            return null;
        }
        if (value.length() > max) {
            problems.add(label + " no puede superar los " + max + " caracteres");
            return null;
        }
        return value;
    }

    private static String optionalText(String raw, int max, String label, List<String> problems) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > max) {
            problems.add(label + " no puede superar los " + max + " caracteres");
            return null;
        }
        return value;
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Minusculas sin tildes, para comparar "Administrador" con "ADMIN". */
    private static String key(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "archivo";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String clean = slash >= 0 ? name.substring(slash + 1) : name;
        return clean.length() > 255 ? clean.substring(0, 255) : clean;
    }

    private static String fileType(MultipartFile file) {
        String name = fileName(file);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    /** Acumulador de una carga; se pasa por referencia para no repetir seis parametros. */
    private static final class Run {
        private int created;
        private boolean unexpectedFailure;
        private final List<ImportErrorResponse> errors = new ArrayList<>();
    }

    private record Prepared<T>(TabularRow source, T entity) {
    }
}
