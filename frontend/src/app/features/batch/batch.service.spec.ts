import { TestBed } from '@angular/core/testing';
import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BatchService } from './batch.service';

describe('BatchService', () => {
  let service: BatchService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BatchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('launches a job with its business parameters', () => {
    service.launch('overdueInstallmentJob', { runDate: '2026-11-23' }).subscribe();

    const req = http.expectOne('/api/batch/jobs/overdueInstallmentJob/executions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ parameters: { runDate: '2026-11-23' } });
    req.flush({ id: 7 });
  });

  it('requests execution history page by page', () => {
    service.executions('policyRenewalJob', 2, 10).subscribe();

    const req = http.expectOne((r) => r.url === '/api/batch/jobs/policyRenewalJob/executions');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('uploads the CSV as multipart form data and reports progress', () => {
    const file = new File(
      ['policy_number;incident_date;description;estimated_amount'],
      'hasar.csv',
      {
        type: 'text/csv',
      },
    );
    const types: HttpEventType[] = [];
    service.importClaims(file).subscribe((event) => types.push(event.type));

    const req = http.expectOne('/api/batch/claim-imports');
    expect(req.request.reportProgress).toBe(true);
    const body = req.request.body as FormData;
    expect((body.get('file') as File).name).toBe('hasar.csv');
    req.event({ type: HttpEventType.UploadProgress, loaded: 50, total: 100 });
    req.flush({ id: 9 });

    expect(types).toContain(HttpEventType.UploadProgress);
    expect(types).toContain(HttpEventType.Response);
  });

  it('downloads reports as blobs with an encoded file name', () => {
    service.downloadReport('uretim-raporu-2026-10.xlsx').subscribe();

    const req = http.expectOne('/api/batch/reports/uretim-raporu-2026-10.xlsx');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['PK']));
  });
});
