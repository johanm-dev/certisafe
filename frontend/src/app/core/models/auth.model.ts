/** Contratos de autenticacion. Espejo de los DTO de `com.certisafe.auth.dto`. */

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Usuario autenticado que devuelven `/api/auth/login`, `/api/auth/refresh` y `/api/auth/me`.
 *
 * Ojo: `role` es el NOMBRE del rol (`'ADMIN'`), no un objeto, y `permissions` son los
 * permisos granulares que el backend recalcula en cada peticion.
 */
export interface AuthenticatedUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  role: string;
  permissions: string[];
}

/**
 * Cuerpo de `PUT /api/auth/change-password` (RF-024, HU-18).
 *
 * La confirmación viaja al servidor aunque el formulario ya la valide: es una entrada que la
 * HU enumera y el backend no debe fiarse de que el cliente la haya comprobado.
 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmNewPassword: string;
}

/** Cuerpo de `POST /api/auth/recovery-request` (RF-023, HU-17). */
export interface PasswordRecoveryRequest {
  email: string;
}

/**
 * Cuerpo de `POST /api/auth/reset-password` (RF-023, HU-17).
 *
 * La confirmación se manda aunque el formulario ya la compare: es una entrada que la HU
 * enumera y el backend no debe fiarse de que el cliente haya hecho su trabajo.
 */
export interface PasswordResetRequest {
  token: string;
  newPassword: string;
  confirmNewPassword: string;
}

/** Respuesta de los dos endpoints de recuperación: el mensaje que se le muestra al usuario. */
export interface RecoveryResponse {
  message: string;
}

/** Respuesta de login y de refresh. `expiresIn` son SEGUNDOS de vida del access token. */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthenticatedUser;
}
