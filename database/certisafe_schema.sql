-- =====================================================================
-- CertiSafe - Esquema de Base de Datos PostgreSQL 15+
-- Basado en diagrama ER: certisafe_er (1) (1).drawio
-- 17 entidades, nomenclatura en inglés (RNF-003)
-- =====================================================================

-- 1. EXTENSIONES
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. TIPOS ENUM (PostgreSQL nativo)
CREATE TYPE workshop_status AS ENUM ('PROGRAMADO', 'EN_PROGRESO', 'FINALIZADO', 'CANCELADO');
CREATE TYPE attendance_status AS ENUM ('PENDIENTE', 'ASISTIO', 'AUSENTE');
CREATE TYPE batch_status AS ENUM ('PROCESANDO', 'COMPLETADO', 'CON_ERRORES', 'FALLIDO');

-- 3. TABLAS - AUTENTICACIÓN Y USUARIOS (Dominio azul)
-- ---------------------------------------------------------------

CREATE TABLE roles (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50) NOT NULL UNIQUE,
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     TEXT,
    module          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE roles_permissions (
    id              BIGSERIAL PRIMARY KEY,
    role_id         BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (role_id, permission_id)
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(30),
    role_id         BIGINT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    last_access     TIMESTAMPTZ,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(512) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recovery_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(512) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_recovery_expires CHECK (expires_at > created_at)
);

-- 4. TABLAS - NEGOCIO (Dominio verde)
-- ---------------------------------------------------------------

CREATE TABLE workshops (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    workshop_date       TIMESTAMPTZ NOT NULL,
    duration_minutes    INTEGER NOT NULL CHECK (duration_minutes > 0),
    location            VARCHAR(300) NOT NULL,
    max_capacity        INTEGER NOT NULL CHECK (max_capacity > 0),
    status              workshop_status NOT NULL DEFAULT 'PROGRAMADO',
    instructor_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_by          BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status_changed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE workshop_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    workshop_id         BIGINT NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
    instructor_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_by         BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_current          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE enrollments (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workshop_id         BIGINT NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
    enrolled_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    attendance_status   attendance_status NOT NULL DEFAULT 'PENDIENTE',
    assigned_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, workshop_id)
);

