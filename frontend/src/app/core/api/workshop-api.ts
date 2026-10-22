import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  AssignInstructorRequest,
  StatusChangeRequest,
  StatusHistory,
  Workshop,
  WorkshopCreateRequest,
  WorkshopQuery,
  WorkshopUpdateRequest,
} from '@core/models/workshop.model';
import { PageResponse } from '@core/models/api.model';
import { environment } from '@env/environment';
import { Observable } from 'rxjs';

/** Acceso a `/api/workshops`. */
@Injectable({ providedIn: 'root' })
export class WorkshopApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/workshops`;

  list(query: WorkshopQuery): Observable<PageResponse<Workshop>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (query.sort) {
      params = params.set('sort', query.sort);
    }
    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.instructorId != null) {
      params = params.set('instructorId', query.instructorId);
    }
    if (query.from) {
      params = params.set('from', query.from);
    }
    if (query.to) {
      params = params.set('to', query.to);
    }

    return this.http.get<PageResponse<Workshop>>(this.base, { params });
  }

  get(id: number): Observable<Workshop> {
    return this.http.get<Workshop>(`${this.base}/${id}`);
  }

  history(id: number): Observable<StatusHistory[]> {
    return this.http.get<StatusHistory[]>(`${this.base}/${id}/history`);
  }

  create(body: WorkshopCreateRequest): Observable<Workshop> {
    return this.http.post<Workshop>(this.base, body);
  }

  update(id: number, body: WorkshopUpdateRequest): Observable<Workshop> {
    return this.http.put<Workshop>(`${this.base}/${id}`, body);
  }

  changeStatus(id: number, body: StatusChangeRequest): Observable<Workshop> {
    return this.http.patch<Workshop>(`${this.base}/${id}/status`, body);
  }

  assignInstructor(id: number, body: AssignInstructorRequest): Observable<Workshop> {
    return this.http.put<Workshop>(`${this.base}/${id}/instructor`, body);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
