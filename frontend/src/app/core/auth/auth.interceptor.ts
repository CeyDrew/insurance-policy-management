import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const PUBLIC_AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];

/**
 * Adds the bearer token to API calls. On 401 it refreshes the token once and retries the request;
 * if the refresh fails the session is cleared and the user is sent to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || PUBLIC_AUTH_ENDPOINTS.some((url) => req.url.startsWith(url))) {
    return next(req);
  }

  const auth = inject(AuthService);
  const router = inject(Router);

  return next(withToken(req, auth.accessToken)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !auth.canRefresh) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError((refreshError: unknown) => {
          auth.clearSession();
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
          return throwError(() => refreshError);
        }),
        switchMap((token) => next(withToken(req, token))),
      );
    }),
  );
};

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}
