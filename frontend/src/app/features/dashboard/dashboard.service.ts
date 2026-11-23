import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Dashboard } from './dashboard.models';

const API = '/api/dashboard';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  get(): Observable<Dashboard> {
    return this.http.get<Dashboard>(API);
  }

  /** Recomputes the materialized views behind the dashboard (ADMIN). */
  refresh(): Observable<{ refreshedAt: string }> {
    return this.http.post<{ refreshedAt: string }>(`${API}/refresh`, null);
  }
}
