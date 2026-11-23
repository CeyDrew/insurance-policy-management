import { HttpClient, HttpEvent } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, toParams } from '../../core/http/page';
import {
  ExecutionDetail,
  ExecutionSummary,
  ImportError,
  JobSummary,
  ReportFile,
} from './batch.models';

const API = '/api/batch';

@Injectable({ providedIn: 'root' })
export class BatchService {
  private readonly http = inject(HttpClient);

  jobs(): Observable<JobSummary[]> {
    return this.http.get<JobSummary[]>(`${API}/jobs`);
  }

  /** Starts a job asynchronously; the returned execution is usually still STARTING. */
  launch(jobName: string, parameters: Record<string, string>): Observable<ExecutionSummary> {
    return this.http.post<ExecutionSummary>(
      `${API}/jobs/${encodeURIComponent(jobName)}/executions`,
      {
        parameters,
      },
    );
  }

  executions(jobName: string, page: number, size: number): Observable<Page<ExecutionSummary>> {
    return this.http.get<Page<ExecutionSummary>>(
      `${API}/jobs/${encodeURIComponent(jobName)}/executions`,
      { params: toParams({ page, size }) },
    );
  }

  execution(id: number): Observable<ExecutionDetail> {
    return this.http.get<ExecutionDetail>(`${API}/executions/${id}`);
  }

  importErrors(id: number): Observable<ImportError[]> {
    return this.http.get<ImportError[]>(`${API}/executions/${id}/errors`);
  }

  /** Uploads a claim CSV with progress events; the final response is the started import execution. */
  importClaims(file: File): Observable<HttpEvent<ExecutionSummary>> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ExecutionSummary>(`${API}/claim-imports`, body, {
      reportProgress: true,
      observe: 'events',
    });
  }

  reports(): Observable<ReportFile[]> {
    return this.http.get<ReportFile[]>(`${API}/reports`);
  }

  /** Reports need the bearer token, so they are fetched as a blob instead of a plain link. */
  downloadReport(name: string): Observable<Blob> {
    return this.http.get(`${API}/reports/${encodeURIComponent(name)}`, { responseType: 'blob' });
  }
}
