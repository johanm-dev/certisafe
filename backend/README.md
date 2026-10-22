# CertiSafe — Backend (Spring Boot)

API REST del sistema de gestión de talleres de seguridad industrial y certificaciones.

- **Java 17** · **Spring Boot 3.5.16** · **Maven**
- **PostgreSQL 17** · **Hibernate/JPA** · **JWT (jjwt 0.12.7)** · **springdoc OpenAPI 2.9.1**
- **Apache POI 5.4.1** y **commons-csv 1.12.0** para la carga/exportación masiva (RF-017…019)
- Roles: `ADMIN`, `INSTRUCTOR`, `OPERATOR` · Nomenclatura de BD en inglés (RNF-003)

---

## 1. Requisitos previos

| Herramienta | Versión verificada en este equipo |
|---|---|
| JDK | Temurin 17.0.20.1 |
| Maven | 3.9.16 |
| PostgreSQL | 17 (servicio `postgresql-x64-17`) |

---

## 2. Base de datos

El modelo de datos **es autoritativo**: lo define `database/certisafe_schema.sql` (17 tablas,
3 tipos ENUM nativos, triggers de `updated_at` y datos semilla).

En este equipo ya existe la base **`Certisafe`** con el esquema aplicado:

```powershell
psql -U postgres -h localhost -d Certisafe -f "database/certisafe_schema.sql"
```

Si tu base ya existía **antes de esta corrección**, aplícale la migración que añade las
columnas de bloqueo por intentos fallidos (RNF-015). Si la creas desde cero, el esquema ya
las trae y este paso sobra:

```powershell
psql -U postgres -h localhost -d Certisafe -f "database/alter_rnf015_lockout.sql"
```

> **Ojo con el nombre.** La base se llama `Certisafe` con mayúscula inicial (fue creada entre
> comillas). Si prefieres la convención en minúsculas, crea `certisafe` y arranca con
> `DB_NAME=certisafe`.

Datos semilla que ya vienen del esquema: 3 roles, 19 permisos, sus asignaciones por rol y
8 parámetros en `configurations`. **No** vienen usuarios (ver punto 4).

### `ddl-auto: validate`

La aplicación **no** crea ni modifica tablas. Hibernate solo verifica que las entidades
coincidan con el esquema; si una entidad no cuadra, la aplicación **no arranca**. Es
intencional: garantiza que el modelo de datos de la entrega y el código no se separen.

---

## 3. Arranque

```powershell
cd certisafe/backend
mvn spring-boot:run
```

- API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

### Datos de demostración (opcional)

Para probar la interfaz con talleres reales en lugar de una tabla vacía:

```powershell
psql -U postgres -h localhost -d Certisafe -f "database/seed_demo.sql"
```

Crea 6 talleres que cubren los cuatro estados, con instructor asignado, historial de
transiciones y una cancelación con motivo. Es idempotente (ejecutarlo dos veces no duplica
nada) y **no forma parte de la entrega**: el esquema oficial sigue sin sembrar datos.

### Variables de entorno

Todas tienen valor por defecto para desarrollo local.

| Variable | Defecto | Para qué |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `Certisafe` | Conexión PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | Credenciales |
| `JWT_SECRET` | *(clave de desarrollo)* | **Obligatorio cambiarlo fuera de local. Mínimo 32 caracteres.** |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Vida del access token |
| `JWT_REFRESH_TTL_DAYS` | `7` | Vida del refresh token |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Orígenes permitidos (Angular) |
| `BOOTSTRAP_ENABLED` | `true` | Crear el admin inicial |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | `admin@certisafe.local` / `Admin123*` | Credenciales del admin inicial |
| `MAIL_SMTP_HOST` | *(vacío)* | Servidor SMTP. **Vacío = el enlace de recuperación se escribe en el log**; con valor se envía de verdad |
| `MAIL_SMTP_PORT` / `MAIL_SMTP_USERNAME` / `MAIL_SMTP_PASSWORD` | `587` / *(vacío)* / *(vacío)* | Conexión SMTP |
| `MAIL_FROM` | `no-responder@certisafe.local` | Remitente |
| `FRONTEND_URL` | `http://localhost:4200` | Base del enlace que viaja en el correo |
| `RECOVERY_TOKEN_TTL_MINUTES` | `15` | Vigencia del enlace de recuperación |

---

## 4. Primer acceso

El esquema no siembra usuarios a propósito (no se versiona una contraseña). Al arrancar con
la tabla `users` vacía, `DataInitializer` crea un ADMIN y lo deja escrito en el log:

```
correo     : admin@certisafe.local
contraseña : Admin123*
```

