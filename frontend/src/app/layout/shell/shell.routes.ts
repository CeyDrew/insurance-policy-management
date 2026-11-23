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
        path: 'claims',
        children: [
          {
            path: '',
            title: 'Hasarlar',
            loadComponent: () =>
              import('../../features/claims/claim-list/claim-list').then((m) => m.ClaimList),
          },
          {
            path: 'new',
            title: 'Hasar ihbarı',
            canActivate: [roleGuard],
            data: { roles: ['ADMIN', 'AGENT'] },
            loadComponent: () =>
              import('../../features/claims/claim-report/claim-report').then((m) => m.ClaimReport),
          },
          {
            path: ':id',
            title: 'Hasar dosyası',
            loadComponent: () =>
              import('../../features/claims/claim-detail/claim-detail').then((m) => m.ClaimDetail),
          },
        ],
      },
      {
        path: 'collections',
        title: 'Tahsilat',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'AGENT'] },
        loadComponent: () =>
          import('../../features/payments/collection-list/collection-list').then(
            (m) => m.CollectionList,
          ),
      },
      {
        path: 'collection-files',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        children: [
          {
            path: '',
            title: 'Tahsilat dosyaları',
            loadComponent: () =>
              import('../../features/collection-files/collection-file-list/collection-file-list').then(
                (m) => m.CollectionFileList,
              ),
          },
          {
            path: ':id',
            title: 'Tahsilat dosyası',
            loadComponent: () =>
              import('../../features/collection-files/collection-file-detail/collection-file-detail').then(
                (m) => m.CollectionFileDetail,
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
        path: 'batch',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        children: [
          {
            path: '',
            title: 'Batch işleri',
            loadComponent: () =>
              import('../../features/batch/batch-dashboard/batch-dashboard').then(
                (m) => m.BatchDashboard,
              ),
          },
          {
            path: 'jobs/:name',
            title: 'Çalışma geçmişi',
            loadComponent: () =>
              import('../../features/batch/job-history/job-history').then((m) => m.JobHistory),
          },
          {
            path: 'executions/:id',
            title: 'Çalıştırma detayı',
            loadComponent: () =>
              import('../../features/batch/execution-detail/execution-detail').then(
                (m) => m.ExecutionDetail,
              ),
          },
          {
            path: 'claim-import',
            title: 'Toplu hasar ihbarı',
            loadComponent: () =>
              import('../../features/batch/claim-import/claim-import').then((m) => m.ClaimImport),
          },
        ],
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
