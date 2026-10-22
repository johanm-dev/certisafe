# CertiSafe — Frontend

Cliente Angular del sistema de gestión de capacitaciones en SST.

- **Angular 22** con componentes independientes y carga diferida por ruta.
- **Angular Material 3** (tema azure, tipografía Roboto, iconos Material Symbols).
- **Sin zone.js** (Angular 22 arranca *zoneless* por defecto): todo el estado de la interfaz
  vive en `signals`, así que la detección de cambios es explícita y barata.

---

## Requisitos

| Herramienta | Versión |
|---|---|
| Node.js | 22 o superior |
| **pnpm** | 10 o superior |

> El gestor de paquetes es **`pnpm`**. No hay ningún paso con `npm`. El proyecto fija
> `packageManager: pnpm` en `package.json` y el `angular.json` lo declara como
> `cli.packageManager`.

---

## Cómo arrancarlo

Necesita el backend y la base de datos funcionando; mira el
**[README de la raíz](../README.md)**.

```powershell
cd frontend
pnpm install
pnpm start
```

Y abre <http://localhost:4200>.

Al primer arranque, con la tabla `users` vacía, el backend crea un administrador. Las
credenciales están en la pantalla de acceso, y el botón **Rellenar** las pone en el
formulario para no teclearlas:

```
admin@certisafe.local · Admin123*
```

### Cuentas de demostración

Para recorrer las tres vistas hace falta un usuario de cada rol. El administrador lo crea el
backend; los otros dos se crean desde la pantalla **Usuarios** (el sistema no siembra
contraseñas en la base). Si vas a usar `database/seed_demo.sql`, crea antes el operario con
este correo exacto:

| Rol | Correo | Contraseña | Para qué sirve |
|---|---|---|---|
| ADMIN | `admin@certisafe.local` | `Admin123*` | Todo el sistema |
| INSTRUCTOR | `juan.perez@certisafe.local` | `Instructor123*` | Solo sus talleres; marca asistencia |
| OPERATOR | `operario@certisafe.local` | `Operario123*` | Se inscribe y consulta su historial |

### Si `pnpm install` se queja de scripts de compilación

`pnpm` bloquea por seguridad los scripts de instalación de dependencias nativas. Las que
hacen falta (`esbuild`, `lmdb`, `@parcel/watcher`, `msgpackr-extract`) ya están declaradas en
`pnpm-workspace.yaml`, así que en una instalación limpia debería bastar. Si aun así aparece
`ERR_PNPM_IGNORED_BUILDS`:

```powershell
pnpm approve-builds --all
pnpm install
```

Sin esas dependencias nativas la aplicación **no compila**.

---

## El detalle del proxy (y por qué no hay CORS)

`ng serve` reenvía `/api` a `http://localhost:8080` según `proxy.conf.json`. El frontend
siempre llama a rutas **relativas** (`/api/workshops`), así que:

- en desarrollo no hay CORS de por medio;
- no hay ninguna URL del backend incrustada en el código;
- en producción basta con que un proxy inverso publique `/api` en el mismo origen.

El cambio está en `src/environments/environment.ts`, que solo contiene `apiBaseUrl`.

---

## Scripts

| Comando | Qué hace |
|---|---|
| `pnpm start` | Servidor de desarrollo en el 4200, con recarga en caliente |
| `pnpm build` | Compilación de producción en `dist/` |
| `pnpm test` | Pruebas unitarias (vitest) |
| `pnpm exec ng generate component …` | Andamiaje de Angular |

---

## Estructura

