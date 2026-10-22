-- ============================================================================
--  seed_demo.sql  --  DATOS DE DEMOSTRACION (OPCIONAL)
--
--  Esto NO es parte del modelo de datos ni de la entrega: es un sembrador para
--  tener talleres con los que probar la interfaz (listados, filtros, paginacion
--  y el ciclo de vida de RNF-016). Se puede borrar sin afectar a nada.
--
--  Uso:
--      psql -U postgres -h localhost -d Certisafe -f database/seed_demo.sql
--
--  Requiere que existan los usuarios sembrados por el arranque del backend
--  (admin@certisafe.local) y, para el instructor, juan.perez@certisafe.local.
--  Si falta el instructor, los talleres se crean igual, sin instructor asignado.
--
--  Las inscripciones de demostracion necesitan un usuario OPERATOR con correo
--  operario@certisafe.local (creele la cuenta desde la pantalla Usuarios). Si no
--  existe, el seed crea los talleres igual y avisa por consola.
--
--  Es idempotente: si ya existen talleres con estos nombres, no duplica nada.
--  Correlo despues de limpiar los talleres (ver database/reset_demo.sql) si
--  quiere volver a empezar de cero.
-- ============================================================================

DO $$
DECLARE
    v_admin      BIGINT;
    v_instructor BIGINT;
    v_operario   BIGINT;
    v_ws         BIGINT;
