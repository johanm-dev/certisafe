import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PageResponse } from '@core/models/api.model';
import {
  AttendanceStatus,
  EnrollRequest,
  Enrollment,
  EnrollmentQuery,
} from '@core/models/enrollment.model';
import { environment } from '@env/environment';
import { Observable } from 'rxjs';

/** Acceso a `/api/enrollments`. */
@Injectable({ providedIn: 'root' })
export class EnrollmentApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/enrollments`;

  list(query: EnrollmentQuery): Observable<PageResponse<Enrollment>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (query.sort) {
      params = params.set('sort', query.sort);
    }
    if (query.workshopId != null) {
      params = params.set('workshopId', query.workshopId);
    }
    if (query.userId != null) {
      params = params.set('userId', query.userId);
    }
    if (query.attendanceStatus) {
      params = params.set('attendanceStatus', query.attendanceStatus);
    }
    if (query.search) {
      params = params.set('search', query.search);
    }

    return this.http.get<PageResponse<Enrollment>>(this.base, { params });
  }

  /**
   * Inscripciones propias. No exige permiso de gestión en el backend: es el historial del
   * OPERATOR, que es su caso de uso principal.
   */
  mine(query: EnrollmentQuery): Observable<PageResponse<Enrollment>> {
    const params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort ?? 'enrolledAt,desc');

    return this.http.get<PageResponse<Enrollment>>(`${this.base}/me`, { params });
  }

  enroll(request: EnrollRequest): Observable<Enrollment> {
    return this.http.post<Enrollment>(this.base, request);
  }

  registerAttendance(id: number, attendanceStatus: AttendanceStatus): Observable<Enrollment> {
    return this.http.patch<Enrollment>(`${this.base}/${id}/attendance`, { attendanceStatus });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
