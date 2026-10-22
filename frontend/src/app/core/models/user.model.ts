/** Contratos de usuarios y roles. Espejo de `UserResponse` y `RoleResponse` del backend. */

export interface Role {
  id: number;
  name: string;
  description?: string | null;
}

export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  phone?: string | null;
  role: Role;
  active: boolean;
  lastAccess: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Id de rol real: los roles son fijos y vienen del esquema. */
export const ROLE_ADMIN_ID = 1;
export const ROLE_INSTRUCTOR_ID = 2;
export const ROLE_OPERATOR_ID = 3;

/**
 * Política de contraseña del backend (RNF de seguridad): mínimo 8 caracteres, al menos una
 * mayúscula, una minúscula, un dígito y un carácter especial. Se replica aquí para avisar en
 * el formulario en vez de dejar que el servidor responda 400.
 */
export const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/;

export interface UserCreateRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string | null;
  roleId: number;
  active?: boolean;
}

/** El PUT no cambia la contraseña: eso es otra operación. */
export interface UserUpdateRequest {
  email: string;
  firstName: string;
  lastName: string;
  phone?: string | null;
  roleId: number;
  active: boolean;
}

export interface StatusUpdateRequest {
  active: boolean;
}
