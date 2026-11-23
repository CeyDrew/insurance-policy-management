import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
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
import { POLICY_STATUS_LABELS, PRODUCT_LABELS, options } from '../../../shared/labels';
import { pagedList } from '../../../shared/paged-list/paged-list';
import { ProductCode } from '../../../shared/reference/reference.models';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { PolicySearch, PolicyStatus } from '../policy.models';
import { PolicyService } from '../policy.service';

@Component({
  selector: 'app-policy-list',
  imports: [
    CurrencyPipe,
    DatePipe,
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
    StatusBadge,
  ],
  templateUrl: './policy-list.html',
  styleUrl: '../../../shared/styles/list-page.scss',
})
export class PolicyList {
  private readonly policies = inject(PolicyService);

  protected readonly canQuote = inject(AuthService).hasRole('ADMIN', 'AGENT');
  protected readonly statusOptions = options(POLICY_STATUS_LABELS);
  protected readonly productOptions = options(PRODUCT_LABELS);
  protected readonly columns = [
    'policyNumber',
    'productName',
    'customerName',
    'insuredObject',
    'period',
    'grossPremium',
    'status',
  ];

  protected readonly filter = inject(NonNullableFormBuilder).group({
    query: [''],
    status: [null as PolicyStatus | null],
    productCode: [null as ProductCode | null],
  });

  private readonly search = toSignal(
    this.filter.valueChanges.pipe(
      debounceTime(300),
      map(() => this.toSearch()),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    ),
    { initialValue: this.toSearch() },
  );

  protected readonly list = pagedList(this.search, (filter, page) =>
    this.policies.search(filter, page),
  );

  private toSearch(): PolicySearch {
    return { ...this.filter.getRawValue(), customerId: null };
  }
}
