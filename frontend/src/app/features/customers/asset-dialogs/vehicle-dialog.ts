import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { errorMessage } from '../../../core/http/problem-detail';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { USAGE_TYPE_LABELS, options } from '../../../shared/labels';
import { UsageType, Vehicle } from '../customer.models';
import { CustomerService } from '../customer.service';

export interface AssetDialogData {
  customerId: number;
}

/** Adds a vehicle to a customer; closes with the created vehicle. */
@Component({
  selector: 'app-vehicle-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Araç ekle</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="dialog-grid">
        @if (error(); as message) {
          <p class="dialog-error" role="alert">{{ message }}</p>
        }
        <mat-form-field>
          <mat-label>Plaka</mat-label>
          <input matInput formControlName="plateNumber" placeholder="34 ABC 123" class="upper" />
          <mat-error>{{
            form.controls.plateNumber.getError('server') ??
              'Geçerli bir plaka girin (örn. 34 ABC 123).'
          }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Şasi no</mat-label>
          <input matInput formControlName="chassisNumber" maxlength="17" class="upper" />
          <mat-error>{{
            form.controls.chassisNumber.getError('server') ??
              '17 karakter olmalıdır (I, O, Q hariç).'
          }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Marka</mat-label>
          <input matInput formControlName="brand" />
          <mat-error>Marka zorunludur.</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Model</mat-label>
          <input matInput formControlName="model" />
          <mat-error>Model zorunludur.</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Model yılı</mat-label>
          <input matInput type="number" formControlName="modelYear" />
          <mat-error>1950 ile {{ maxYear }} arasında olmalıdır.</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Kullanım tipi</mat-label>
          <mat-select formControlName="usageType">
            @for (option of usageOptions; track option.value) {
              <mat-option [value]="option.value">{{ option.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Araç değeri (₺)</mat-label>
          <input matInput type="number" formControlName="vehicleValue" min="1" />
          <mat-error>Pozitif bir tutar girin.</mat-error>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Vazgeç</button>
        <button mat-flat-button type="submit" [disabled]="saving()">Kaydet</button>
      </mat-dialog-actions>
    </form>
  `,
  styleUrl: './asset-dialog.scss',
})
export class VehicleDialog {
  private readonly data = inject<AssetDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<VehicleDialog, Vehicle>>(MatDialogRef);
  private readonly customers = inject(CustomerService);

  protected readonly maxYear = new Date().getFullYear() + 1;
  protected readonly usageOptions = options(USAGE_TYPE_LABELS);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    plateNumber: [
      '',
      [
        Validators.required,
        Validators.pattern(/^(0[1-9]|[1-7]\d|8[01])\s*[A-Za-z]{1,3}\s*\d{2,4}$/),
      ],
    ],
    chassisNumber: [
      '',
      [Validators.required, Validators.pattern(/^[A-HJ-NPR-Za-hj-npr-z0-9]{17}$/)],
    ],
    brand: ['', [Validators.required, Validators.maxLength(50)]],
    model: ['', [Validators.required, Validators.maxLength(50)]],
    modelYear: [
      new Date().getFullYear(),
      [Validators.required, Validators.min(1950), Validators.max(this.maxYear)],
    ],
    usageType: ['PRIVATE' as UsageType, Validators.required],
    vehicleValue: [null as number | null, [Validators.required, Validators.min(1)]],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.customers
      .addVehicle(this.data.customerId, { ...value, vehicleValue: value.vehicleValue! })
      .subscribe({
        next: (vehicle) => this.dialogRef.close(vehicle),
        error: (err: unknown) => {
          if (!applyServerErrors(this.form, err)) {
            this.error.set(errorMessage(err));
          }
          this.saving.set(false);
        },
      });
  }
}
