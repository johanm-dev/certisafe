# CertiSafe

Sistema de gestión de capacitaciones y certificaciones en Seguridad y Salud en el Trabajo
(SST). Proyecto del **Reto 1** del SENA.

Tres roles con permisos granulares distintos — **ADMIN** (líder HSE), **INSTRUCTOR**
(especialista técnico) y **OPERATOR** (operario de planta) — sobre los talleres, las
inscripciones y las certificaciones.

---

## Estructura del repositorio

```
certisafe/
├── backend/     API REST en Spring Boot 3.5 + Java 17      <- README propio, léelo
├── database/    Esquema PostgreSQL (fuente de verdad del modelo de datos)
└── frontend/    Cliente Angular 22 + Angular Material      <- README propio
```

El detalle de la API, las decisiones de diseño y el estado de avance están en
**[`backend/README.md`](backend/README.md)**; el del cliente, en
**[`frontend/README.md`](frontend/README.md)**.

### Justificación técnica del Frontend (Angular 22 frente a React/Next.js)

Aunque el enunciado preliminar sugería React/Next.js, se seleccionó **Angular 22** con componentes independientes (*standalone*) y arquitectura reactiva por **Signals (zoneless)** para responder a la criticidad operativa de IndustrialCore:
1. **Tipado estricto y cohesión con Spring Boot:** El modelo fuertemente tipado de Angular garantiza paridad directa con los DTOs y validaciones Bean Validation de la API, reduciendo fallos de captura en certificaciones.
2. **Control de acceso granular (RBAC):** La infraestructura de guards funcionales (`authGuard`, `permissionGuard`) y directivas estructurales resuelve con elegancia la matriz de 19 permisos para los tres roles.
3. **Rendimiento y eficiencia:** El uso de Signals zoneless permite una reactividad granular en el DOM sin la sobrecarga de `zone.js`, logrando un bundle optimizado de menos de 160 kB en transferencia inicial.

---

## Requisitos previos

| Herramienta | Versión usada | Comprobar con |
|---|---|---|
| Java (Temurin) | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| PostgreSQL | 17 | `psql --version` |
| Node.js | 22+ | `node -v` |
| **pnpm** | 10+ | `pnpm -v` |

> El gestor de paquetes del frontend es **`pnpm`**. No se usa `npm` en ningún paso.

---

## Puesta en marcha

### 1. Base de datos

```powershell
psql -U postgres -h localhost -f "database/certisafe_schema.sql"
```

El esquema crea la base, los 3 tipos enumerados nativos (`workshop_status`,
`attendance_status`, `batch_status`), las 17 tablas, las 3 relaciones, 7 índices de negocio,
los triggers de `updated_at` y los datos semilla (roles y 19 permisos). **No siembra
usuarios** a propósito: no se versiona una contraseña.

Si tu base ya existía antes de la corrección de RNF-015, aplícale también
`database/alter_rnf015_lockout.sql`.

### 2. Backend

```powershell
cd backend
mvn spring-boot:run
```

- API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

Al primer arranque, con `users` vacía, se crea un ADMIN y sus credenciales quedan escritas en
el log:

```
correo     : admin@certisafe.local
contraseña : Admin123*
```

**Cámbialas antes de cualquier entrega.** El backend **no** crea ni altera tablas
(`ddl-auto: validate`): si una entidad no cuadra con el esquema, la aplicación no arranca.

La recuperación de contraseña (RF-023) manda el enlace por correo. Sin `MAIL_SMTP_HOST` no se
intenta SMTP: el enlace se escribe en el log del backend y se puede abrir desde ahí, que es lo
que permite probar el flujo completo sin servidor de correo. Para enviar de verdad:

| Variable | Para qué |
|---|---|
| `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT` | Servidor SMTP (el puerto por defecto es 587). |
| `MAIL_SMTP_USERNAME`, `MAIL_SMTP_PASSWORD` | Credenciales, si el servidor las pide. |
| `MAIL_FROM` | Remitente del correo. |
| `FRONTEND_URL` | Base del enlace que viaja en el correo. |
| `RECOVERY_TOKEN_TTL_MINUTES` | Vigencia del enlace (15 por defecto). |

### 3. Datos de demostración (opcional)

```powershell
psql -U postgres -h localhost -d Certisafe -f "database/seed_demo.sql"
```

Crea 6 talleres cubriendo los cuatro estados, con instructor, historial, dos inscripciones de
ejemplo y la certificación de la asistencia ya registrada (si existe el operario de
demostración). Es idempotente y no forma parte de la entrega.

Para dejar solo las inscripciones de ejemplo hace falta un OPERATOR con el correo
`operario@certisafe.local` (la contraseña sugerida es `Operario123*`); se crea desde la pantalla
**Usuarios**. Si no existe, el seed crea igual los talleres y avisa por consola. El sistema no
siembra usuarios porque no se versiona una contraseña.

Para volver a empezar de cero:

