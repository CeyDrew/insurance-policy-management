import { Provider } from '@angular/core';
import { DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
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
];
