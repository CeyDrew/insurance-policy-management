import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, PageRequest, toParams } from '../../core/http/page';
import {
  Claim,
  ClaimDocument,
  ClaimSearch,
  ClaimSummary,
  DocumentType,
  ReportClaimRequest,
} from './claim.models';

const API = '/api/claims';

@Injectable({ providedIn: 'root' })
export class ClaimService {
  private readonly http = inject(HttpClient);

  search(filter: ClaimSearch, page: PageRequest): Observable<Page<ClaimSummary>> {
    const params = toParams({
      query: filter.query.trim(),
      status: filter.status,
      mine: filter.mine ? 'true' : null,
      policyId: filter.policyId,
      page: page.page,
      size: page.size,
      sort: page.sort,
    });
    return this.http.get<Page<ClaimSummary>>(API, { params });
  }

  get(id: number): Observable<Claim> {
    return this.http.get<Claim>(`${API}/${id}`);
  }

  report(request: ReportClaimRequest): Observable<Claim> {
    return this.http.post<Claim>(API, request);
  }

  assignToMe(id: number): Observable<Claim> {
    return this.http.post<Claim>(`${API}/${id}/assign`, {});
  }

  approve(id: number, approvedAmount: number, note: string | null): Observable<Claim> {
    return this.http.post<Claim>(`${API}/${id}/approve`, { approvedAmount, note });
  }

  reject(id: number, note: string): Observable<Claim> {
    return this.http.post<Claim>(`${API}/${id}/reject`, { note });
  }

  pay(id: number): Observable<Claim> {
    return this.http.post<Claim>(`${API}/${id}/pay`, {});
  }

  uploadDocument(id: number, documentType: DocumentType, file: File): Observable<ClaimDocument> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ClaimDocument>(`${API}/${id}/documents`, body, {
      params: toParams({ documentType }),
    });
  }

  downloadDocument(id: number, documentId: number): Observable<Blob> {
    return this.http.get(`${API}/${id}/documents/${documentId}`, { responseType: 'blob' });
  }
}