BEGIN
    SELECT id INTO v_admin FROM users WHERE email = 'admin@certisafe.local';
    IF v_admin IS NULL THEN
        RAISE EXCEPTION 'No existe admin@certisafe.local. Arranca el backend una vez para crearlo.';
    END IF;
    SELECT id INTO v_instructor FROM users WHERE email = 'juan.perez@certisafe.local';

    -- ------------------------------------------------------------------
    -- 1. Trabajo seguro en alturas  ->  FINALIZADO (ya dictado)
    -- ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM workshops WHERE name = 'Trabajo seguro en alturas') THEN
        INSERT INTO workshops (name, description, workshop_date, duration_minutes, location,
                               max_capacity, status, instructor_id, created_by, status_changed_at)
        VALUES ('Trabajo seguro en alturas',
                'Identificacion de riesgos, linea de vida, arnes y anclajes. Incluye practica.',
                NOW() - INTERVAL '12 days', 480, 'Bogota - Sede Norte, torre de entrenamiento',
                20, 'PROGRAMADO', v_instructor, v_admin, NOW() - INTERVAL '20 days')
        RETURNING id INTO v_ws;

        INSERT INTO status_history (workshop_id, previous_status, new_status, changed_by, changed_at, reason)
        VALUES (v_ws, NULL, 'PROGRAMADO', v_admin, NOW() - INTERVAL '20 days', 'Creacion del taller'),
               (v_ws, 'PROGRAMADO', 'EN_PROGRESO', v_instructor, NOW() - INTERVAL '12 days', NULL),
               (v_ws, 'EN_PROGRESO', 'FINALIZADO', v_instructor, NOW() - INTERVAL '12 days' + INTERVAL '8 hours', NULL);

        UPDATE workshops SET status = 'FINALIZADO',
                             status_changed_at = NOW() - INTERVAL '12 days' + INTERVAL '8 hours'
        WHERE id = v_ws;

        IF v_instructor IS NOT NULL THEN
            INSERT INTO workshop_assignments (workshop_id, instructor_id, assigned_by, assigned_at, is_current)
            VALUES (v_ws, v_instructor, v_admin, NOW() - INTERVAL '20 days', TRUE);
        END IF;
    END IF;

    -- ------------------------------------------------------------------
    -- 2. Espacios confinados  ->  EN_PROGRESO (dictandose ahora)
    -- ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM workshops WHERE name = 'Espacios confinados: entrada y rescate') THEN
        INSERT INTO workshops (name, description, workshop_date, duration_minutes, location,
                               max_capacity, status, instructor_id, created_by, status_changed_at)
        VALUES ('Espacios confinados: entrada y rescate',
                'Permisos de entrada, monitoreo de atmosfera, vigia y plan de rescate.',
                NOW() - INTERVAL '2 hours', 240, 'Medellin - Planta 2, tanque de lavado',
                12, 'PROGRAMADO', v_instructor, v_admin, NOW() - INTERVAL '15 days')
        RETURNING id INTO v_ws;

        INSERT INTO status_history (workshop_id, previous_status, new_status, changed_by, changed_at, reason)
        VALUES (v_ws, NULL, 'PROGRAMADO', v_admin, NOW() - INTERVAL '15 days', 'Creacion del taller'),
               (v_ws, 'PROGRAMADO', 'EN_PROGRESO', v_instructor, NOW() - INTERVAL '2 hours', NULL);

        UPDATE workshops SET status = 'EN_PROGRESO', status_changed_at = NOW() - INTERVAL '2 hours'
        WHERE id = v_ws;

        IF v_instructor IS NOT NULL THEN
            INSERT INTO workshop_assignments (workshop_id, instructor_id, assigned_by, assigned_at, is_current)
            VALUES (v_ws, v_instructor, v_admin, NOW() - INTERVAL '15 days', TRUE);
        END IF;
    END IF;

    -- ------------------------------------------------------------------
    -- 3. Manejo de extintores  ->  EN_PROGRESO
    --    Es el taller que sirve para ver la asistencia ya registrada: la
    --    asistencia en un taller que aun no ha empezado no es coherente.
    -- ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM workshops WHERE name = 'Manejo de extintores y conato de incendio') THEN
        INSERT INTO workshops (name, description, workshop_date, duration_minutes, location,
                               max_capacity, status, instructor_id, created_by, status_changed_at)
        VALUES ('Manejo de extintores y conato de incendio',
                'Clases de fuego, seleccion del extintor y practica de extincion controlada.',
                NOW() - INTERVAL '1 hour', 120, 'Bogota - Sede Norte, patio de simulacion',
                30, 'PROGRAMADO', v_instructor, v_admin, NOW() - INTERVAL '9 days')
        RETURNING id INTO v_ws;

        INSERT INTO status_history (workshop_id, previous_status, new_status, changed_by, changed_at, reason)
        VALUES (v_ws, NULL, 'PROGRAMADO', v_admin, NOW() - INTERVAL '9 days', 'Creacion del taller'),
               (v_ws, 'PROGRAMADO', 'EN_PROGRESO', v_instructor, NOW() - INTERVAL '1 hour', NULL);

        UPDATE workshops SET status = 'EN_PROGRESO', status_changed_at = NOW() - INTERVAL '1 hour'
        WHERE id = v_ws;

        IF v_instructor IS NOT NULL THEN
            INSERT INTO workshop_assignments (workshop_id, instructor_id, assigned_by, assigned_at, is_current)
            VALUES (v_ws, v_instructor, v_admin, NOW() - INTERVAL '9 days', TRUE);
        END IF;
    END IF;

    -- ------------------------------------------------------------------
    -- 4. Primeros auxilios  ->  PROGRAMADO (sin instructor asignado todavia)
    -- ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM workshops WHERE name = 'Primeros auxilios y RCP basico') THEN
        INSERT INTO workshops (name, description, workshop_date, duration_minutes, location,
                               max_capacity, status, instructor_id, created_by, status_changed_at)
        VALUES ('Primeros auxilios y RCP basico',
                'Evaluacion primaria, maniobras de RCP y uso del DEA.',
                NOW() + INTERVAL '14 days', 360, 'Cali - Auditorio principal',
                25, 'PROGRAMADO', NULL, v_admin, NOW() - INTERVAL '5 days')
        RETURNING id INTO v_ws;

        INSERT INTO status_history (workshop_id, previous_status, new_status, changed_by, changed_at, reason)
        VALUES (v_ws, NULL, 'PROGRAMADO', v_admin, NOW() - INTERVAL '5 days', 'Creacion del taller');
    END IF;

    -- ------------------------------------------------------------------
    -- 5. Riesgo electrico  ->  CANCELADO (con motivo)
    -- ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM workshops WHERE name = 'Riesgo electrico y bloqueo etiquetado LOTO') THEN
        INSERT INTO workshops (name, description, workshop_date, duration_minutes, location,
                               max_capacity, status, instructor_id, created_by, status_changed_at)
        VALUES ('Riesgo electrico y bloqueo etiquetado LOTO',
                'Cinco reglas de oro, bloqueo y etiquetado de fuentes de energia.',
                NOW() + INTERVAL '3 days', 180, 'Barranquilla - Taller de mantenimiento',
                15, 'PROGRAMADO', v_instructor, v_admin, NOW() - INTERVAL '11 days')
        RETURNING id INTO v_ws;

        INSERT INTO status_history (workshop_id, previous_status, new_status, changed_by, changed_at, reason)
        VALUES (v_ws, NULL, 'PROGRAMADO', v_admin, NOW() - INTERVAL '11 days', 'Creacion del taller'),
               (v_ws, 'PROGRAMADO', 'CANCELADO', v_admin, NOW() - INTERVAL '3 days',
                'El cliente reprogramo la parada de planta');

        UPDATE workshops SET status = 'CANCELADO', status_changed_at = NOW() - INTERVAL '3 days'
        WHERE id = v_ws;

        IF v_instructor IS NOT NULL THEN
            INSERT INTO workshop_assignments (workshop_id, instructor_id, assigned_by, assigned_at, is_current)
            VALUES (v_ws, v_instructor, v_admin, NOW() - INTERVAL '11 days', TRUE);
        END IF;
    END IF;

    -- ------------------------------------------------------------------
    -- 6. Inspeccion de EPP  ->  PROGRAMADO
    -- ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM workshops WHERE name = 'Inspeccion de EPP y seguridad vial') THEN
        INSERT INTO workshops (name, description, workshop_date, duration_minutes, location,
                               max_capacity, status, instructor_id, created_by, status_changed_at)
        VALUES ('Inspeccion de EPP y seguridad vial',
                'Criterios de inspeccion de elementos de proteccion personal y plan estrategico de seguridad vial.',
                NOW() + INTERVAL '21 days', 90, 'Bucaramanga - Sala de capacitacion',
                40, 'PROGRAMADO', NULL, v_admin, NOW() - INTERVAL '1 day')
        RETURNING id INTO v_ws;

        INSERT INTO status_history (workshop_id, previous_status, new_status, changed_by, changed_at, reason)
        VALUES (v_ws, NULL, 'PROGRAMADO', v_admin, NOW() - INTERVAL '1 day', 'Creacion del taller');
    END IF;

    -- ------------------------------------------------------------------
    -- 7. Inscripciones del operario de demostracion
    --
    --    El seed NO crea usuarios (no se versiona una contrasena), asi que este
    --    bloque solo actua si ya existe operario@certisafe.local. Para tenerlo,
    --    creelo desde la pantalla Usuarios con el rol OPERATOR y esta clave:
    --    Operario123*
    -- ------------------------------------------------------------------
    SELECT id INTO v_operario FROM users WHERE email = 'operario@certisafe.local' AND active;

    IF v_operario IS NOT NULL THEN
        -- Manejo de extintores (en progreso): asistio.
        INSERT INTO enrollments (user_id, workshop_id, attendance_status, enrolled_at, assigned_at)
        SELECT v_operario, w.id, 'ASISTIO', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 hour'
        FROM workshops w
        WHERE w.name = 'Manejo de extintores y conato de incendio'
          AND NOT EXISTS (SELECT 1 FROM enrollments e
                          WHERE e.user_id = v_operario AND e.workshop_id = w.id);

        -- Inspeccion de EPP (programado): pendiente, sin asistencia todavia.
        INSERT INTO enrollments (user_id, workshop_id, attendance_status, enrolled_at)
        SELECT v_operario, w.id, 'PENDIENTE', NOW() - INTERVAL '1 day'
        FROM workshops w
        WHERE w.name = 'Inspeccion de EPP y seguridad vial'
          AND NOT EXISTS (SELECT 1 FROM enrollments e
                          WHERE e.user_id = v_operario AND e.workshop_id = w.id);

        -- Certificacion de la asistencia anterior (RF-013).
        --
        -- En la aplicacion la emite el servicio al registrar la asistencia. Aqui se
        -- siembra ya emitida para que la pantalla de certificados no aparezca vacia en
        -- una demo recien montada: la asistencia esta en ASISTIO, y con ASISTIO y sin
        -- certificado el estado seria incoherente.
        --
        -- La vigencia sale de configurations, igual que en el backend; la fecha de
        -- emision es la de la asistencia registrada.
        INSERT INTO certificates (user_id, workshop_id, enrollment_id, instructor_id,
                                  certificate_number, issued_at, expires_at)
        SELECT e.user_id, e.workshop_id, e.id, w.instructor_id,
               -- Numero provisional: el definitivo lleva el id del certificado, que no
               -- existe hasta despues del INSERT. Se renombra justo abajo.
               'TMP-' || gen_random_uuid()::text,
               e.assigned_at,
               e.assigned_at + make_interval(days => COALESCE(
                   (SELECT c.value::int FROM configurations c
                    WHERE c.config_key = 'certificate_validity_days'), 365))
        FROM enrollments e
        JOIN workshops w ON w.id = e.workshop_id
        WHERE e.user_id = v_operario
          AND e.attendance_status = 'ASISTIO'
          AND w.instructor_id IS NOT NULL
        ON CONFLICT (enrollment_id) DO NOTHING;

        -- Mismo formato que usa CertificateService.issue: CS-<ano>-<id del certificado>.
        UPDATE certificates c
        SET certificate_number = 'CS-' || to_char(c.issued_at, 'YYYY') || '-'
                                  || lpad(c.id::text, 6, '0')
        WHERE c.certificate_number LIKE 'TMP-%';
    ELSE
        RAISE NOTICE 'No existe operario@certisafe.local: se omiten las inscripciones de demostracion.';
    END IF;

    RAISE NOTICE 'Datos de demostracion listos.';
END $$;

SELECT w.id,
       w.status,
       to_char(w.workshop_date, 'YYYY-MM-DD HH24:MI') AS fecha,
       w.name,
       COALESCE(u.email, '(sin instructor)') AS instructor,
       (SELECT COUNT(*) FROM enrollments e WHERE e.workshop_id = w.id) AS inscritos,
       (SELECT COUNT(*) FROM certificates c WHERE c.workshop_id = w.id) AS certificados
FROM workshops w
LEFT JOIN users u ON u.id = w.instructor_id
ORDER BY w.workshop_date;
