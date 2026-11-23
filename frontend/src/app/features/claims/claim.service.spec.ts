import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PaymentService } from '../payments/payment.service';
import { ClaimService } from './claim.service';

describe('ClaimService and PaymentService', () => {
  let claims: ClaimService;
  let payments: PaymentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    claims = TestBed.inject(ClaimService);
    payments = TestBed.inject(PaymentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('searches only the current handler claims when "mine" is set', () => {
    claims
      .search(
        { query: ' HSR ', status: 'IN_REVIEW', mine: true, policyId: null },
        { page: 0, size: 20 },
      )
      .subscribe();

    const req = http.expectOne((r) => r.url === '/api/claims');
    expect(req.request.params.get('query')).toBe('HSR');
    expect(req.request.params.get('status')).toBe('IN_REVIEW');
    expect(req.request.params.get('mine')).toBe('true');
    expect(req.request.params.has('policyId')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('sends decisions to the matching endpoints', () => {
    claims.approve(5, 1500, 'uygun').subscribe();
    claims.reject(5, 'teminat dışı').subscribe();
    claims.pay(5).subscribe();

    expect(http.expectOne('/api/claims/5/approve').request.body).toEqual({
      approvedAmount: 1500,
      note: 'uygun',
    });
    expect(http.expectOne('/api/claims/5/reject').request.body).toEqual({ note: 'teminat dışı' });
    expect(http.expectOne('/api/claims/5/pay').request.method).toBe('POST');
  });

  it('uploads documents as multipart with the document type as a parameter', () => {
    const file = new File(['%PDF-1.4'], 'tutanak.pdf', { type: 'application/pdf' });
    claims.uploadDocument(5, 'ACCIDENT_REPORT', file).subscribe();

    const req = http.expectOne((r) => r.url === '/api/claims/5/documents');
    expect(req.request.params.get('documentType')).toBe('ACCIDENT_REPORT');
    expect(((req.request.body as FormData).get('file') as File).name).toBe('tutanak.pdf');
    req.flush({});
  });

  it('lists unpaid installments and records payments', () => {
    payments
      .installments(
        { query: '', status: null, unpaidOnly: true, policyId: 9 },
        { page: 1, size: 10 },
      )
      .subscribe();
    const list = http.expectOne((r) => r.url === '/api/installments');
    expect(list.request.params.get('unpaidOnly')).toBe('true');
    expect(list.request.params.get('policyId')).toBe('9');
    expect(list.request.params.has('status')).toBe(false);

    payments.pay(42, 'CASH').subscribe();
    expect(http.expectOne('/api/installments/42/payments').request.body).toEqual({
      paymentMethod: 'CASH',
    });
  });
});