```
src/
├── environments/          Parametros de compilacion (apiBaseUrl)
└── app/
    ├── core/              Todo lo transversal
    │   ├── api/           Acceso a la API (talleres, usuarios, inscripciones, certificados, reportes, cargas)
    │   ├── auth/          Sesion, guards de ruta y persistencia de tokens
    │   │                  (y `password-strength.ts`, la politica de contrasenas compartida)
    │   ├── http/          Interceptor que adjunta el token, refresca ante un 401 y descarga archivos
    │   ├── i18n/          Textos del paginador en espanol
    │   ├── models/        Interfaces espejo de los DTO del backend
    │   └── util/          Conversiones de fecha y duracion
    ├── features/          Pantallas
    │   ├── auth/login/            Acceso
    │   ├── auth/password-recovery/  Pedir el enlace de recuperación (RF-023)
    │   ├── auth/reset-password/     Elegir la contraseña nueva con el enlace (RF-023)
    │   ├── auth/change-password-dialog/  Cambio de la propia contraseña (RF-024)
    │   ├── no-access/             Aviso de permiso insuficiente
    │   ├── workshops/             Listado, formulario, estado, instructor e historial
    │   ├── users/                 Listado, alta y edicion de usuarios
    │   ├── enrollments/           Inscripciones, asistencia y dialogo de inscripcion
    │   ├── certificates/          Listado de certificaciones y dialogo de revocacion
    │   ├── reports/               Reporte de cumplimiento para auditoria
    │   └── imports/               Carga masiva de usuarios y talleres con su reporte de errores
    ├── layout/shell/      Cabecera, menu lateral y area de contenido
    └── shared/            Confirmacion y boton de exportacion reutilizables
```

---

## Decisiones que conviene conocer

**La máquina de estados no está en el frontend.** Cada taller llega con
`allowedTransitions`, que el backend calcula con la misma regla que aplica al validar
(RNF-016). El diálogo de cambio de estado pinta esa lista y nada más: así la interfaz no
puede ofrecer una transición que el servidor vaya a rechazar, y no existen dos versiones de
la regla que se puedan desincronizar. Lo único que se adelanta en el cliente es que
*cancelar* exige motivo, para no mandar una petición que rebotaría con 400.

**La paginación, la ordenación y los filtros son del servidor.** El navegador nunca se trae
la tabla completa: cada movimiento del paginador o del buscador es una consulta nueva
(`page`, `size`, `sort`, `search`, `status`, `from`, `to`). El buscador va con retardo de
350 ms y, si llega una respuesta vieja después de una nueva, se descarta.

**Los permisos se comprueban dos veces.** El menú y las acciones se pintan según los
permisos del usuario para no ofrecer algo que va a dar 403, pero la comprobación que manda
es la del backend en cada endpoint.

**El refresco de token se comparte.** El backend **rota** el refresh token, de modo que dos
peticiones que refrescasen a la vez se invalidarían entre sí y el usuario acabaría expulsado
sin motivo. `AuthService.refreshOnce()` comparte un único refresco entre todos los 401
simultáneos.

---

## Las pantallas

### Talleres (`/talleres`)

Listado con paginación, orden y filtros del servidor, y cuatro diálogos: alta y edición,
cambio de estado, asignación de instructor e historial. La columna **Cupos** muestra
`inscritos / total` y los que quedan libres, y la acción **Inscribir** solo aparece en talleres
`PROGRAMADO`.

### Inscripciones (`/inscripciones`)

Es **una sola pantalla** para los tres roles, no tres pantallas casi iguales. Lo que cambia es
la presentación, y el filtrado real lo hace el servidor: un OPERATOR recibe solo las suyas, un
INSTRUCTOR solo las de los talleres que tiene asignados y un ADMIN todas. La tabla no filtra
nada por su cuenta.

| Rol | Título | Columnas de operario | Registra asistencia | Borra | Inscribe |
|---|---|---|---|---|---|
| ADMIN | Inscripciones | Sí | Sí | Sí | Sí, eligiendo al operario |
| INSTRUCTOR | Inscripciones | Sí | Solo en sus talleres | No | **No** (RF-012, criterio 4) |
| OPERATOR | Mis inscripciones | No (todas son suyas) | No | No | Sí, a sí mismo |

Tres decisiones que conviene justificar:

- **La asistencia es un `mat-select` y no una casilla de verificación.** La historia de usuario
  habla de «casilla», pero el estado tiene tres valores (`PENDIENTE`, `ASISTIO`, `AUSENTE`) y
  una casilla solo expresa dos. Se aparta de la letra para no perder un estado.
