import { HttpParams } from '@angular/common/http';

/** Paged result returned by list endpoints (backend PageResponse). */
export interface Page<T> {
  content: T[];
  /** zero-based */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageRequest {
  page: number;
  size: number;
  sort?: string;
}

/** Builds query params, skipping empty values. */
export function toParams(values: Record<string, string | number | null | undefined>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
