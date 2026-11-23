import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Observable, filter, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/problem-detail';
import { fromIsoDate, today } from '../../../core/util/dates';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { CancelDialog, CancelDialogData } from '../cancel-dialog/cancel-dialog';
import { Installment, Policy } from '../policy.models';
import { ClaimService } from '../../claims/claim.service';
import { PaymentDialog } from '../../payments/payment-dialog/payment-dialog';
import { PaymentResult, PaymentTarget } from '../../payments/payment.models';
import { PolicyService } from '../policy.service';

@Component({
  selector: 'app-policy-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    StatusBadge,
  ],
  templateUrl: './policy-detail.html',
  styleUrl: './policy-detail.scss',
})
export class PolicyDetail {
  readonly id = input.required({ transform: numberAttribute });

  private readonly policies = inject(PolicyService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly claims = inject(ClaimService);

  protected readonly canManage = inject(AuthService).hasRole('ADMIN', 'AGENT');
  protected readonly busy = signal(false);
  protected readonly installmentColumns = [
    'installmentNo',
    'dueDate',
    'amount',
    'status',
    'paidAt',
    'actions',
  ];
  protected readonly claimColumns = [
    'claimNumber',
    'status',
    'incidentDate',
    'estimatedAmount',
    'paidAmount',
  ];

  /** Claims of this policy (first page is plenty for one policy). */
  protected readonly claimsOfPolicy = rxResource({
    params: () => this.id(),
    stream: ({ params }) =>
      this.claims.search(
        { query: '', status: null, mine: false, policyId: params },
        { page: 0, size: 50 },
      ),
  });

  protected readonly policy = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.policies.get(params),
  });

  protected readonly loadError = computed(() => {
    const error = this.policy.error();
    return error ? errorMessage(error) : null;
  });

  protected readonly quoteExpired = computed(() => {
    const p = this.policy.value();
    const validUntil = fromIsoDate(p?.quoteValidUntil);
    return validUntil !== null && validUntil < today();
  });

  protected issue(policy: Policy): void {
    this.run(this.policies.issue(policy.id), 'Poliçe düzenlendi.');
  }

  protected isPayable(installment: Installment): boolean {
    return installment.status === 'PENDING' || installment.status === 'OVERDUE';
  }

  protected collect(policy: Policy, installment: Installment): void {
    const target: PaymentTarget = {
      installmentId: installment.id,
      installmentNo: installment.installmentNo,
      policyNumber: policy.policyNumber,
      customerName: policy.customerName,
      dueDate: installment.dueDate,
      amount: installment.amount,
    };
    this.dialog
      .open(PaymentDialog, { data: target })
      .afterClosed()
      .pipe(filter((result): result is PaymentResult => !!result))
      .subscribe((result) => {
        this.policy.reload();
        this.snackBar.open(
          result.policyReactivated
            ? 'Tahsil edildi. Poliçe yeniden yürürlüğe girdi.'
            : `Tahsil edildi (${result.referenceNo}).`,
          undefined,
          { duration: 5000 },
        );
      });
  }

  protected cancel(policy: Policy): void {
    this.dialog
      .open(CancelDialog, {
        data: { policyNumber: policy.policyNumber } satisfies CancelDialogData,
      })
      .afterClosed()
      .pipe(
        filter((reason): reason is string => !!reason),
        switchMap((reason) => {
          this.busy.set(true);
          return this.policies.cancel(policy.id, reason);
        }),
      )
      .subscribe({
        next: (updated) => this.done(updated, 'Poliçe iptal edildi.'),
        error: (err: unknown) => this.failed(err),
      });
  }

  private run(call: Observable<Policy>, message: string): void {
    this.busy.set(true);
    call.subscribe({
      next: (updated) => this.done(updated, message),
      error: (err: unknown) => this.failed(err),
    });
  }

  private done(updated: Policy, message: string): void {
    this.policy.set(updated);
    this.busy.set(false);
    this.snackBar.open(message, undefined, { duration: 3000 });
  }

  private failed(err: unknown): void {
    this.busy.set(false);
    this.snackBar.open(errorMessage(err), 'Kapat', { duration: 6000 });
  }
}
