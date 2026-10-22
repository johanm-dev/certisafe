-- =====================================================================
-- CertiSafe - Migracion RNF-015: bloqueo de cuenta por intentos fallidos
--
-- Se aplica sobre una base CREADA ANTES de esta correccion.
-- Si creas la base desde cero con certisafe_schema.sql ya no hace falta:
-- el esquema ya incluye las dos columnas.
--
-- RNF-015: tras 5 intentos fallidos consecutivos la cuenta se bloquea
-- 15 minutos y la API responde 429 Too Many Requests.
-- =====================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_failed_attempts') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_failed_attempts
            CHECK (failed_login_attempts >= 0);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_users_locked_until
    ON users (locked_until) WHERE locked_until IS NOT NULL;

COMMENT ON COLUMN users.failed_login_attempts IS 'Intentos de login fallidos consecutivos (RNF-015)';
COMMENT ON COLUMN users.locked_until IS 'Timestamp hasta el que la cuenta queda bloqueada (RNF-015)';
