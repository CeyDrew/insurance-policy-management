import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { isProblemDetail } from '../../core/http/problem-detail';

/**
 * Copies field errors from a 400 ProblemDetail onto the matching form controls (as `server` errors).
 * Returns true if at least one field error was applied.
 */
export function applyServerErrors(form: FormGroup, error: unknown): boolean {
  if (
    !(error instanceof HttpErrorResponse) ||
    !isProblemDetail(error.error) ||
    !error.error.errors
  ) {
    return false;
  }
  let applied = false;
  for (const [field, message] of Object.entries(error.error.errors)) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ ...control.errors, server: message });
      control.markAsTouched();
      applied = true;
    }
  }
  return applied;
}
