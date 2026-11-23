import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { City, Product } from './reference.models';

/** Cities and products rarely change, so they are fetched once per session. */
@Injectable({ providedIn: 'root' })
export class ReferenceService {
  private readonly http = inject(HttpClient);

  readonly cities$: Observable<City[]> = this.http.get<City[]>('/api/cities').pipe(shareReplay(1));
  readonly products$: Observable<Product[]> = this.http
    .get<Product[]>('/api/products')
    .pipe(shareReplay(1));
}
