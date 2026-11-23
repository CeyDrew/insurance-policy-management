import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { toIsoDate, today } from '../../../core/util/dates';
import { JobSummary } from '../batch.models';

/** Last 12 months, newest first, as yyyy-MM with a Turkish label. */
function recentMonths(): { value: string; label: string }[] {
  const now = new Date();
  return Array.from({ length: 12 }, (_, i) => {
    const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
    const label = date.toLocaleDateString('tr-TR', { month: 'long', year: 'numeric' });
    return { value, label };
  });
}

/** Collects the job's business parameters and closes with them (or undefined when cancelled). */
@Component({
  selector: 'app-launch-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ job.title }}</h2>
    <form [formGroup]="form" (ngSubmit)="confirm()" novalidate>
      <mat-dialog-content>
        <p class="description">{{ job.description }}</p>
        @if (job.parameters.includes('runDate')) {
          <mat-form-field class="full">
            <mat-label>İşlem tarihi</mat-label>
            <input matInput [matDatepicker]="picker" formControlName="runDate" />
            <mat-datepicker-toggle matIconSuffix [for]="picker" />
            <mat-datepicker #picker />
            <mat-hint
              >Job bu tarihe göre çalışır (ör. vadesi bu tarihten önce olan taksitler).</mat-hint
            >
            <mat-error>Geçerli bir tarih seçin.</mat-error>
          </mat-form-field>
        }
        @if (job.parameters.includes('month')) {
          <mat-form-field class="full">
            <mat-label>Rapor ayı</mat-label>
            <mat-select formControlName="month">
              @for (month of months; track month.value) {
                <mat-option [value]="month.value">{{ month.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Vazgeç</button>
        <button mat-flat-button type="submit">Çalıştır</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .full {
      width: 100%;
      margin-top: 8px;
    }
    .description {
      max-width: 440px;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class LaunchDialog {
  protected readonly job = inject<JobSummary>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<LaunchDialog, Record<string, string>>>(MatDialogRef);

  protected readonly months = recentMonths();
  protected readonly form = inject(NonNullableFormBuilder).group({
    runDate: [today(), Validators.required],
    // Reports default to the previous, complete month.
    month: [this.months[1].value, Validators.required],
  });

  protected confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { runDate, month } = this.form.getRawValue();
    const parameters: Record<string, string> = {};
    if (this.job.parameters.includes('runDate')) {
      parameters['runDate'] = toIsoDate(runDate);
    }
    if (this.job.parameters.includes('month')) {
      parameters['month'] = month;
    }
    this.dialogRef.close(parameters);
  }
}
