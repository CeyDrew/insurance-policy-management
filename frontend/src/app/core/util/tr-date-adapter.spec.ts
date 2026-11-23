import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { TrDateAdapter } from './tr-date-adapter';

describe('TrDateAdapter', () => {
  let adapter: DateAdapter<Date>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideNativeDateAdapter(),
        { provide: DateAdapter, useClass: TrDateAdapter },
        { provide: MAT_DATE_LOCALE, useValue: 'tr-TR' },
      ],
    });
    adapter = TestBed.inject(DateAdapter);
  });

  it('parses Turkish day.month.year input', () => {
    const date = adapter.parse('15.06.1988', null)!;

    expect(date.getFullYear()).toBe(1988);
    expect(date.getMonth()).toBe(5);
    expect(date.getDate()).toBe(15);
  });

  it('accepts single-digit day and month and other separators', () => {
    expect(adapter.parse('1/2/2024', null)!.getMonth()).toBe(1);
    expect(adapter.parse('1-2-2024', null)!.getDate()).toBe(1);
  });

  it('rejects impossible dates instead of rolling over', () => {
    expect(adapter.isValid(adapter.parse('31.02.2024', null)!)).toBe(false);
  });

  it('returns null for empty input', () => {
    expect(adapter.parse('', null)).toBeNull();
  });
});
