import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, PageRequest, toParams } from '../../core/http/page';
import { ExecutionSummary } from '../batch/batch.models';
import {
  CollectionFile,
  CollectionFileDetail,
  CollectionResult,
  Direction,
  Outcome,
} from './collection-file.models';

const API = '/api/collection-files';

@Injectable({ providedIn: 'root' })
export class CollectionFileService {
  private readonly http = inject(HttpClient);

  list(direction: Direction | null, page: PageRequest): Observable<Page<CollectionFile>> {
    return this.http.get<Page<CollectionFile>>(API, {
      params: toParams({ direction, page: page.page, size: page.size }),
    });
  }

  detail(id: number): Observable<CollectionFileDetail> {
    return this.http.get<CollectionFileDetail>(`${API}/${id}`);
  }

  results(
    id: number,
    outcome: Outcome | null,
    page: PageRequest,
  ): Observable<Page<CollectionResult>> {
    return this.http.get<Page<CollectionResult>>(`${API}/${id}/results`, {
      params: toParams({ outcome, page: page.page, size: page.size }),
    });
  }

  /** Starts collectionRequestExportJob for the given business date (default: today). */
  createRequest(runDate: string | null): Observable<ExecutionSummary> {
    return this.http.post<ExecutionSummary>(`${API}/requests`, { runDate });
  }

  uploadResult(file: File): Observable<ExecutionSummary> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ExecutionSummary>(`${API}/results`, body);
  }

  /** Demo: the bank simulator answers a request file and the answer is imported. */
  simulateBankAnswer(id: number): Observable<ExecutionSummary> {
    return this.http.post<ExecutionSummary>(`${API}/${id}/bank-answer`, null);
  }

  restart(id: number): Observable<ExecutionSummary> {
    return this.http.post<ExecutionSummary>(`${API}/${id}/restart`, null);
  }

  /** Files need the bearer token, so they are fetched as a blob instead of a plain link. */
  download(id: number): Observable<Blob> {
    return this.http.get(`${API}/${id}/download`, { responseType: 'blob' });
  }
}
