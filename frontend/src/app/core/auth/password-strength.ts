/**
 * Politica de contrasenas, replicada del backend (`PasswordPolicy`).
 *
 * Esta aqui y no en cada formulario para que los dos (cambio y recuperacion) no puedan
 * divergir: si el backend endurece la regla, se cambia en un sitio.
 */
export const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/;

export type StrengthLevel = 'vacia' | 'debil' | 'media' | 'fuerte';

export interface PasswordCheck {
  label: string;
  ok: boolean;
}

export interface PasswordStrength {
  level: StrengthLevel;
  label: string;
  /** Porcentaje de la barra (0 a 100). */
  percent: number;
  /** Color de la barra, con los tokens del tema. */
  color: string;
  checks: PasswordCheck[];
}

/**
 * Mide la fortaleza de una contrasena mientras se escribe (HU-17, tarea 18).
 *
 * No pretende ser un medidor criptografico: comprueba lo mismo que exige la politica y suma
 * un punto por longitud, que es lo unico que el usuario puede corregir mirando la barra.
 * Los cinco primeros criterios son obligatorios; el sexto (12 caracteres) solo mejora.
 */
export function measurePassword(value: string): PasswordStrength {
  const checks: PasswordCheck[] = [
    { label: 'Al menos 8 caracteres', ok: value.length >= 8 },
    { label: 'Una mayúscula', ok: /[A-Z]/.test(value) },
    { label: 'Una minúscula', ok: /[a-z]/.test(value) },
    { label: 'Un dígito', ok: /\d/.test(value) },
    { label: 'Un carácter especial', ok: /[^A-Za-z0-9]/.test(value) },
  ];

  if (value.length === 0) {
    return {
      level: 'vacia',
      label: 'Sin contraseña',
      percent: 0,
      color: 'transparent',
      checks,
    };
  }

  const cumplidos = checks.filter((check) => check.ok).length;
  const extra = value.length >= 12 ? 1 : 0;
  const puntos = cumplidos + extra;

  if (puntos <= 2) {
    return { level: 'debil', label: 'Débil', percent: 33, color: 'var(--mat-sys-error)', checks };
  }
  if (puntos <= 4) {
    return { level: 'media', label: 'Media', percent: 66, color: '#f9a825', checks };
  }
  return { level: 'fuerte', label: 'Fuerte', percent: 100, color: '#2e7d32', checks };
}
