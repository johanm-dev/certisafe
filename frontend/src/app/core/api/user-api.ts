import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PageResponse } from '@core/models/api.model';
import {
  ROLE_INSTRUCTOR_ID,
  Role,
  User,
  UserCreateRequest,
  UserUpdateRequest,
} from '@core/models/user.model';
import { environment } from '@env/environment';
import { Observable } from 'rxjs';

export interface UserQuery {
  page: number;
  size: number;
  sort?: string;
  search?: string;
  roleId?: number;
  active?: boolean;
}

/** Acceso a `/api/users` y `/api/roles`. */
@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/users`;

  list(query: UserQuery): Observable<PageResponse<User>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (query.sort) {
      params = params.set('sort', query.sort);
    }
    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.roleId != null) {
      params = params.set('roleId', query.roleId);
    }
    if (query.active != null) {
      params = params.set('active', query.active);
    }

    return this.http.get<PageResponse<User>>(this.base, { params });
  }

  roles(): Observable<Role[]> {
    return this.http.get<Role[]>(`${environment.apiBaseUrl}/roles`);
  }

  create(request: UserCreateRequest): Observable<User> {
    return this.http.post<User>(this.base, request);
  }

  update(id: number, request: UserUpdateRequest): Observable<User> {
    return this.http.put<User>(`${this.base}/${id}`, request);
  }

  /** Borrado lógico: activar o desactivar. */
  changeStatus(id: number, active: boolean): Observable<User> {
    return this.http.patch<User>(`${this.base}/${id}/status`, { active });
  }

  /**
   * Instructores activos, para el selector de asignacion.
   *
   * Se pide `size=100`, que es el maximo que admite la API. Si algun dia hubiera mas de
   * 100 instructores activos habria que sustituir esto por un autocompletado paginado.
   */
  activeInstructors(): Observable<PageResponse<User>> {
    return this.list({
      page: 0,
      size: 100,
      sort: 'firstName,asc',
      roleId: ROLE_INSTRUCTOR_ID,
      active: true,
    });
  }
}
