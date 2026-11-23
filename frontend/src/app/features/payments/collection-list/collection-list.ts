import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs';
import { pagedList } from '../../../shared/paged-list/paged-list';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { InstallmentStatus } from '../../policies/policy.models';
import { PaymentDialog } from '../payment-dialog/payment-dialog';
import { InstallmentRow, InstallmentSearch, PaymentResult, PaymentTarget } from '../payment.models';
import { PaymentService } from '../payment.service';

type StatusFilter = 'UNPAID' | InstallmentStatus | 'ALL';

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'UNPAID', label: 'Ödenmemiş' },
  { value: 'OVERDUE', label: 'Gecikmede' },
  { value: 'PENDING', label: 'Bekliyor' },
  { value: 'PAID', label: 'Ödendi' },
  { value: 'ALL', label: 'Tümü' },
];

@Component({
  selector: 'app-collection-list',
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
  templateUrl: './collection-list.html',
  styleUrl: '../../../shared/styles/list-page.scss',
})
export class CollectionList {
  private readonly payments = inject(PaymentService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly statusFilters = STATUS_FILTERS;
  protected readonly columns = [
    'policyNumber',
    'customerName',
    'installmentNo',
    'dueDate',
    'amount',
    'status',
    'actions',
  ];
  protected readonly filter = inject(NonNullableFormBuilder).group({
    query: [''],
    status: ['UNPAID' as StatusFilter],
  });

  private readonly search = toSignal(
    this.filter.valueChanges.pipe(
      debounceTime(300),
      map(() => this.toSearch()),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    ),
    { initialValue: this.toSearch() },
  );

  protected readonly list = pagedList(this.search, (search, page) =>
    this.payments.installments(search, page),
  );

  protected isPayable(row: InstallmentRow): boolean {
    return row.status === 'PENDING' || row.status === 'OVERDUE';
  }

  protected collect(row: InstallmentRow): void {
    const target: PaymentTarget = {
      installmentId: row.id,
      installmentNo: row.installmentNo,
      policyNumber: row.policyNumber,
      customerName: row.customerName,
      dueDate: row.dueDate,
      amount: row.amount,
    };
    this.dialog
      .open(PaymentDialog, { data: target })
      .afterClosed()
      .pipe(filter((result): result is PaymentResult => !!result))
      .subscribe((result) => {
        this.list.reload();
        this.snackBar.open(
          result.policyReactivated
            ? `Tahsil edildi (${result.referenceNo}). Poliçe yeniden yürürlüğe girdi.`
            : `Tahsil edildi (${result.referenceNo}).`,
          undefined,
          { duration: 5000 },
        );
      });
  }

  private toSearch(): InstallmentSearch {
    const { query, status } = this.filter.getRawValue();
    return {
      query,
      status: status === 'UNPAID' || status === 'ALL' ? null : status,
      unpaidOnly: status === 'UNPAID',
      policyId: null,
    };
  }
}
