import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { errorMessage } from '../../../core/http/problem-detail';
import { PAYMENT_METHOD_LABELS, options } from '../../../shared/labels';
import { PaymentMethod, PaymentResult, PaymentTarget } from '../payment.models';
import { PaymentService } from '../payment.service';

/** Records a full payment of one installment; closes with the result. Collection is simulated. */
@Component({
  selector: 'app-payment-dialog',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Tahsilat</h2>
    <form [formGroup]="form" (ngSubmit)="pay()" novalidate>
      <mat-dialog-content>
        <dl class="summary">
          <div>
            <dt>Poliçe</dt>
            <dd class="mono">{{ target.policyNumber }}</dd>
          </div>
          @if (target.customerName) {
            <div>
              <dt>Müşteri</dt>
              <dd>{{ target.customerName }}</dd>
            </div>
          }
          <div>
            <dt>Taksit</dt>
            <dd>
              {{ target.installmentNo }}. taksit · vade {{ target.dueDate | date: 'dd.MM.yyyy' }}
            </dd>
          </div>
          <div class="total">
            <dt>Tutar</dt>
            <dd>{{ target.amount | currency }}</dd>
          </div>
        </dl>
        <mat-form-field class="full">
          <mat-label>Ödeme yöntemi</mat-label>
          <mat-select formControlName="paymentMethod">
            @for (option of methods; track option.value) {
              <mat-option [value]="option.value">{{ option.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <p class="hint">
          <mat-icon>info</mat-icon> Tahsilat simülasyondur; gerçek bir ödeme alınmaz.
        </p>
        @if (error(); as message) {
          <p class="dialog-error" role="alert">{{ message }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Vazgeç</button>
        <button mat-flat-button type="submit" [disabled]="saving()">
          {{ saving() ? 'İşleniyor…' : 'Tahsil et' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .summary {
      margin: 0 0 16px;
      min-width: min(380px, 80vw);
      div {
        display: flex;
        justify-content: space-between;
        gap: 16px;
        padding: 4px 0;
      }
      dt {
        color: var(--mat-sys-on-surface-variant);
      }
      dd {
        margin: 0;
      }
      .total {
        border-top: 1px solid var(--mat-sys-outline-variant);
        margin-top: 4px;
        padding-top: 8px;
        font: var(--mat-sys-title-medium);
      }
    }
    .mono {
      font-family: 'Roboto Mono', ui-monospace, monospace;
    }
    .full {
      width: 100%;
    }
    .hint {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
    }
    .dialog-error {
      margin: 12px 0 0;
      padding: 12px;
      border-radius: 8px;
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
  `,
})
export class PaymentDialog {
  protected readonly target = inject<PaymentTarget>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<PaymentDialog, PaymentResult>>(MatDialogRef);
  private readonly payments = inject(PaymentService);

  // auto-debit payments only arrive in bank result files
  protected readonly methods = options(PAYMENT_METHOD_LABELS).filter(
    (m) => m.value !== 'DIRECT_DEBIT',
  );
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    paymentMethod: ['CREDIT_CARD' as PaymentMethod, Validators.required],
  });

  protected pay(): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.payments.pay(this.target.installmentId, this.form.getRawValue().paymentMethod).subscribe({
      next: (result) => this.dialogRef.close(result),
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.saving.set(false);
      },
    });
  }
}
