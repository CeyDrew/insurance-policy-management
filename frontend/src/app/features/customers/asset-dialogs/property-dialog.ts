import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { errorMessage } from '../../../core/http/problem-detail';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { CONSTRUCTION_TYPE_LABELS, options } from '../../../shared/labels';
import { ReferenceService } from '../../../shared/reference/reference.service';
import { ConstructionType, Property } from '../customer.models';
import { CustomerService } from '../customer.service';
import { AssetDialogData } from './vehicle-dialog';

/** Adds a property (konut) to a customer; closes with the created property. */
@Component({
  selector: 'app-property-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Konut ekle</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="dialog-grid">
        @if (error(); as message) {
          <p class="dialog-error" role="alert">{{ message }}</p>
        }
        <mat-form-field>
          <mat-label>İl</mat-label>
          <mat-select formControlName="cityId">
            @for (city of cities(); track city.id) {
              <mat-option [value]="city.id">{{ city.plateCode }} - {{ city.name }}</mat-option>
            }
          </mat-select>
          <mat-error>İl zorunludur.</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Yapı tipi</mat-label>
          <mat-select formControlName="constructionType">
            @for (option of constructionOptions; track option.value) {
              <mat-option [value]="option.value">{{ option.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field class="wide">
          <mat-label>Adres</mat-label>
          <input matInput formControlName="addressLine" />
          <mat-error>Adres zorunludur.</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Yapım yılı</mat-label>
          <input matInput type="number" formControlName="buildingYear" />
          <mat-error>1800 ile {{ currentYear }} arasında olmalıdır.</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Brüt alan (m²)</mat-label>
          <input matInput type="number" formControlName="grossAreaM2" min="1" />
          <mat-error>1 ile 10.000 arasında olmalıdır.</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Konut değeri (₺)</mat-label>
          <input matInput type="number" formControlName="propertyValue" min="1" />
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
export class PropertyDialog {
  private readonly data = inject<AssetDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<PropertyDialog, Property>>(MatDialogRef);
  private readonly customers = inject(CustomerService);

  protected readonly cities = toSignal(inject(ReferenceService).cities$, { initialValue: [] });
  protected readonly constructionOptions = options(CONSTRUCTION_TYPE_LABELS);
  protected readonly currentYear = new Date().getFullYear();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    cityId: [null as number | null, Validators.required],
    addressLine: ['', [Validators.required, Validators.maxLength(300)]],
    buildingYear: [
      null as number | null,
      [Validators.required, Validators.min(1800), Validators.max(this.currentYear)],
    ],
    grossAreaM2: [
      null as number | null,
      [Validators.required, Validators.min(1), Validators.max(10000)],
    ],
    constructionType: ['REINFORCED_CONCRETE' as ConstructionType, Validators.required],
    propertyValue: [null as number | null, [Validators.required, Validators.min(1)]],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.customers
      .addProperty(this.data.customerId, {
        cityId: v.cityId!,
        addressLine: v.addressLine.trim(),
        buildingYear: v.buildingYear!,
        grossAreaM2: v.grossAreaM2!,
        constructionType: v.constructionType,
        propertyValue: v.propertyValue!,
      })
      .subscribe({
        next: (property) => this.dialogRef.close(property),
        error: (err: unknown) => {
          if (!applyServerErrors(this.form, err)) {
            this.error.set(errorMessage(err));
          }
          this.saving.set(false);
        },
      });
  }
}
