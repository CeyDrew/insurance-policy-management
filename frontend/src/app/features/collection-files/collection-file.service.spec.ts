import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CollectionFile, isBusy } from './collection-file.models';
import { CollectionFileService } from './collection-file.service';
import { presentFile } from './file-status/file-status';

function file(overrides: Partial<CollectionFile>): CollectionFile {
  return {
    id: 1,
    direction: 'INBOUND',
    requestFileId: 7,
    fileName: 'SN.txt',
    fileDate: '2026-11-23',
    status: 'COMPLETED',
    recordCount: 10,
    totalAmount: 100,
    collectedCount: 9,
    collectedAmount: 90,
    rejectedCount: 1,
    errorCount: 0,
    errorMessage: null,
    jobExecutionId: 5,
    createdAt: '2026-11-23T10:00:00',
    createdBy: 'admin',
    completedAt: null,
    answered: false,
    interrupted: false,
    ...overrides,
  };
}

describe('CollectionFileService', () => {
  let service: CollectionFileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CollectionFileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists files by direction and pages results by outcome', () => {
    service.list('OUTBOUND', { page: 1, size: 20 }).subscribe();
    http.expectOne('/api/collection-files?direction=OUTBOUND&page=1&size=20').flush({});

    service.list(null, { page: 0, size: 20 }).subscribe();
    http.expectOne('/api/collection-files?page=0&size=20').flush({});

    service.results(4, 'REJECTED', { page: 0, size: 50 }).subscribe();
    http.expectOne('/api/collection-files/4/results?outcome=REJECTED&page=0&size=50').flush({});
  });

  it('starts the jobs behind the screen', () => {
    service.createRequest(null).subscribe();
    const request = http.expectOne({ method: 'POST', url: '/api/collection-files/requests' });
    expect(request.request.body).toEqual({ runDate: null });
    request.flush({});

    service.simulateBankAnswer(3).subscribe();
    http.expectOne({ method: 'POST', url: '/api/collection-files/3/bank-answer' }).flush({});

    service.restart(3).subscribe();
    http.expectOne({ method: 'POST', url: '/api/collection-files/3/restart' }).flush({});

    service.uploadResult(new File(['H'], 'sonuc.txt')).subscribe();
    const upload = http.expectOne({ method: 'POST', url: '/api/collection-files/results' });
    expect(upload.request.body instanceof FormData).toBe(true);
    upload.flush({});
  });
});

describe('collection file status', () => {
  it('tells waiting, answered, interrupted and rejected files apart', () => {
    expect(presentFile(file({ direction: 'OUTBOUND', status: 'GENERATED' })).label).toBe(
      'Banka cevabı bekleniyor',
    );
    expect(
      presentFile(file({ direction: 'OUTBOUND', status: 'GENERATED', answered: true })).label,
    ).toBe('Cevaplandı');
    expect(presentFile(file({ status: 'PROCESSING', interrupted: true })).tone).toBe('warning');
    expect(presentFile(file({ status: 'FAILED' })).label).toBe('Reddedildi');
  });

  it('keeps polling only while a job is working on the file', () => {
    expect(isBusy(file({ status: 'GENERATING' }))).toBe(true);
    expect(isBusy(file({ status: 'PROCESSING' }))).toBe(true);
    expect(isBusy(file({ status: 'PROCESSING', interrupted: true }))).toBe(false);
    expect(isBusy(file({ status: 'COMPLETED' }))).toBe(false);
  });
});
