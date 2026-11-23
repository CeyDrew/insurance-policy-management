import { Provider } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { TrDateAdapter } from '../../core/util/tr-date-adapter';

/**
 * Providers needed only after login (forms, date pickers). Registered on the lazy shell route
 * instead of the root injector to keep them out of the initial bundle.
 */
export const SHELL_PROVIDERS: Provider[] = [
  provideNativeDateAdapter(),
  { provide: DateAdapter, useClass: TrDateAdapter },
  { provide: MAT_DATE_LOCALE, useValue: 'tr-TR' },
  { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
  // MatDialog delegates to the CDK Dialog, which is provided in root and would create dialog content from the
  // root injector. That injector cannot see the providers above, so a datepicker inside a dialog failed with
  // "No provider found for DateAdapter". Scoping both services here makes dialogs inherit these providers.
  Dialog,
  MatDialog,
];
