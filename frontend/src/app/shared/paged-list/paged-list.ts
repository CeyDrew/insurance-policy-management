import { Signal, computed, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { PageEvent } from '@angular/material/paginator';
import { Observable, catchError, finalize, of, switchMap, tap } from 'rxjs';
import { Page, PageRequest } from '../../core/http/page';
import { errorMessage } from '../../core/http/problem-detail';

export interface PagedList<T> {
  readonly page: Signal<Page<T> | null>;
  readonly rows: Signal<T[]>;
  readonly loading: Signal<boolean>;
  readonly error: Signal<string | null>;
  onPage(event: PageEvent): void;
  /** Reloads the current page (e.g. after a row was changed), keeping filter and page. */
  reload(): void;
}

/**
 * Loads a paged list whenever the filter or the page changes. A new filter resets to the first page in the
 * same tick, so only one request is sent. Must be called in an injection context (e.g. a field initializer).
 */
export function pagedList<F, T>(
  filter: Signal<F>,
  load: (filter: F, page: PageRequest) => Observable<Page<T>>,
  pageSize = 20,
): PagedList<T> {
  const paging = linkedSignal<F, PageRequest>({
    source: filter,
    computation: (_, previous) => ({ page: 0, size: previous?.value.size ?? pageSize }),
  });
  const loading = signal(true);
  const error = signal<string | null>(null);

  const version = signal(0);
  const request = computed(() => ({ filter: filter(), paging: paging(), version: version() }));
  const page = toSignal(
    toObservable(request).pipe(
      tap(() => {
        loading.set(true);
        error.set(null);
      }),
      switchMap(({ filter, paging }) =>
        load(filter, paging).pipe(
          catchError((err: unknown) => {
            error.set(errorMessage(err));
            return of(null);
          }),
          finalize(() => loading.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  return {
    page,
    rows: computed(() => page()?.content ?? []),
    loading,
    error,
    onPage: (event) => paging.set({ page: event.pageIndex, size: event.pageSize }),
    reload: () => version.update((v) => v + 1),
  };
}
