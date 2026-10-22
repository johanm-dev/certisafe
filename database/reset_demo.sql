-- ============================================================================
--  reset_demo.sql  --  BORRA LOS DATOS DE DEMOSTRACION
--
--  Deshace lo que hizo seed_demo.sql para poder volver a sembrar desde cero.
--  NO toca usuarios, roles ni permisos: solo los talleres de demostracion y lo
--  que cuelga de ellos.
--
--  Uso:
--      psql -U postgres -h localhost -d Certisafe -f database/reset_demo.sql
--
--  Las inscripciones, los certificados, el historial de estados y las
--  asignaciones de estos talleres se borran en cascada con ellos.
-- ============================================================================

BEGIN;

-- Inscripciones y certificados de los talleres de demostracion. El borrado del
-- taller ya los arrastraria en cascada; se hace explicito para que quede escrito
-- que se esta borrando un certificado emitido, y no por accidente.
DELETE FROM certificates
WHERE workshop_id IN (SELECT id FROM workshops WHERE name IN (
    'Trabajo seguro en alturas',
    'Espacios confinados: entrada y rescate',
    'Manejo de extintores y conato de incendio',
    'Primeros auxilios y RCP basico',
    'Riesgo electrico y bloqueo etiquetado LOTO',
    'Inspeccion de EPP y seguridad vial'
));

DELETE FROM enrollments
WHERE workshop_id IN (SELECT id FROM workshops WHERE name IN (
    'Trabajo seguro en alturas',
    'Espacios confinados: entrada y rescate',
    'Manejo de extintores y conato de incendio',
    'Primeros auxilios y RCP basico',
    'Riesgo electrico y bloqueo etiquetado LOTO',
    'Inspeccion de EPP y seguridad vial'
));

-- El historial, las asignaciones y (si existieran) los lotes de importacion y
-- las notificaciones caen con el taller.
DELETE FROM workshops WHERE name IN (
    'Trabajo seguro en alturas',
    'Espacios confinados: entrada y rescate',
    'Manejo de extintores y conato de incendio',
    'Primeros auxilios y RCP basico',
    'Riesgo electrico y bloqueo etiquetado LOTO',
    'Inspeccion de EPP y seguridad vial'
);

COMMIT;

SELECT COUNT(*) AS talleres_restantes FROM workshops;
