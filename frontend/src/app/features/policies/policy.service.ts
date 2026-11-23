import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, PageRequest, toParams } from '../../core/http/page';
import { Policy, PolicySearch, PolicySummary, QuoteRequest } from './policy.models';

const API = '/api/policies';

@Injectable({ providedIn: 'root' })
export class PolicyService {
  private readonly http = inject(HttpClient);

  search(filter: PolicySearch, page: PageRequest): Observable<Page<PolicySummary>> {
    const params = toParams({
      query: filter.query.trim(),
      status: filter.status,
      productCode: filter.productCode,
      customerId: filter.customerId,
      page: page.page,
      size: page.size,
      sort: page.sort,
    });
    return this.http.get<Page<PolicySummary>>(API, { params });
  }

  get(id: number): Observable<Policy> {
    return this.http.get<Policy>(`${API}/${id}`);
  }

  createQuote(request: QuoteRequest): Observable<Policy> {
    return this.http.post<Policy>(`${API}/quotes`, request);
  }

  issue(id: number): Observable<Policy> {
    return this.http.post<Policy>(`${API}/${id}/issue`, {});
  }

  cancel(id: number, reason: string): Observable<Policy> {
    return this.http.post<Policy>(`${API}/${id}/cancel`, { reason });
  }
}
