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
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CLAIM_STATUS_LABELS, options } from '../../../shared/labels';
import { pagedList } from '../../../shared/paged-list/paged-list';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { ClaimSearch, ClaimStatus } from '../claim.models';
import { ClaimService } from '../claim.service';

@Component({
  selector: 'app-claim-list',
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
    MatSlideToggleModule,
    MatTableModule,
    StatusBadge,
  ],
  templateUrl: './claim-list.html',
  styleUrl: '../../../shared/styles/list-page.scss',
})
export class ClaimList {
  private readonly claims = inject(ClaimService);
  private readonly auth = inject(AuthService);

  protected readonly canReport = this.auth.hasRole('ADMIN', 'AGENT');
  protected readonly isHandler = this.auth.hasRole('CLAIM_HANDLER');
  protected readonly statusOptions = options(CLAIM_STATUS_LABELS);
  protected readonly columns = [
    'claimNumber',
    'status',
    'policyNumber',
    'customerName',
    'incidentDate',
    'estimatedAmount',
    'handlerName',
  ];

  protected readonly filter = inject(NonNullableFormBuilder).group({
    query: [''],
    status: [null as ClaimStatus | null],
    // Claim handlers start with their own files.
    mine: [this.isHandler],
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
    this.claims.search(filter, page),
  );

  private toSearch(): ClaimSearch {
    return { ...this.filter.getRawValue(), policyId: null };
  }
}
