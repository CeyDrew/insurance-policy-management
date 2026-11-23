import { FormControl } from '@angular/forms';
import { isValidTckn, isValidVkn, tcknValidator, vknValidator } from './identity-validators';

describe('identity validators', () => {
  it('accepts valid TCKN values', () => {
    expect(isValidTckn('10000000146')).toBe(true);
    expect(isValidTckn('73098463538')).toBe(true);
  });

  it('rejects invalid TCKN values', () => {
    for (const value of ['10000000147', '01234567890', '1234567890', '1000000014a']) {
      expect(isValidTckn(value)).toBe(false);
    }
  });

  it('accepts valid and rejects invalid VKN values', () => {
    expect(isValidVkn('4519775531')).toBe(true);
    expect(isValidVkn('4519775532')).toBe(false);
    expect(isValidVkn('451977553')).toBe(false);
  });

  it('reports errors on form controls and ignores empty values', () => {
    expect(new FormControl('12345678901', tcknValidator).errors).toEqual({ tckn: true });
    expect(new FormControl('1111111111', vknValidator).errors).toEqual({ vkn: true });
    expect(new FormControl('', tcknValidator).errors).toBeNull();
  });
});