**Cámbiala antes de cualquier entrega.** Para desactivar este comportamiento:
`BOOTSTRAP_ENABLED=false`.

---

## 5. Endpoints implementados

| Método | Ruta | Permiso requerido |
|---|---|---|
| `POST` | `/api/auth/login` | público |
| `POST` | `/api/auth/refresh` | público |
| `POST` | `/api/auth/logout` | público |
| `GET` | `/api/auth/me` | autenticado |
| `PUT` | `/api/auth/change-password` | autenticado |
| `POST` | `/api/auth/recovery-request` | público |
| `POST` | `/api/auth/reset-password` | público |
| `GET` | `/api/users` | `users.read` |
| `GET` | `/api/users/{id}` | `users.read` |
| `POST` | `/api/users` | `users.create` |
| `PUT` | `/api/users/{id}` | `users.update` |
| `PATCH` | `/api/users/{id}/status` | `users.delete` |
| `GET` | `/api/roles` | `users.read` |
| `GET` | `/api/workshops` | `workshops.read` |
| `GET` | `/api/workshops/{id}` | `workshops.read` |
| `GET` | `/api/workshops/{id}/history` | `workshops.read` |
| `POST` | `/api/workshops` | `workshops.create` |
| `PUT` | `/api/workshops/{id}` | `workshops.update` |
| `PATCH` | `/api/workshops/{id}/status` | `workshops.change_status` |
| `PUT` | `/api/workshops/{id}/instructor` | `workshops.assign_instructor` |
| `DELETE` | `/api/workshops/{id}` | `workshops.delete` |
| `GET` | `/api/enrollments` | `enrollments.manage` |
| `GET` | `/api/enrollments/me` | autenticado |
| `POST` | `/api/enrollments` | `enrollments.manage` |
| `PATCH` | `/api/enrollments/{id}/attendance` | `enrollments.manage` |
| `DELETE` | `/api/enrollments/{id}` | `enrollments.delete` |
| `GET` | `/api/certificates` | `certificates.view` |
| `GET` | `/api/certificates/{id}` | `certificates.view` |
| `PATCH` | `/api/certificates/{id}/revoke` | `certificates.revoke` |
| `POST` | `/api/users/bulk-upload` | `import.execute` |
| `POST` | `/api/workshops/bulk-upload` | `import.execute` |
| `GET` | `/api/imports/templates/{entity}` | `import.execute` |
| `GET` | `/api/export/users` | `export.execute` |
| `GET` | `/api/export/workshops` | `export.execute` |
| `GET` | `/api/export/enrollments` | `export.execute` |
| `GET` | `/api/reports/compliance` | `reports.view` |
| `GET` | `/api/reports/compliance/export` | `reports.view` |

Los permisos son los 19 reales de la tabla `permissions` y se evalúan con
`@PreAuthorize("hasAuthority('...')")`. Un `INSTRUCTOR` recibe **403** al llamar a
`/api/users`, porque su rol no tiene `users.read`.

### Paginación

`GET /api/users` acepta `page`, `size`, `sort` y filtros `search`, `roleId`, `active`.
El tamaño de página está limitado a **100** (`spring.data.web.pageable.max-page-size`);
pedir `size=500` devuelve `size=100`.

Formato de respuesta (contrato fijo que consumirá Angular):

```json
{
  "content": [ { "id": 4, "email": "...", "role": { "id": 1, "name": "ADMIN" }, "active": true } ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1, "first": true, "last": true
}
```

### Errores

Formato único para todo fallo (400, 401, 403, 404, 409, 429, 500). El **429 Too Many
Requests** es el bloqueo de cuenta por intentos fallidos de RNF-015:

```json
{
  "timestamp": "2026-10-19T01:59:13.97-05:00",
  "status": 409,
  "error": "Conflict",
  "message": "Ya existe un usuario con el correo juan@certisafe.local",
  "path": "/api/users",
  "fieldErrors": { "email": "Debe ser una dirección de correo electrónico válida" }
}
```

`fieldErrors` solo aparece en errores de validación (400).

### Talleres y RNF-016

El ciclo de vida de un taller es una máquina de estados que vive en el enum `WorkshopStatus`
(no en el servicio), porque RNF-016 la define como regla de negocio:

| Estado actual | Puede pasar a |
|---|---|
| `PROGRAMADO` | `EN_PROGRESO`, `CANCELADO` |
| `EN_PROGRESO` | `FINALIZADO`, `CANCELADO` |
| `FINALIZADO` | — (estado final) |
| `CANCELADO` | — (estado final) |

Cualquier otra transición devuelve **409** con el detalle de las que sí se permiten, y toda
transición aceptada se graba en `status_history` con autor, fecha/hora y motivo. Cancelar
exige motivo (**400** si falta). La creación del taller también deja su fila en el historial,
con `previous_status` nulo.