- **El selector se deshabilita hasta que el taller está en progreso o finalizado** (RF-012,
  criterio 3). Cuando está bloqueado por esa razón se escribe el motivo debajo, para que no
  parezca roto. A un OPERATOR no se le llena la tabla de explicaciones sobre algo que no le
  corresponde: en su vista sale una etiqueta de color, sin selector.
- **El INSTRUCTOR no ve la acción de inscribir.** El backend le devuelve 403 si lo intenta
  (RF-012, criterio 4); ocultarla evita ofrecer algo que va a fallar.
- La columna **Certificado** muestra el número que emitió esa asistencia, o «—» si todavía no
  hay. Así se ve en la misma fila que marcar «Asistió» produjo el certificado, sin cambiar de
  pantalla.

### Certificados (`/certificados`)

El certificado **no se crea aquí**: lo emite el sistema cuando el instructor registra la
asistencia (RF-013). La pantalla sirve para consultar el historial (RF-016) y, solo al ADMIN,
para revocar. El estado (`Vigente`, `Vencida`, `Revocada`) lo calcula el servidor: «vencida»
depende de la fecha y de la hora, y el reloj del navegador no tiene por qué coincidir.

| Rol | Título | Filtros | Ve el operario | Revoca |
|---|---|---|---|---|
| ADMIN | Certificados | Sí | Sí | Sí |
| INSTRUCTOR | Certificados | Sí | Sí (solo sus talleres) | No |
| OPERATOR | Mis certificados | No | No (todos son suyos) | No |

- La vigencia sale de `configurations.certificate_validity_days` (hoy 365 días). Cambiarla en
  la base afecta a los certificados **nuevos**; los ya emitidos conservan la fecha con la que
  nacieron.
- Marcar `AUSENTE` a quien ya tenía certificado lo **revoca automáticamente**, con el motivo
  «Corrección de asistencia: …». Si la asistencia se corrige de vuelta a `ASISTIO`, el sistema
  lo rehabilita. Una revocación hecha por el ADMIN, en cambio, no se deshace marcando
  asistencia.
- **La revocación no tiene un RF propio.** El requerimiento no describe la operación, pero el
  esquema ya traía el permiso `certificates.revoke` y las columnas `revoked`, `revoked_at` y
  `revocation_reason`: se implementó como la acción administrativa que esos campos preveían.
  Queda anotado como interpretación.

### Usuarios (`/usuarios`)

Listado con filtros por texto, rol y estado. El borrado es **lógico**: desactivar una cuenta no
la elimina, deja de poder iniciar sesión y conserva su historial. Por eso activar y desactivar
exige el permiso `users.delete` y no `users.update` — en este sistema, desactivar *es* el
borrado.

El formulario no pide la contraseña al editar: el backend no expone el cambio de contraseña de
otro usuario y fingirlo aquí sería engañar al usuario. La política de contraseña que se valida
en el cliente es la misma que aplica el servidor, y se pide dos veces al crear.

### Importación (`/importacion`)

Pantalla **solo para el ADMIN** (permiso `import.execute`) con dos tarjetas idénticas, una para
operarios y otra para talleres: descarga de plantilla (CSV o Excel), selección del archivo y
un botón **Procesar**. Nada se envía al servidor hasta pulsarlo, para que elegir el archivo
equivocado no dispare una carga.

El resultado se muestra tal como lo devuelve el backend: estado del lote, total leído, creados
y rechazados, y una tabla con **cada fila rechazada**: su número (el mismo que se ve en Excel,
donde el encabezado es la fila 1), el motivo y los datos crudos, para corregir el archivo sin
buscarla a mano.

Decisiones que conviene conocer:

- **El reporte no se pagina.** Vive en memoria, dura lo que dura la pantalla y el servidor ya
  limita el archivo a 5000 filas. Un paginador del lado del servidor obligaría a persistir el
  reporte y a otra consulta para algo que el usuario lee una vez.
- **La validación de extensión se hace también en el cliente** (`.csv` o `.xlsx`) para no subir
  un archivo que el servidor va a rechazar, pero la que manda es la del servidor.
