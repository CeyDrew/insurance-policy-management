import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

const TR_DATE = /^\s*(\d{1,2})[./-](\d{1,2})[./-](\d{4})\s*$/;

/**
 * NativeDateAdapter displays dates in the tr-TR locale (dd.MM.yyyy) but parses typed input with
 * Date.parse, which does not understand that format. This adapter parses day.month.year explicitly.
 */
@Injectable()
export class TrDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      const match = TR_DATE.exec(value);
      if (match) {
        const [, day, month, year] = match.map(Number);
        const date = new Date(year, month - 1, day);
        // Reject overflow such as 31.02.2024 instead of silently rolling over to March.
        return date.getFullYear() === year &&
          date.getMonth() === month - 1 &&
          date.getDate() === day
          ? date
          : this.invalid();
      }
    }
    return super.parse(value, parseFormat);
  }
}
