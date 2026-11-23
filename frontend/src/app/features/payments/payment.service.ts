import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, PageRequest, toParams } from '../../core/http/page';
import { InstallmentRow, InstallmentSearch, PaymentMethod, PaymentResult } from './payment.models';

const API = '/api/installments';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);

  installments(filter: InstallmentSearch, page: PageRequest): Observable<Page<InstallmentRow>> {
    const params = toParams({
      query: filter.query.trim(),
      status: filter.status,
      unpaidOnly: filter.unpaidOnly ? 'true' : null,
      policyId: filter.policyId,
      page: page.page,
      size: page.size,
    });
    return this.http.get<Page<InstallmentRow>>(API, { params });
  }

  pay(installmentId: number, paymentMethod: PaymentMethod): Observable<PaymentResult> {
    return this.http.post<PaymentResult>(`${API}/${installmentId}/payments`, { paymentMethod });
  }
}