- El input de archivo está recortado a 1 px en lugar de usar `hidden`: así sigue existiendo para
  los lectores de pantalla (y para las pruebas de interfaz), y el botón visible es el que lo
  dispara.

### Exportación (en los tres listados)

Los listados de **usuarios**, **talleres** e **inscripciones** llevan un botón **Exportar** con
menú de formato (Excel `.xlsx` o CSV), **solo para el ADMIN** (permiso `export.execute`).

La exportación se lleva los **mismos filtros que están puestos en pantalla**, no la página
actual: si el listado está filtrado por estado `PROGRAMADO`, el archivo trae todos los talleres
programados, no los 20 de la página. Por eso el botón vive en cada listado y no en una pantalla
aparte.

El botón se **deshabilita cuando el listado está vacío**: pedir una exportación sin filas
siempre daría error, y ofrecerla para luego quejarse es peor que no ofrecerla. El límite sigue
existiendo en el servidor (400 con «No hay datos para exportar») porque la API es pública para
cualquier cliente.

La descarga la centraliza `core/http/download.service.ts`, que además resuelve una trampa de
las respuestas binarias: cuando el backend devuelve 400, el cuerpo llega como `Blob`, no como
JSON; hay que leerlo para poder mostrar «No hay datos para exportar» en vez de un «400» pelado.

### Reporte de cumplimiento (`/reportes`)

Pantalla **solo para el ADMIN** (permiso `reports.view`, RF-020 y HU-14). Lista a los operarios
con su estado —**Vigente**, **Vencida** o **Sin certificar**— y con qué certificado cuentan.
Las filas sin certificación vigente se pintan en rojo y se marcan como **No apto**: el criterio
de aceptación 3 pide resaltar a quienes no pueden ingresar a zonas de alto riesgo, y eso no se
puede dejar solo en una columna que hay que ir a leer.

| Control | Qué hace |
|---|---|
| Buscar | Nombre o correo del operario |
| Taller | Restringe el reporte a un taller (y el estado pasa a ser el del operario en ese taller) |
| Taller desde / hasta | Rango sobre la **fecha del taller**, no sobre la de emisión del certificado |
| Limpiar | Quita los cuatro filtros y vuelve a la página 1 |
| Actualizar | Vuelve a consultar: el estado cambia cuando otra persona registra una asistencia |
| Exportar | Descarga `.xlsx` o `.csv` con **todas** las filas y los filtros puestos |

Tres detalles que conviene conocer:

- **La columna «Operario» ordena por apellido**, y el `matSort` emite el nombre de la columna
  (`operario`), que no es una propiedad de la entidad. El componente lo traduce a `lastName`
  antes de mandar el `sort`; sin esa traducción el backend intentaría ordenar por una columna
  que no existe. Por estado no se puede ordenar: se calcula en el servidor con la fecha actual,
  así que no es una columna de la base.
- **El export no depende de la página abierta.** Un informe de auditoría que solo trae las 10
  filas visibles no sirve; el botón manda los filtros, no el índice de página.
- El botón se deshabilita con el reporte vacío, y la tabla vacía explica que el rango se aplica
  a la fecha del taller, que es la confusión más fácil de tener con este filtro.

### Cambio de contraseña (diálogo de la cabecera, RF-024)

La HU-18 llama «vista» al formulario; se resuelve como diálogo abierto desde el icono de la
cabecera (junto al de cerrar sesión) y no como ruta: son tres campos y una acción, y una
pantalla propia obligaría a ir y volver del menú para algo que se hace una vez cada tanto.
Está disponible para los tres roles.

| Elemento | Comportamiento |
|---|---|
| Contraseña actual, nueva, confirmación | Los tres son obligatorios; la nueva pasa la misma política del backend (el regex vive en `PasswordPolicy` y el frontend lo replica en `core/auth/password-strength.ts`, en un solo sitio) |
| Mostrar / Ocultar | Un solo interruptor para las tres casillas: mirar lo que se escribe no debería ser tres clics |
| Error del servidor | Se muestra en un aviso **sin cerrar** el diálogo: si la contraseña actual estaba mal, se corrige ahí mismo en lugar de repetir el camino |
| Confirmación exitosa | Se cierra, se avisa y se cierra la sesión: el backend ya invalidó los refresh tokens y el access token en curso caduca en minutos, así que no tiene sentido quedarse dentro |