```powershell
psql -U postgres -h localhost -d Certisafe -f "database/reset_demo.sql"
psql -U postgres -h localhost -d Certisafe -f "database/seed_demo.sql"
```

### 4. Frontend

Angular 22 con Angular Material. El gestor de paquetes es **`pnpm`**, nunca `npm`.
Necesita el backend del paso 2 levantado, porque `ng serve` reenvía `/api` al 8080.

```powershell
cd frontend
pnpm install
pnpm start
```

Si `pnpm install` avisa de scripts de compilación bloqueados
(`ERR_PNPM_IGNORED_BUILDS`), ejecuta `pnpm approve-builds --all` y repite la instalación;
sin esas dependencias nativas el frontend no compila.

| Qué | Dónde |
|---|---|
| Aplicación | <http://localhost:4200> |
| API | <http://localhost:8080/api> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |

En la pantalla de acceso, ingrese las credenciales del usuario deseado para acceder al sistema.

### Pruebas

El backend tiene **48 comprobaciones de integración** (`mvn test`) sobre las reglas críticas
que RNF-014 nombra —aforo, transiciones de estado y carga masiva— más las de certificación,
cambio y recuperación de contraseña, con una carrera real de dos inscripciones al último cupo.
El frontend no tiene todavía pruebas; está anotado en su README.

---

## Cómo está organizado el trabajo

El repositorio se construyó por rebanadas verticales completas (esquema → entidad →
repositorio → servicio → controlador → prueba real contra la base), no por capas a medias:

1. **Autenticación** — login con JWT de acceso + refresh token opaco con rotación, logout,
   `/me`, bloqueo por intentos fallidos (RNF-015) y seguridad por permisos granulares.
2. **Usuarios y roles** — CRUD paginado y filtrado, activación/desactivación, protecciones
   para no quedarse sin administradores.
3. **Talleres** — CRUD paginado y filtrado, asignación de instructor con histórico, ciclo de
   vida con validación de transiciones (RNF-016) y auditoría en `status_history`.
4. **Frontend: talleres** — listado con paginación, filtros y ordenación del servidor; alta,
   edición, cambio de estado guiado por las transiciones que devuelve el backend, asignación
   de instructor, historial y borrado con sus reglas. Sin zone.js: el estado va en signals.
5. **Inscripciones y asistencia** — inscripción con control de aforo, reglas de rol
   (el INSTRUCTOR no inscribe), registro de asistencia solo con el taller en progreso o
   finalizado y recorte de datos por rol (el INSTRUCTOR solo ve sus talleres).
6. **Frontend: inscripciones y usuarios** — una sola pantalla de inscripciones que se adapta a
   los tres roles, con el aforo real en el listado de talleres, y la gestión de usuarios con
   borrado lógico.
7. **Certificaciones** — emisión automática al registrar la asistencia (RF-013), con la
   vigencia configurable en `configurations.certificate_validity_days`; historial paginado y
   filtrado (RF-016) y revocación con motivo para el ADMIN. El estado (vigente, vencida,
   revocada) se deriva de las fechas y de `revoked`, no se guarda duplicado.
8. **Carga y exportación masiva** — alta de operarios y talleres desde `.xlsx` o `.csv` con
   reporte por fila (RF-017, RF-018), inserción por lotes que se revierten completos si algo
   falla, y exportación en ambos formatos con los filtros del listado (RF-019). La interfaz
   tiene la pantalla de importación y un botón «Exportar» en cada listado, ambos para el ADMIN.
9. **Reporte de cumplimiento** — consulta por operario con su estado (vigente, vencida o sin
   certificar), resaltado de quienes no son aptos para zonas de alto riesgo, filtros por taller
   y por rango de fechas, y exportación con los mismos filtros (RF-020, HU-14). Solo el ADMIN.
10. **Cambio de contraseña** — cualquier usuario autenticado cambia la suya desde la cabecera:
    exige la contraseña actual, aplica la misma política de complejidad, cierra todas las
    sesiones y deja el rastro en `audit_logs` (RF-024, HU-18). Con freno propio de 5
    solicitudes por 15 minutos, como pide RNF-015.
11. **Recuperación de contraseña** — desde el acceso, sin sesión: se pide un enlace, se envía
    por correo (o al log si no hay SMTP), caduca en 15 minutos, sirve una sola vez y cierra
    todas las sesiones al canjearlo (RF-023, HU-17). La respuesta es siempre la misma exista o
    no la cuenta, y el endpoint tiene un freno de 3 peticiones por hora y por IP.

Pendiente: notificaciones y configuración, que tampoco tienen todavía backend.

---

## Documentación

La entrega escrita (requerimientos funcionales y no funcionales, historias de usuario,
diagramas de casos de uso, modelo relacional, diagrama de clases y BPMN) está fuera de este
repositorio, en los `.docx` y `.drawio` del paquete de entrega. El modelo relacional de esos
documentos **es** el de `database/certisafe_schema.sql`.
