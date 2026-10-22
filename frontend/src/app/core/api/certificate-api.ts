import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PageResponse } from '@core/models/api.model';
import { Certificate, CertificateQuery } from '@core/models/certificate.model';
import { environment } from '@env/environment';
import { Observable } from 'rxjs';

/**
 * Acceso a `/api/certificates`.
 *
 * No hay método para emitir: el certificado nace del registro de asistencia (RF-013). Si
 * existiera un endpoint de emisión manual se podría certificar a alguien que no asistió.
 */
@Injectable({ providedIn: 'root' })
export class CertificateApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/certificates`;

  list(query: CertificateQuery): Observable<PageResponse<Certificate>> {
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
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.search) {
      params = params.set('search', query.search);
    }

    return this.http.get<PageResponse<Certificate>>(this.base, { params });
  }

  /** Certificaciones propias. No exige permiso de gestión: es el historial del operario. */
  mine(query: CertificateQuery): Observable<PageResponse<Certificate>> {
    const params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort ?? 'issuedAt,desc');

    return this.http.get<PageResponse<Certificate>>(`${this.base}/me`, { params });
  }

  /** Revoca dejando el motivo. Requiere el permiso `certificates.revoke` (solo ADMIN). */
  revoke(id: number, reason: string): Observable<Certificate> {
    return this.http.patch<Certificate>(`${this.base}/${id}/revoke`, { reason });
  }
}