La confirmación se valida en el propio campo (no como error del grupo): `mat-error` solo se
muestra cuando el campo está en error, y un error del grupo lo dejaría invisible.

### Recuperación de contraseña (`/recuperar-contrasena` y `/restablecer-contrasena`, RF-023)

Son las dos únicas rutas **públicas** además de `/login`, y por la misma razón: quien olvidó su
contraseña no puede autenticarse. Se llega desde el enlace «¿Olvidaste tu contraseña?» del acceso.

| Pantalla | Qué hace |
|---|---|
| `/recuperar-contrasena` | Pide el correo y muestra el mensaje que devuelve el servidor, que es el mismo exista o no la cuenta |
| `/restablecer-contrasena?token=…` | Recibe el token del enlace, pide la contraseña nueva y su confirmación, y la guarda |

Decisiones que conviene conocer:

- **La pantalla no intenta averiguar si el correo existe.** No hay ningún «ese correo no está
  registrado»: sería una pista gratis para sondear cuentas, y el backend contesta lo mismo en
  los dos casos. Lo que sí se muestra es el 429 del freno de peticiones, que es un mensaje
  legítimo del servidor.
- **Indicador de fortaleza en vivo.** `measurePassword` comprueba los mismos cinco criterios que
  la política y suma un punto si la contraseña llega a 12 caracteres. No es un medidor
  criptográfico: es una ayuda para que el usuario vea qué le falta antes de enviar.
- **El enlace caducado se puede resolver sin salir.** Si el backend responde «El enlace ha
  expirado» o «El enlace no es válido», la pantalla cambia el botón de guardar por uno para
  pedir un enlace nuevo. Un callejón sin salida en el único camino de vuelta a la cuenta no es
  aceptable.
- **Sin `token` en la URL** (alguien escribió `/restablecer-contrasena` a mano) no se pinta el
  formulario: se explica que el enlace no sirve y se ofrece pedir uno.
- **Al guardar se cierran todas las sesiones**, incluida la de ese navegador si la hubiera, así
  que la pantalla de éxito lleva al acceso en lugar de intentar entrar sola.

El enlace llega por correo. Sin SMTP configurado el backend lo escribe en su log: es cómo se
prueba el recorrido completo en desarrollo y está anotado en el README del backend.

---

## Limitaciones conocidas

- **No hay pruebas automatizadas todavía.** El andamiaje de vitest está listo, pero aún no
  hay casos escritos. Es el pendiente más serio de esta parte.
- Los tokens se guardan en `localStorage`, que es accesible desde JavaScript: un XSS podría
  leer el refresh token. Lo correcto sería una cookie `HttpOnly` con `Secure` y `SameSite`,
  lo que obliga a coordinar CORS y CSRF con el backend.
- El selector de instructor pide `size=100`, el máximo que admite la API. Con más de 100
  instructores activos habría que sustituirlo por un autocompletado paginado. Lo mismo pasa con
  el selector de operarios al inscribir.
- Están implementadas las pantallas de **talleres, inscripciones, certificados, usuarios,
  reporte de cumplimiento e importación/exportación**. Faltan las de notificaciones y
  configuración, que tampoco tienen todavía backend.
- **Escribir una fecha a mano en un selector es ambiguo.** Se usa el adaptador nativo de
  Angular Material, que interpreta lo tecleado al estilo estadounidense (`03/05/2026` es 5 de
  marzo) mientras la interfaz lo muestra al estilo español (`3/5/2026`). Elegir la fecha en el
  calendario no tiene este problema. Corregirlo exige un `DateAdapter` propio que sobrescriba
  `parse`; se dejó anotado en lugar de cambiar el comportamiento de todas las pantallas a
  última hora.
- El certificado **no genera un PDF descargable**. Se emite y se consulta, pero imprimirlo no
  está implementado: ninguna RF define el formato y habría que añadir una librería solo para
  eso.