CREATE TABLE certificates (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workshop_id         BIGINT NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
    enrollment_id       BIGINT NOT NULL UNIQUE REFERENCES enrollments(id) ON DELETE RESTRICT,
    instructor_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    certificate_number  VARCHAR(50) NOT NULL UNIQUE,
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked             BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at          TIMESTAMPTZ,
    revocation_reason   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. TABLAS - AUDITORÍA Y TRAZABILIDAD (Dominio naranja)
-- ---------------------------------------------------------------

CREATE TABLE status_history (
    id                  BIGSERIAL PRIMARY KEY,
    workshop_id         BIGINT NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
    previous_status     VARCHAR(50),
    new_status          VARCHAR(50) NOT NULL,
    changed_by          BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason              TEXT
);

CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                VARCHAR(50) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    message             TEXT NOT NULL,
    read                BOOLEAN NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action              VARCHAR(100) NOT NULL,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           BIGINT,
    old_values          JSONB,
    new_values          JSONB,
    ip_address          VARCHAR(45),
    user_agent          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. TABLAS - SOPORTE (Dominio gris)
-- ---------------------------------------------------------------

CREATE TABLE import_batches (
    id                  BIGSERIAL PRIMARY KEY,
    file_name           VARCHAR(255) NOT NULL,
    file_type           VARCHAR(50) NOT NULL,
    entity_type         VARCHAR(50) NOT NULL,
    total_rows          INTEGER NOT NULL DEFAULT 0,
    processed_rows      INTEGER NOT NULL DEFAULT 0,
    rejected_rows       INTEGER NOT NULL DEFAULT 0,
    uploaded_by         BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              batch_status NOT NULL DEFAULT 'PROCESANDO'
);

CREATE TABLE import_errors (
    id                  BIGSERIAL PRIMARY KEY,
    batch_id            BIGINT NOT NULL REFERENCES import_batches(id) ON DELETE CASCADE,
    row_number          INTEGER NOT NULL,
    error_reason        TEXT NOT NULL,
    row_data            JSONB NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE files (
    id                  BIGSERIAL PRIMARY KEY,
    file_name           VARCHAR(255) NOT NULL,
    file_path           VARCHAR(500) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    mime_type           VARCHAR(100) NOT NULL,
    entity_type         VARCHAR(50),
    entity_id           BIGINT,
    uploaded_by         BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE configurations (
    id                  BIGSERIAL PRIMARY KEY,
    config_key          VARCHAR(100) NOT NULL UNIQUE,
    value               TEXT NOT NULL,
    description         TEXT,
    updated_by          BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. ÍNDICES PARA RENDIMIENTO (RNF-007, RNF-008)
-- ---------------------------------------------------------------

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(active) WHERE active;
CREATE INDEX idx_users_role ON users(role_id);

CREATE INDEX idx_workshops_status ON workshops(status);
CREATE INDEX idx_workshops_instructor ON workshops(instructor_id);
CREATE INDEX idx_workshops_date ON workshops(workshop_date);
CREATE INDEX idx_workshops_created_by ON workshops(created_by);

CREATE INDEX idx_enrollments_workshop ON enrollments(workshop_id);
CREATE INDEX idx_enrollments_user ON enrollments(user_id);
CREATE INDEX idx_enrollments_status ON enrollments(attendance_status);
CREATE INDEX idx_enrollments_workshop_status ON enrollments(workshop_id, attendance_status);

CREATE INDEX idx_certificates_user ON certificates(user_id);
CREATE INDEX idx_certificates_workshop ON certificates(workshop_id);
CREATE INDEX idx_certificates_expires ON certificates(expires_at);
CREATE INDEX idx_certificates_revoked ON certificates(revoked) WHERE NOT revoked;

CREATE INDEX idx_assignments_workshop_instructor ON workshop_assignments(workshop_id, instructor_id);
-- Un único instructor vigente por taller (índice único parcial).
-- No se puede expresar como UNIQUE de tabla: PostgreSQL no admite WHERE en constraints.
CREATE UNIQUE INDEX uq_workshop_one_current_assignment ON workshop_assignments(workshop_id) WHERE is_current;

CREATE INDEX idx_status_history_workshop ON status_history(workshop_id);
CREATE INDEX idx_status_history_changed_by ON status_history(changed_by);

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read) WHERE NOT read;
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);

CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);

CREATE INDEX idx_batches_uploaded_by ON import_batches(uploaded_by);
CREATE INDEX idx_batches_status ON import_batches(status);
CREATE INDEX idx_errors_batch ON import_errors(batch_id);

CREATE INDEX idx_files_entity ON files(entity_type, entity_id);
CREATE INDEX idx_files_uploaded_by ON files(uploaded_by);

CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE NOT used;
CREATE INDEX idx_recovery_tokens_user ON recovery_tokens(user_id);
CREATE INDEX idx_recovery_tokens_expires ON recovery_tokens(expires_at) WHERE NOT used;

-- 8. TRIGGERS updated_at AUTOMÁTICO
-- ---------------------------------------------------------------

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_workshops_updated_at BEFORE UPDATE ON workshops FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_enrollments_updated_at BEFORE UPDATE ON enrollments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_configurations_updated_at BEFORE UPDATE ON configurations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 9. DATOS SEMILLA (Seed Data)
-- ---------------------------------------------------------------

INSERT INTO roles (id, name, description) VALUES
    (1, 'ADMIN', 'Líder de HSE - Acceso total al sistema'),
    (2, 'INSTRUCTOR', 'Especialista técnico - Dicta talleres'),
    (3, 'OPERATOR', 'Operario de planta - Autoconsulta')
ON CONFLICT (id) DO NOTHING;

INSERT INTO permissions (id, name, description, module) VALUES
    (1, 'users.create', 'Crear usuarios', 'USERS'),
    (2, 'users.read', 'Consultar usuarios', 'USERS'),
    (3, 'users.update', 'Editar usuarios', 'USERS'),
    (4, 'users.delete', 'Activar/desactivar usuarios', 'USERS'),
    (5, 'workshops.create', 'Crear talleres', 'WORKSHOPS'),
    (6, 'workshops.read', 'Consultar talleres', 'WORKSHOPS'),
    (7, 'workshops.update', 'Editar talleres', 'WORKSHOPS'),
    (8, 'workshops.delete', 'Eliminar talleres', 'WORKSHOPS'),
    (9, 'workshops.assign_instructor', 'Asignar instructor', 'WORKSHOPS'),
    (10, 'workshops.change_status', 'Cambiar estado taller', 'WORKSHOPS'),
    (11, 'enrollments.manage', 'Gestionar inscripciones y asistencia', 'ENROLLMENTS'),
    (12, 'certificates.view', 'Ver certificaciones', 'CERTIFICATES'),
    (13, 'certificates.revoke', 'Revocar certificaciones', 'CERTIFICATES'),
    (14, 'import.execute', 'Ejecutar cargas masivas', 'IMPORT'),
    (15, 'export.execute', 'Exportar datos', 'EXPORT'),
    (16, 'reports.view', 'Ver reportes de cumplimiento', 'REPORTS'),
    (17, 'notifications.read', 'Leer notificaciones', 'NOTIFICATIONS'),
    (18, 'audit.read', 'Consultar auditoría', 'AUDIT'),
    (19, 'config.manage', 'Gestionar configuración', 'CONFIG')
ON CONFLICT (id) DO NOTHING;

-- ADMIN: todos los permisos
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- INSTRUCTOR
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'INSTRUCTOR' AND p.name IN (
    'workshops.read', 'workshops.change_status', 'enrollments.manage',
    'certificates.view', 'notifications.read'
)
ON CONFLICT DO NOTHING;

-- OPERATOR
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'OPERATOR' AND p.name IN (
    'workshops.read', 'enrollments.manage', 'certificates.view', 'notifications.read'
)
ON CONFLICT DO NOTHING;

INSERT INTO configurations (config_key, value, description) VALUES
    ('certificate_validity_days', '365', 'Días de vigencia de certificaciones'),
    ('max_login_attempts', '5', 'Intentos máximos de login fallidos antes de bloqueo'),
    ('lockout_duration_minutes', '15', 'Minutos de bloqueo tras exceder intentos'),
    ('recovery_token_expiry_minutes', '15', 'Expiración token recuperación'),
    ('access_token_expiry_minutes', '15', 'Expiración JWT access token'),
    ('refresh_token_expiry_days', '7', 'Expiración JWT refresh token'),
    ('bulk_batch_size', '500', 'Tamaño de lote para procesamiento masivo'),
    ('max_import_rows', '5000', 'Máximo filas permitidas en archivo importación')
ON CONFLICT (config_key) DO NOTHING;

-- 10. COMENTARIOS EN TABLAS Y COLUMNAS
-- ---------------------------------------------------------------

COMMENT ON TABLE users IS 'Usuarios del sistema (ADMIN, INSTRUCTOR, OPERATOR)';
COMMENT ON COLUMN users.password_hash IS 'Hash BCrypt costo 12, nunca almacenar plano';
COMMENT ON COLUMN users.failed_login_attempts IS 'Intentos de login fallidos consecutivos (RNF-015)';
COMMENT ON COLUMN users.locked_until IS 'Timestamp hasta el que la cuenta queda bloqueada (RNF-015)';
COMMENT ON TABLE workshops IS 'Talleres de seguridad industrial';
COMMENT ON COLUMN workshops.status IS 'Estado: PROGRAMADO, EN_PROGRESO, FINALIZADO, CANCELADO';
COMMENT ON TABLE enrollments IS 'Inscripciones de operarios a talleres';
COMMENT ON COLUMN enrollments.attendance_status IS 'PENDIENTE, ASISTIO, AUSENTE';
COMMENT ON TABLE certificates IS 'Certificaciones emitidas por asistencia a talleres';
COMMENT ON COLUMN certificates.enrollment_id IS 'UNIQUE - Un certificado por inscripción (1:0..1)';
COMMENT ON TABLE audit_logs IS 'Registro inmutable de operaciones críticas para auditoría legal';
COMMENT ON TABLE import_batches IS 'Lotes de importación masiva (Excel/CSV)';
COMMENT ON TABLE configurations IS 'Configuración dinámica clave-valor del sistema';

-- =====================================================================
-- FIN DEL SCRIPT - 17 tablas creadas
-- =====================================================================