`GET /api/workshops` acepta `page`, `size`, `sort` y los filtros `search` (nombre o lugar),
`status`, `instructorId`, `from` y `to` (ISO-8601 **con offset**, por ejemplo
`2026-11-01T08:00:00Z`). Un `?status=LOQUESEA` devuelve **400**, no 500.

Cada respuesta de taller incluye además `allowedTransitions`: el servidor calcula las
transiciones válidas con la misma regla que aplica el cambio de estado, para que el frontend
pueda deshabilitar los botones inválidos sin duplicar la lógica del ciclo de vida.

**Reglas de borrado.** `DELETE /api/workshops/{id}` solo procede si el taller está
`PROGRAMADO` y no tiene inscripciones. Para anular un taller en curso o finalizado se usa
`CANCELADO`: borrarlo arrastraría en cascada inscripciones y certificados
(`ON DELETE CASCADE`), que es justo lo que no se quiere.

**Matriz de permisos del módulo.** Nótese que un INSTRUCTOR puede avanzar el ciclo de vida
pero no crear ni editar:

| Acción | ADMIN | INSTRUCTOR | OPERATOR |
|---|:---:|:---:|:---:|
| Ver talleres | sí | sí | sí |
| Crear / editar / borrar | sí | — | — |
| Cambiar estado | sí | sí | — |
| Asignar instructor | sí | — | — |

### Carga masiva y exportación (RF-017, RF-018, RF-019)

Las rutas son las de las HU-11, HU-12 y HU-13, sin inventar un `/api/imports` genérico:

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/users/bulk-upload` | Alta masiva de operarios (multipart `file`) |
| `POST` | `/api/workshops/bulk-upload` | Alta masiva de talleres (multipart `file`) |
| `GET` | `/api/imports/templates/{entity}?format=csv\|xlsx` | Plantilla con los encabezados exactos |
| `GET` | `/api/export/{users\|workshops\|enrollments}?format=csv\|xlsx` | Exporta con los filtros del listado |

**Cómo se procesa un archivo.** Las filas se validan **antes** de tocar la base; luego se
insertan por lotes de `certisafe.import.batch-size` (500), **cada lote en su propia
transacción**. Si un lote se cae por algo que la validación no pudo ver (otra carga simultánea
que insertó el mismo correo, por ejemplo), se revierte **completo** y sus filas pasan al
reporte como rechazadas: no quedan datos a medias, que es el CA3 de la HU-11. El lote se
registra en `import_batches` y cada rechazo en `import_errors` (`row_number`, `error_reason` y
`row_data` en `jsonb`), y ambos se guardan con `REQUIRES_NEW` para que **sobrevivan al rollback
que los provocó**. Se cumple así, a la vez, «guardar las filas válidas» (CA2) y «revertir el
lote» (CA3), que en una lectura literal chocan.

El tope es de **5000 filas** por archivo (`certisafe.import.max-rows`) y la respuesta trae
`totalRows`, `createdRows`, `rejectedRows`, el estado del lote y el detalle de cada rechazo.
Invariante: `totalRows = createdRows + rejectedRows`. Estado `COMPLETADO` si no hubo rechazos,
`CON_ERRORES` si se guardó algo y algo se rechazó, y `FALLIDO` si un lote inesperado se
revirtió sin que se guardara ninguna fila.

**Lectura de los archivos.** El CSV se lee detectando el delimitador (`,` o `;`, porque Excel
en español guarda con punto y coma) y la codificación (UTF-8 y, si los bytes no son UTF-8
válidos, Windows-1252, que es lo que produce Excel al guardar como CSV). El `.xlsx` se lee con
**Apache POI** (`DataFormatter` + `getLocalDateTimeCellValue`), la librería que nombra la HU-11.
Los encabezados se normalizan (minúsculas, sin tildes, espacios a `_`), de modo que «Correo
electrónico» y `correo_electronico` son la misma columna, y faltar una columna obligatoria
devuelve **400** diciendo cuál.

**Archivo de usuarios.** Columnas: `correo`, `nombres`, `apellidos`, `rol`, `contrasena`,
`telefono`. `rol` es opcional y por defecto **OPERATOR**; `contrasena` es **obligatoria**. Esto
último es una **interpretación que se aparta de lo habitual y queda anotada**: los documentos
no enumeran las columnas, y la carga masiva **no avisa a nadie por correo**; la recuperación de
RF-023 existe, pero enviar el enlace a un operario de prueba con un correo inventado no sirve de
nada. Si la contraseña se generara sola, el operario no tendría forma de conocerla y la cuenta
sería inservible. Se validan el formato del correo, la política de contraseña y el rol, y se
rechazan los correos repetidos dentro del archivo y los que ya existen en la base.

**Archivo de talleres.** Columnas: `nombre`, `descripcion`, `fecha`, `duracion_minutos`,
`aforo`, `lugar`, `correo_instructor`. La fecha debe ser **futura** (misma regla que el alta
individual), la duración va de 1 a 1440 minutos y el aforo de 1 a 1000. Un
`correo_instructor` que no exista, que no sea INSTRUCTOR o que esté inactivo se rechaza con su
motivo. Los talleres nacen en `PROGRAMADO` y **dejan su fila en `status_history`** igual que el
alta individual (RNF-016): sin eso, un taller creado por carga masiva aparecería sin origen en
su historial.

**Exportación.** Cada endpoint acepta los **mismos filtros que su listado** y los reutiliza
(los servicios exponen `findAllForExport`, que comparte la construcción de la consulta con el
listado paginado; duplicarla habría permitido que un día el listado y la exportación devolvieran
conjuntos distintos). El CSV sale con BOM UTF-8 y comillas para Excel, y todas las celdas del
`.xlsx` se escriben como texto para que un código como `00123` no se convierta en `123`. Un
resultado vacío devuelve **400 con «No hay datos para exportar»**, en lugar de un archivo sin
filas. Las fechas salen en la zona horaria del servidor —la misma en la que el frontend las
muestra—; la base guarda en UTC.

**Lo que conviene saber antes de subir un archivo grande.** BCrypt con costo 12 (la política de
contraseñas de este sistema) tarda unos 300 ms por usuario en este equipo: importar 5000
operarios puede tardar más de 20 minutos. Es el precio de no bajar el costo del hash para una
carga masiva; queda anotado como limitación conocida en lugar de esconderse.

### Reporte de cumplimiento (RF-020, HU-14)

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/reports/compliance` | Reporte paginado, una fila por operario |
| `GET` | `/api/reports/compliance/export?format=csv\|xlsx` | Descarga todas las filas con los mismos filtros |

