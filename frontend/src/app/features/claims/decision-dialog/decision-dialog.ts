import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Claim } from '../claim.models';

export type Decision = 'approve' | 'reject' | 'pay';

export interface DecisionDialogData {
  decision: Decision;
  claim: Claim;
}

export interface DecisionResult {
  approvedAmount?: number;
  note?: string;
}

const TITLES: Record<Decision, string> = {
  approve: 'Hasarı onayla',
  reject: 'Hasarı reddet',
  pay: 'Hasar ödemesi',
};

/** Collects the input of a claim decision (amount and/or note) and closes with it. */
@Component({
  selector: 'app-decision-dialog',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ title }}</h2>
    <form [formGroup]="form" (ngSubmit)="confirm()" novalidate>
      <mat-dialog-content class="content">
        <p class="muted">
          <span class="mono">{{ data.claim.claimNumber }}</span> · {{ data.claim.customerName }}
        </p>
        @switch (data.decision) {
          @case ('approve') {
            <p>
              Tahmini tutar: <strong>{{ data.claim.estimatedAmount | currency }}</strong>
            </p>
            <mat-form-field class="full">
              <mat-label>Onaylanan tutar (₺)</mat-label>
              <input matInput type="number" formControlName="approvedAmount" min="1" />
              <mat-error>Pozitif bir tutar girin.</mat-error>
            </mat-form-field>
            <mat-form-field class="full">
              <mat-label>Not (isteğe bağlı)</mat-label>
              <textarea matInput formControlName="note" rows="3" maxlength="1000"></textarea>
            </mat-form-field>
          }
          @case ('reject') {
            <mat-form-field class="full">
              <mat-label>Red gerekçesi</mat-label>
              <textarea matInput formControlName="note" rows="3" maxlength="1000"></textarea>
              <mat-error>Red gerekçesi zorunludur.</mat-error>
            </mat-form-field>
          }
          @case ('pay') {
            <p>
              <strong>{{ data.claim.approvedAmount | currency }}</strong> sigortalıya ödenecek ve
              dosya kapanacak.
            </p>
            @if (isVehicleProduct) {
              <p class="warn">
                <mat-icon>trending_down</mat-icon>
                Poliçenin hasarsızlık basamağı {{ data.claim.ncdStep }} →
                {{ data.claim.ncdStep > 1 ? data.claim.ncdStep - 1 : 1 }} olacak; açık yenileme
                teklifleri yeniden fiyatlanır.
              </p>
            }
          }
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Vazgeç</button>
        <button mat-flat-button type="submit" [class.danger]="data.decision === 'reject'">
          {{
            data.decision === 'approve'
              ? 'Onayla'
              : data.decision === 'reject'
                ? 'Reddet'
                : 'Ödemeyi yap'
          }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .content {
      min-width: min(420px, 80vw);
    }
    .full {
      width: 100%;
    }
    .muted {
      color: var(--mat-sys-on-surface-variant);
    }
    .mono {
      font-family: 'Roboto Mono', ui-monospace, monospace;
    }
    .warn {
      display: flex;
      gap: 8px;
      padding: 12px;
      border-radius: 8px;
      background: #ffefc2;
      color: #6b4b00;
    }
    .danger {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
})
export class DecisionDialog {
  protected readonly data = inject<DecisionDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<DecisionDialog, DecisionResult>>(MatDialogRef);

  protected readonly title = TITLES[this.data.decision];
  /** Only vehicle products carry a no-claims step. */
  protected readonly isVehicleProduct =
    this.data.claim.productCode === 'TRAFFIC' || this.data.claim.productCode === 'CASCO';

  protected readonly form = inject(NonNullableFormBuilder).group({
    approvedAmount: [
      this.data.claim.estimatedAmount ?? 0,
      this.data.decision === 'approve' ? [Validators.required, Validators.min(1)] : [],
    ],
    note: [
      '',
      this.data.decision === 'reject' ? [Validators.required, Validators.maxLength(1000)] : [],
    ],
  });

  protected confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { approvedAmount, note } = this.form.getRawValue();
    this.dialogRef.close({ approvedAmount, note: note.trim() || undefined });
  }
}
