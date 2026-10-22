import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PageResponse } from '@core/models/api.model';
import { ComplianceQuery, ComplianceRow } from '@core/models/report.model';
import { environment } from '@env/environment';
import { Observable } from 'rxjs';

/**
 * Acceso a `/api/reports/compliance` (RF-020).
 *
 * Solo el ADMIN tiene el permiso `reports.view`, así que esta API no se usa desde ninguna
 * pantalla compartida: la ruta está protegida por el guard y el menú no se muestra a los
 * demás roles.
 */
@Injectable({ providedIn: 'root' })
export class ReportApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/reports/compliance`;

  list(query: ComplianceQuery): Observable<PageResponse<ComplianceRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (query.sort) {
      params = params.set('sort', query.sort);
    }
    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.workshopId != null) {
      params = params.set('workshopId', query.workshopId);
    }
    if (query.from) {
      params = params.set('from', query.from);
    }
    if (query.to) {
      params = params.set('to', query.to);
    }

    return this.http.get<PageResponse<ComplianceRow>>(this.base, { params });
  }
}