Ambas rutas exigen `reports.view`, que solo tiene el ADMIN: la HU-14 dice literalmente que un
INSTRUCTOR o un OPERATOR que intente consultar o exportar recibe **403**.

Cada fila trae el operario con su estado —`VIGENTE`, `VENCIDA` o `SIN_CERTIFICAR`—, la marca
`aptForHighRisk` (verdadera solo con certificación vigente) y los datos del certificado que
gobierna el estado. El estado se calcula en cada consulta a partir de `expires_at` y `revoked`,
igual que en el listado de certificaciones: no se guarda, porque un dato que se puede calcular
se desincroniza en cuanto algo lo olvida actualizar.

Filtros: `search` (nombre o correo), `workshopId`, y `from`/`to` en ISO-8601 con offset.

**Interpretaciones que el documento no fija** (anotadas donde se implementan,
`ReportService`):

- **La fila es el operario**, no la inscripción: el criterio de aceptación dice «muestra cada
  operario con su estado», en singular.
- **El rango de fechas se aplica a la fecha del taller**, no a la de emisión del certificado.
  «Revisar un periodo» en una auditoría de formación es el periodo en que se dictaron las
  capacitaciones.
- **Los filtros recortan qué certificaciones cuentan** para el estado: con un taller
  seleccionado, se ve el estado del operario *en ese taller*, que es lo que se audita.
- **Aparecen los operarios con al menos una inscripción en el alcance**, que es la precondición
  del caso de uso («deben existir operarios con inscripciones o certificaciones
  registradas»). Un operario sin ninguna inscripción no tiene registro que auditar.
- **Una certificación revocada cuenta como `VENCIDA`, no como `SIN_CERTIFICAR`**: el operario
  estuvo certificado y dejó de estarlo, y esa diferencia importa en una auditoría.
- **La ruta de exportación no está escrita en la HU** (solo pide «permitir exportar»): se colgó
  del mismo recurso en lugar de meterla en `/api/export`, donde el permiso es otro.
- **No se puede ordenar por estado**: se calcula en memoria con la fecha actual, así que no
  existe como columna por la que la base pueda ordenar y paginar.

### Cambio de contraseña (RF-024, HU-18)

| Método | Ruta | Qué hace |
|---|---|---|
| `PUT` | `/api/auth/change-password` | Cambia la contraseña del propio usuario e invalida sus sesiones |

Cualquier usuario autenticado puede cambiar **su** contraseña; nadie puede cambiar la de otro.
Recibe la contraseña actual, la nueva y la confirmación (los tres campos los valida el servidor,
no solo el formulario). Al confirmar:

- Se guarda el hash con BCrypt costo 12, igual que en el alta de usuarios.
- Se invalidan **todos** los refresh tokens del usuario: el criterio 4 de la HU exige volver a
  iniciar sesión. El access token en curso no se puede revocar (es un JWT sin estado), así que
  sobrevive como mucho sus 15 minutos; por eso la interfaz cierra la sesión en cuanto recibe
  el 204.
- Se escribe una fila en `audit_logs` con `PASSWORD_CHANGED`, el IP y el agente de usuario
  (HU-18, tarea 2).

**Detalles de seguridad:**

- El fallo de la contraseña actual **no** cuenta para el bloqueo de RNF-015: quien tuviera un
  access token robado podría bloquear la cuenta de la víctima a base de intentos.
- El freno de este endpoint es propio (`PasswordChangeThrottle`): **5 solicitudes por cada 15
  minutos por usuario**, contando también las rechazadas; al sexto, 429. Es la «limitación de
  solicitudes por usuario» que RNF-015 pide para el cambio de contraseña en su última frase.
- **Limitación conocida:** el contador vive en memoria del proceso. Con varias instancias del
  backend cada una llevaría su propia cuenta; para eso haría falta un almacenamiento compartido
  (Redis o una tabla). Anotado, no escondido.

**Regla añadida (no está en la HU):** la nueva contraseña debe ser distinta de la actual.
Cambiar por la misma no cambia nada y, con la invalidación de sesiones, expulsaría al usuario
sin motivo.

