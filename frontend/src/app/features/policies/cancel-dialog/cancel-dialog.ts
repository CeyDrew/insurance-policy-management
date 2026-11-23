import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

export interface CancelDialogData {
  policyNumber: string;
}

const COMMON_REASONS = [
  'Müşteri talebi',
  'Araç satışı',
  'Konut satışı',
  'Başka şirkete geçiş',
  'Prim ödenmemesi',
];

/** Asks for the cancellation reason; closes with the reason or undefined. */
@Component({
  selector: 'app-cancel-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Poliçeyi iptal et</h2>
    <form [formGroup]="form" (ngSubmit)="confirm()" novalidate>
      <mat-dialog-content>
        <p>
          <strong>{{ data.policyNumber }}</strong> numaralı poliçe bugün itibarıyla iptal edilecek.
          Kullanılmayan gün sayısına göre prim iadesi hesaplanır, ödenmemiş taksitler iptal olur.
        </p>
        <mat-form-field class="full">
          <mat-label>İptal nedeni</mat-label>
          <mat-select formControlName="preset">
            @for (reason of reasons; track reason) {
              <mat-option [value]="reason">{{ reason }}</mat-option>
            }
            <mat-option value="">Diğer</mat-option>
          </mat-select>
        </mat-form-field>
        @if (form.controls.preset.value === '') {
          <mat-form-field class="full">
            <mat-label>Açıklama</mat-label>
            <input matInput formControlName="other" maxlength="300" />
            <mat-error>İptal nedeni zorunludur.</mat-error>
          </mat-form-field>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Vazgeç</button>
        <button mat-flat-button type="submit" class="danger">İptal et</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .full {
      width: 100%;
    }
    .danger {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
})
export class CancelDialog {
  protected readonly data = inject<CancelDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<CancelDialog, string>>(MatDialogRef);

  protected readonly reasons = COMMON_REASONS;
  protected readonly form = inject(NonNullableFormBuilder).group({
    preset: [COMMON_REASONS[0]],
    other: ['', Validators.maxLength(300)],
  });

  protected confirm(): void {
    const { preset, other } = this.form.getRawValue();
    const reason = (preset || other).trim();
    if (!reason) {
      this.form.controls.other.setErrors({ required: true });
      this.form.controls.other.markAsTouched();
      return;
    }
    this.dialogRef.close(reason);
  }
}
