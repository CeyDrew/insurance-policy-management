import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** T.C. kimlik no checksum (same algorithm as the backend). */
export function isValidTckn(value: string): boolean {
  if (!/^[1-9]\d{10}$/.test(value)) {
    return false;
  }
  const d = [...value].map(Number);
  const odd = d[0] + d[2] + d[4] + d[6] + d[8];
  const even = d[1] + d[3] + d[5] + d[7];
  const tenth = (((odd * 7 - even) % 10) + 10) % 10;
  const eleventh = (odd + even + d[9]) % 10;
  return d[9] === tenth && d[10] === eleventh;
}

/** Vergi kimlik no checksum (same algorithm as the backend). */
export function isValidVkn(value: string): boolean {
  if (!/^\d{10}$/.test(value)) {
    return false;
  }
  const d = [...value].map(Number);
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    const tmp = (d[i] + 9 - i) % 10;
    let v = (tmp * 2 ** (9 - i)) % 9;
    if (tmp !== 0 && v === 0) {
      v = 9;
    }
    sum += v;
  }
  return (10 - (sum % 10)) % 10 === d[9];
}

function checksumValidator(check: (value: string) => boolean, errorKey: string): ValidatorFn {
  return (control: AbstractControl<string | null>): ValidationErrors | null => {
    const value = control.value;
    if (!value) {
      return null; // combine with Validators.required where needed
    }
    return check(value) ? null : { [errorKey]: true };
  };
}

export const tcknValidator = checksumValidator(isValidTckn, 'tckn');
export const vknValidator = checksumValidator(isValidVkn, 'vkn');
