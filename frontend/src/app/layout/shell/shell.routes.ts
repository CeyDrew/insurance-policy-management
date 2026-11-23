import { Routes } from '@angular/router';
import { roleGuard } from '../../core/auth/auth.guards';
import { Shell } from './shell';
import { SHELL_PROVIDERS } from './shell.providers';

/** Everything behind the login, loaded lazily together with the shell layout. */
export const SHELL_ROUTES: Routes = [
  {
    path: '',
    component: Shell,
    providers: SHELL_PROVIDERS,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Gösterge Paneli',
        loadComponent: () => import('../../features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'customers',
        children: [
          {
            path: '',
            title: 'Müşteriler',
            loadComponent: () =>
              import('../../features/customers/customer-list/customer-list').then(
                (m) => m.CustomerList,
              ),
          },
          {
            path: 'new',
            title: 'Yeni müşteri',
            canActivate: [roleGuard],
            data: { roles: ['ADMIN', 'AGENT'] },
            loadComponent: () =>
              import('../../features/customers/customer-form/customer-form').then(
                (m) => m.CustomerForm,
              ),
          },
          {
            path: ':id',
            title: 'Müşteri',
            loadComponent: () =>
              import('../../features/customers/customer-detail/customer-detail').then(
                (m) => m.CustomerDetail,
              ),
          },
          {
            path: ':id/edit',
            title: 'Müşteriyi düzenle',
            canActivate: [roleGuard],
            data: { roles: ['ADMIN', 'AGENT'] },
            loadComponent: () =>
              import('../../features/customers/customer-form/customer-form').then(
                (m) => m.CustomerForm,
              ),
          },
        ],
      },
      {
        path: 'policies',
        children: [
          {
            path: '',
            title: 'Poliçeler',
            loadComponent: () =>
              import('../../features/policies/policy-list/policy-list').then((m) => m.PolicyList),
          },
          {
            path: ':id',
            title: 'Poliçe',
            loadComponent: () =>
              import('../../features/policies/policy-detail/policy-detail').then(
                (m) => m.PolicyDetail,
              ),
          },
        ],
      },
      {
        path: 'quotes/new',
        title: 'Yeni teklif',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'AGENT'] },
        loadComponent: () =>
          import('../../features/policies/quote-wizard/quote-wizard').then((m) => m.QuoteWizard),
      },
      {
        path: 'users',
        title: 'Kullanıcılar',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('../../features/users/user-list').then((m) => m.UserList),
      },
    ],
  },
];