### Recuperación de contraseña (RF-023, HU-17)

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/auth/recovery-request` | Genera un enlace de un solo uso y lo envía por correo |
| `POST` | `/api/auth/reset-password` | Canjea el enlace por una contraseña nueva |

Los dos son **públicos**: quien olvidó su contraseña, por definición, no puede autenticarse.

**La solicitud no revela si el correo existe.** Responde siempre 200 con el mismo mensaje
(«Si el correo está registrado, recibirás un enlace»), haya o no cuenta. Es lo que pide el
criterio 1 de la HU y además es lo correcto: un mensaje distinto convertiría el endpoint en un
detector de usuarios registrados.

**El token.** 48 bytes aleatorios (`SecureRandom`) en base64url, guardados en `recovery_tokens`
con su vencimiento a 15 minutos (`RECOVERY_TOKEN_TTL_MINUTES`). Pedir otro enlace **quema los
anteriores**: nunca hay dos vivos para el mismo usuario. Al canjearlo se marca `used`, se guarda
la contraseña con BCrypt costo 12, se invalidan todos los refresh tokens (misma regla que
RF-024) y se queman los demás enlaces pendientes, todo en la misma transacción. Un enlace
caducado responde «El enlace ha expirado, solicita uno nuevo», y uno ya usado o inventado «El
enlace no es válido. Solicita uno nuevo»: la interfaz usa esos mensajes para ofrecer pedir otro.

**El envío del correo.** `MailSender` tiene dos implementaciones y la elección depende de la
configuración, no del código:

- `SmtpMailSender`, cuando `MAIL_SMTP_HOST` tiene valor. Es lo que se usa en producción.
- `ConsoleMailSender`, cuando no lo tiene. Escribe el enlace en el log, que es lo que permite
  probar el flujo completo sin servidor de correo. **Es una comodidad de desarrollo, no un modo
  de producción**: un enlace en un log es un enlace que cualquiera con acceso al log puede usar
  durante 15 minutos.

La decisión se toma con `StringUtils.hasText` y no con `@ConditionalOnProperty` porque la
propiedad está siempre presente (su valor por defecto es la cadena vacía) y la anotación habría
dado el SMTP por configurado.

**Freno de peticiones.** 3 solicitudes por hora **por IP** (`RecoveryRequestThrottle`), que es lo
que pide la tarea 12 de la HU. Al cuarto intento, 429. Dos limitaciones anotadas: vive en la
memoria del proceso (con varias instancias cada una lleva su cuenta) y usa
`request.getRemoteAddr()`, que detrás de un proxy es la IP del proxy —y si se confiara en
`X-Forwarded-For` sin validarlo, sería una cabecera que el cliente puede falsear.

**Auditoría.** Se registran `PASSWORD_RECOVERY_REQUEST` (con la IP) y `PASSWORD_RESET` (con el
número de sesiones cerradas) en `audit_logs`. Lo que no se registra es el token: un rastro que
guarda el secreto que acaba de canjearse no es un rastro, es una copia.

---

## 6. Decisiones de diseño

**Access token vs. refresh token.** El access token es un JWT firmado (HS256, 15 min) que no
toca la base de datos. El refresh token **no es un JWT**: es una cadena opaca de 64 bytes
aleatorios guardada en `refresh_tokens`, revocable y de un solo uso. En cada refresco se
quema el token usado y se emite otro (rotación). Reutilizar uno ya usado devuelve 401.

**Permisos frescos en cada petición.** `AppUserDetailsService` consulta la base de datos en
cada petición autenticada en vez de confiar en los permisos incrustados en el JWT. Así, si a
un usuario le cambian el rol, el efecto es inmediato y no hay que esperar a que expire el token.

**Contraseñas.** BCrypt con costo 12 (lo que exige el comentario de `users.password_hash`).
Política mínima: 8 caracteres con mayúscula, minúscula, dígito y carácter especial.

**Bloqueo por intentos fallidos (RNF-015).** Tras 5 fallos consecutivos la cuenta se bloquea
15 minutos y la API responde **429**, no 401. Los umbrales se leen de `configurations`
(`max_login_attempts`, `lockout_duration_minutes`), no están en el código. El detalle fino:
`AuthService.login` **no** es transaccional a propósito — si lo fuera, la excepción de
autenticación haría rollback y el contador de intentos nunca se guardaría, con lo que el
bloqueo jamás se alcanzaría. Cada escritura del contador va en su propia transacción
(`LoginAttemptService`, `REQUIRES_NEW`). Un correo inexistente siempre responde 401 y no
se bloquea nada, para no permitir enumeración de usuarios.

**Borrado lógico.** No hay `DELETE` de usuarios: se usa `PATCH /status`. La clave foránea
`users.role_id` es `ON DELETE RESTRICT`, y además un usuario nunca debe desaparecer del
historial de certificaciones y auditoría. Reglas añadidas: no puedes desactivar tu propio
usuario ni dejar el sistema sin ningún ADMIN activo.

**Sin Flyway todavía.** El esquema se aplica con `certisafe_schema.sql` y los cambios
posteriores con scripts sueltos en `database/` (`alter_rnf015_lockout.sql`). Versionar las
migraciones con Flyway queda como mejora pendiente.

---

## 7. Estado del avance

### Implementado y verificado contra la base real

Autenticación completa (login, refresh con rotación, logout, `/me`), CRUD de usuarios con
paginación, filtrado y ordenación, catálogo de roles, seguridad por permisos granulares,
manejo global de errores y datos del usuario inicial.

**Talleres** (`workshops`, `workshop_assignments`, `status_history`): CRUD con paginación y
filtros, asignación de instructor con histórico, historial de estados y la máquina de estados
de RNF-016 con rechazo de transiciones inválidas. Verificado contra la base real con los tres
roles: creación, validaciones (fecha pasada, duración, capacidad), las cuatro transiciones
válidas y las inválidas, permisos por rol (INSTRUCTOR puede cambiar estado pero recibe 403 al
crear, editar o borrar), reglas de borrado y persistencia en las tres tablas.

**Inscripciones y asistencia** (`enrollments`): inscripción solo a talleres `PROGRAMADO` con
control de aforo bajo bloqueo pesimista de la fila del taller (`SELECT … FOR UPDATE`, que es
lo que impide que dos inscripciones simultáneas revienten el aforo), duplicado con 409, el
INSTRUCTOR no puede inscribir, asistencia solo con el taller en progreso o finalizado y recorte
por rol en el servicio (ADMIN todas, INSTRUCTOR solo sus talleres, OPERATOR solo las suyas).
Verificado con 45 comprobaciones de API, incluida una doble inscripción concurrente real
(201 + 409).

**Certificaciones** (`certificates`): emisión automática al registrar `ASISTIO` dentro de la
misma transacción (RF-013), vigencia leída de `configurations.certificate_validity_days`,
numeración `CS-<año>-<id>`, estado derivado (nunca almacenado), revocación administrativa con
motivo y revocación/rehabilitación automática al corregir la asistencia. Verificado con 69
comprobaciones de API entre alcance por rol y ciclo de vida.

**Carga masiva y exportación** (`import_batches`, `import_errors`): RF-017, RF-018 y RF-019,
descritos arriba. Verificado con 79 comprobaciones de API: CSV con coma y con punto y coma,
`.xlsx` real generado con POI (incluida una celda de fecha), filas buenas y malas en el mismo
archivo, archivo sin filas, archivo sin columnas obligatorias, extensión no permitida,
exportaciones CSV y Excel, exportación sin datos, y 403 para INSTRUCTOR y OPERATOR en todos
los endpoints.

**Reporte de cumplimiento** (RF-020, HU-14): consulta paginada por operario con estado
derivado y marca de no apto para zonas de alto riesgo, filtros por taller y por rango de fechas
del taller, y exportación en ambos formatos con los mismos filtros. Verificado con 63
comprobaciones de API: los tres estados (sin certificación, vigente, vencida por caducidad y
vencida por revocación), el resaltado de no aptos, los filtros y la búsqueda, que el export
trae las mismas filas que la pantalla, el 400 con «No hay datos para exportar» y el 403 para
INSTRUCTOR y OPERATOR.

**Cambio de contraseña** (RF-024, HU-18): endpoint `PUT /api/auth/change-password` con validación
de la actual y de la política, BCrypt costo 12, invalidación de todos los refresh tokens y fila
de auditoría. Verificado con 33 comprobaciones de API: 401 sin token, «La contraseña actual es
incorrecta» sin tocar la contraseña ni el contador de bloqueo, política de complejidad explicada,
confirmación distinta, contraseña repetida, 204 sin cuerpo, hash BCrypt 12, refresh token
anterior quemado (401), contraseña anterior fuera (401) y nueva dentro (200), fila en`audit_logs` con IP y sesiones revocadas, y el freno propio de 5/15 min con 429 sin bloquear
la cuenta.

**Recuperación de contraseña** (RF-023, HU-17): `POST /api/auth/recovery-request` y
`POST /api/auth/reset-password`, ambos públicos. Verificado con 19 comprobaciones de API: mensaje
idéntico exista o no la cuenta, enlace en el log, política de contraseña y confirmación validadas,
token inventado, canje correcto, contraseña anterior fuera y nueva dentro, token reutilizado,
enlace nuevo que quema el anterior y el freno de 3 por hora con 429. En la interfaz se comprobó
el recorrido completo: enlace «¿Olvidaste tu contraseña?», mensaje genérico, formulario con
indicador de fortaleza, aviso de enlace caducado con botón para pedir otro y el 429 del freno.

### Pendiente

Los módulos de negocio del alcance funcional aún no existen:

`notifications` · `configurations` (API) · `files`.

De autenticación no queda ninguno: RF-023 (recuperación) y RF-025 (revocación de certificados,
ADMIN y con motivo obligatorio) están implementados y verificados.

`audit_logs` ya tiene uso —cambio de contraseña (HU-18), solicitud y canje del enlace de
recuperación (HU-17) y revocación de certificación (HU-19)— pero sigue sin API de consulta ni
pantalla: RNF-013 no define ningún RF para leerla, y las operaciones que enumera (cambios de
estado, emisiones, instructores, inscripciones) ya viven en sus propias tablas
(`status_history`, `certificates`, `workshop_assignments`, `enrollments`).

**Pruebas automatizadas.** 48 comprobaciones de integración (`mvn test`) cubren las tres reglas
que RNF-014 nombra —aforo, transiciones de estado y carga masiva— además de las de
certificación, cambio y recuperación de contraseña, incluida una carrera real de dos
inscripciones al último cupo. Detalladas en la sección 10.

### RNF-015 (bloqueo por intentos fallidos) — resuelto

RNF-015 tenía una **inconsistencia real con el modelo de datos original**: exigía bloquear la
cuenta 15 minutos tras 5 fallos y responder 429, pero la tabla `users` no tenía dónde guardar
ni el contador ni la fecha de desbloqueo. Se corrigió en los cuatro sitios:

1. `users` incorpora `failed_login_attempts` (INTEGER NOT NULL DEFAULT 0, con CHECK `>= 0`) y
   `locked_until` (TIMESTAMPTZ, con índice parcial).
2. El diagrama del modelo relacional refleja las dos columnas nuevas.
3. El diagrama de clases añade `failedLoginAttempts` y `lockedUntil` a `User`.
4. `LoginAttemptService` + `AuthService` implementan el comportamiento.

Verificado contra la base real: 4 fallos → **401**, el 5.º → **429**, con la cuenta ya
bloqueada la contraseña correcta también da **429**, un login correcto reinicia el contador a
0, y un correo inexistente da 401 sin crear fila ni bloquear nada.

---

## 8. Estructura del código

```
src/main/java/com/certisafe/
├── common/          PageResponse (contrato de paginación) y manejo global de errores
├── config/          SecurityConfig (filtros, CORS, BCrypt), OpenApiConfig y ParameterService
├── security/        JwtService, JwtAuthenticationFilter, UserPrincipal, entry point / denied handler
│                     y PasswordPolicy (la política de contraseñas en un solo sitio)
├── audit/           Registro de operaciones sensibles en audit_logs (RNF-013)
├── domain/          Entidades JPA mapeadas 1:1 contra certisafe_schema.sql
├── repository/      Spring Data JPA
├── auth/            Login, refresh, logout, /me, bloqueo por intentos fallidos (RNF-015),
│                     cambio de contraseña (RF-024) y recuperación por enlace (RF-023)
│   └── mail/        MailSender con sus dos implementaciones: SMTP o el enlace al log
├── user/            CRUD de usuarios
├── role/            Catálogo de roles
├── workshop/        Talleres, asignación de instructor y ciclo de vida (RNF-016)
├── enrollment/      Inscripciones y registro de asistencia (RF-015, RF-012)
├── certificate/     Emisión, consulta y revocación de certificaciones (RF-013, RF-016)
├── tabular/         Lectura y escritura de .csv/.xlsx (POI + commons-csv), compartido
├── importer/        Carga masiva con reporte de errores por fila (RF-017, RF-018)
├── exporter/        Exportación con los filtros del listado (RF-019) y del reporte (RF-020)
├── report/          Reporte de cumplimiento para auditoría (RF-020)
└── bootstrap/       Admin inicial
```

---

## 9. Comandos útiles

```powershell
mvn -DskipTests compile          # compilar
mvn test                         # pruebas de las reglas de negocio (48 comprobaciones)
mvn spring-boot:run              # arrancar
mvn -DskipTests package          # generar el jar ejecutable
java -jar target/certisafe-backend-1.0.0.jar
```

---

## 10. Pruebas automatizadas (RNF-014)

RNF-014 pide que las reglas críticas —aforo, transiciones de estado y carga masiva— tengan
pruebas automatizadas. Las tres están cubiertas con **48 comprobaciones** en
`src/test/java/com/certisafe/`, y corren contra la base real con `mvn test`.

| Clase | Regla | Comprueba |
|---|---|---|
| `TallerCicloDeVidaIT` | RNF-016 | Transiciones permitidas y prohibidas, motivo obligatorio al cancelar, historial completo, borrado según estado, aforo no editable por debajo de los inscritos |
| `InscripcionReglasIT` | RF-015, RF-012 | Duplicado, aforo completo, solo taller `PROGRAMADO`, el INSTRUCTOR no inscribe, el OPERATOR solo se inscribe a sí mismo, asistencia solo en progreso o finalizado y recorte por rol |
| `CertificacionReglasIT` | RF-013, RF-016 | Sin asistencia no hay certificado, asistir emite, es idempotente, ausentar revoca, corregir rehabilita el mismo certificado |
| `CargaMasivaIT` | RF-017, RF-018 | Filas buenas guardadas y malas reportadas con su número de línea, correo duplicado dentro del archivo, correo ya existente, columnas obligatorias, talleres importados con instructor |
| `CambioContrasenaIT` | RF-024, HU-18 | Cambio válido, contraseña actual incorrecta, política de complejidad, confirmación, quema de refresh tokens y rastro en `audit_logs` |
| `RecuperacionContrasenaIT` | RF-023, HU-17 | Mensaje genérico con y sin cuenta, enlace vigente y de un solo uso, canje que cambia la contraseña y cierra sesiones, enlace caducado, enlace nuevo que quema el anterior, freno de 3 por hora y rastro en `audit_logs` |
| `ConcurrenciaAforoIT` | RF-015 | Dos inscripciones simultáneas al último cupo: una entra y la otra recibe «Aforo completo» |

**Cómo están montadas.** La mayoría van dentro de una transacción de prueba que se deshace al
terminar, y crean sus propios talleres, instructores y operarios en lugar de depender de los
datos de demostración (que la aplicación va modificando mientras tanto).

Dos escapan a ese esquema por una razón concreta, y vale la pena entenderla:

- `ConcurrenciaAforoIT` **no** puede ir en la transacción de prueba: si fuera así, las dos
  inscripciones entrarían en la *misma* transacción, no se bloquearían entre sí y la prueba no
  demostraría nada. Cada inscripción es una llamada con su propia transacción, como en
  producción, y los datos se borran con SQL.
- `CargaMasivaIT` tampoco, porque el lote se registra con `REQUIRES_NEW` a propósito: si el
  reporte de errores viviera en la transacción de la importación, desaparecería con el
  rollback que el CA3 de la HU-11 exige.

`pom.xml` declara `maven-surefire-plugin` para que reconozca el sufijo `*IT`; sin esa línea
`mvn test` las ignora en silencio y dice «BUILD SUCCESS» sin haber ejecutado nada. La política
de contraseñas, que vive en Bean Validation, se prueba validando el DTO con el mismo
`Validator` que usa Spring en el controlador.

El **frontend** no tiene todavía pruebas: ver su README.
