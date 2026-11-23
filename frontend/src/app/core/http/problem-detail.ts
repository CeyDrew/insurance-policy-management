import { HttpErrorResponse } from '@angular/common/http';

/** RFC 7807 error body returned by the backend for every failed request. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: Record<string, string>;
}

const FALLBACK_MESSAGE = 'Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.';

export function isProblemDetail(body: unknown): body is ProblemDetail {
  return typeof body === 'object' && body !== null && 'status' in body;
}

/** User-facing message for an HTTP error. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Sunucuya ulaşılamıyor. Bağlantınızı kontrol edin.';
    }
    if (isProblemDetail(error.error) && error.error.detail) {
      return error.error.detail;
    }
  }
  return FALLBACK_MESSAGE;
}
