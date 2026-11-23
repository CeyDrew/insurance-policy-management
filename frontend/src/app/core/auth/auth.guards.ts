import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from './auth.models';
import { AuthService } from './auth.service';

/** Only authenticated users; others are redirected to the login page with a return URL. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Login page is only for anonymous users. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/']) : true;
};

/** Restricts a route to the roles listed in its `data.roles`. */
export const roleGuard: CanActivateFn = (route) => {
  const roles = (route.data['roles'] ?? []) as Role[];
  const auth = inject(AuthService);
  return roles.length === 0 || auth.hasRole(...roles) ? true : inject(Router).createUrlTree(['/']);
};
