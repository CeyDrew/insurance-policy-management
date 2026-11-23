import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authResponse } from './auth.testing';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', children: [] }]),
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('stores the session after login', () => {
    let username: string | undefined;
    service
      .login({ username: 'agent', password: 'secret' })
      .subscribe((user) => (username = user.username));

    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ username: 'agent', password: 'secret' });
    req.flush(authResponse('AGENT'));

    expect(username).toBe('agent');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken).toBe('access-1');
    expect(service.hasRole('AGENT')).toBe(true);
    expect(service.hasRole('ADMIN')).toBe(false);
    expect(localStorage.getItem('ipm.session')).toContain('refresh-1');
  });

  it('shares one refresh request between concurrent callers', () => {
    service.login({ username: 'agent', password: 'secret' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse('AGENT', '1'));

    const tokens: string[] = [];
    service.refresh().subscribe((token) => tokens.push(token));
    service.refresh().subscribe((token) => tokens.push(token));

    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
    req.flush(authResponse('AGENT', '2'));

    expect(tokens).toEqual(['access-2', 'access-2']);
    expect(service.accessToken).toBe('access-2');
  });

  it('clears the session and revokes the refresh token on logout', () => {
    service.login({ username: 'agent', password: 'secret' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse());

    service.logout();

    const req = http.expectOne('/api/auth/logout');
    expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
    req.flush(null);
    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('ipm.session')).toBeNull();
  });
});
