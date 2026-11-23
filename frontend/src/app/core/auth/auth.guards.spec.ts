import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Role } from './auth.models';
import { authGuard, guestGuard, roleGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { authResponse } from './auth.testing';

describe('auth guards', () => {
  const state = { url: '/users' } as RouterStateSnapshot;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  function loginAs(role: Role): void {
    TestBed.inject(AuthService).login({ username: 'x', password: 'y' }).subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush(authResponse(role));
  }

  function routeWithRoles(roles: Role[]): ActivatedRouteSnapshot {
    return { data: { roles } } as unknown as ActivatedRouteSnapshot;
  }

  function serialize(result: unknown): string {
    return TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  it('authGuard redirects anonymous users to login with a return URL', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, state),
    );

    expect(serialize(result)).toBe('/login?returnUrl=%2Fusers');
  });

  it('authGuard lets authenticated users through', () => {
    loginAs('AGENT');

    expect(
      TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, state)),
    ).toBe(true);
  });

  it('guestGuard sends authenticated users away from the login page', () => {
    loginAs('AGENT');

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, state),
    );

    expect(serialize(result)).toBe('/');
  });

  it('roleGuard allows matching roles and redirects others', () => {
    loginAs('AGENT');

    const allowed = TestBed.runInInjectionContext(() =>
      roleGuard(routeWithRoles(['AGENT', 'ADMIN']), state),
    );
    const denied = TestBed.runInInjectionContext(() => roleGuard(routeWithRoles(['ADMIN']), state));

    expect(allowed).toBe(true);
    expect(serialize(denied)).toBe('/');
  });
});
