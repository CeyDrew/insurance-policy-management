import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CUSTOMER_TYPE_LABELS, options } from '../../../shared/labels';
import { pagedList } from '../../../shared/paged-list/paged-list';
import { ReferenceService } from '../../../shared/reference/reference.service';
import { CustomerSearch, CustomerType } from '../customer.models';
import { CustomerService } from '../customer.service';

@Component({
  selector: 'app-customer-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './customer-list.html',
  styleUrl: '../../../shared/styles/list-page.scss',
})
export class CustomerList {
  private readonly customers = inject(CustomerService);

  protected readonly canEdit = inject(AuthService).hasRole('ADMIN', 'AGENT');
  protected readonly cities = toSignal(inject(ReferenceService).cities$, { initialValue: [] });
  protected readonly typeOptions = options(CUSTOMER_TYPE_LABELS);
  protected readonly columns = [
    'displayName',
    'customerType',
    'identityNumber',
    'phone',
    'cityName',
  ];

  protected readonly filter = inject(NonNullableFormBuilder).group({
    query: [''],
    type: [null as CustomerType | null],
    cityId: [null as number | null],
  });

  private readonly search = toSignal(
    this.filter.valueChanges.pipe(
      debounceTime(300),
      map(() => this.filter.getRawValue() as CustomerSearch),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    ),
    { initialValue: this.filter.getRawValue() as CustomerSearch },
  );

  protected readonly list = pagedList(this.search, (filter, page) =>
    this.customers.search(filter, page),
  );

  protected typeLabel(type: CustomerType): string {
    return CUSTOMER_TYPE_LABELS[type];
  }
}
