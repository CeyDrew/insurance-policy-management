import { Provider } from '@angular/core';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';

/**
 * Providers needed only after login (forms, date pickers). Registered on the lazy shell route
 * instead of the root injector to keep them out of the initial bundle.
 */
export const SHELL_PROVIDERS: Provider[] = [
  provideNativeDateAdapter(),
  { provide: MAT_DATE_LOCALE, useValue: 'tr-TR' },
  { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
];
