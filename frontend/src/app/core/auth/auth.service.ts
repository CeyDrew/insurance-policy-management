import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, map, shareReplay, tap } from 'rxjs';
import { AuthResponse, LoginRequest, Role, Session, User } from './auth.models';

const STORAGE_KEY = 'ipm.session';
const AUTH_API = '/api/auth';

/**
 * Holds the current session (tokens + user) as a signal and persists it to localStorage.
 * Access tokens are short-lived; the interceptor calls {@link refresh} transparently on 401.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(readStoredSession());
  private refreshInFlight$: Observable<string> | null = null;

  readonly currentUser = computed<User | null>(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);

  get accessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.currentUser();
    return user !== null && roles.includes(user.role);
  }

  login(request: LoginRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${AUTH_API}/login`, request).pipe(
      tap((response) => this.store(response)),
      map((response) => response.user),
    );
  }

  /**
   * Exchanges the refresh token for a new token pair and returns the new access token.
   * Concurrent callers share a single request, because the backend rotates (invalidates) refresh tokens.
   */
  refresh(): Observable<string> {
    const refreshToken = this.session()?.refreshToken;
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }
    this.refreshInFlight$ ??= this.http
      .post<AuthResponse>(`${AUTH_API}/refresh`, { refreshToken })
      .pipe(
        tap((response) => this.store(response)),
        map((response) => response.accessToken),
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay(1),
      );
    return this.refreshInFlight$;
  }

  get canRefresh(): boolean {
    return !!this.session()?.refreshToken;
  }

  logout(): void {
    const refreshToken = this.session()?.refreshToken;
    if (refreshToken) {
      // Best effort: the local session is cleared even if the server call fails.
      this.http.post<void>(`${AUTH_API}/logout`, { refreshToken }).subscribe({ error: () => undefined });
    }
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  /** Drops the local session without calling the server (e.g. after a failed refresh). */
  clearSession(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // storage unavailable (private mode); nothing to clean up
    }
  }

  private store(response: AuthResponse): void {
    const session: Session = {
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      user: response.user,
    };
    this.session.set(session);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // storage unavailable: session lives in memory only
    }
  }
}

function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}
