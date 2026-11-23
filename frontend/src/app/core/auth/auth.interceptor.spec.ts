import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { authResponse } from './auth.testing';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);

    auth.login({ username: 'agent', password: 'secret' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse('AGENT', '1'));
  });

  afterEach(() => http.verify());

  it('adds the bearer token to API requests', () => {
    client.get('/api/users').subscribe();

    const req = http.expectOne('/api/users');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush([]);
  });

  it('does not add the token to non-API requests', () => {
    client.get('/assets/config.json').subscribe();

    const req = http.expectOne('/assets/config.json');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('refreshes the token on 401 and retries the request once', () => {
    let result: unknown;
    client.get('/api/users').subscribe((body) => (result = body));

    http.expectOne('/api/users').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(authResponse('AGENT', '2'));

    const retry = http.expectOne('/api/users');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush(['ok']);

    expect(result).toEqual(['ok']);
  });

  it('clears the session and goes to login when the refresh fails', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    let failed = false;
    client.get('/api/users').subscribe({ error: () => (failed = true) });

    http.expectOne('/api/users').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('passes 403 errors through without refreshing', () => {
    let status = 0;
    client.get('/api/users').subscribe({ error: (err) => (status = err.status) });

    http.expectOne('/api/users').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
    http.expectNone('/api/auth/refresh');
  });
});
