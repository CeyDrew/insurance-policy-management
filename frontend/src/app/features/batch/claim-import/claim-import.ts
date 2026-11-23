import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpEventType } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { errorMessage } from '../../../core/http/problem-detail';
import { formatBytes } from '../../../shared/format/duration.pipe';
import { BatchService } from '../batch.service';

/** Keep in sync with the backend upload limit (BatchJobService.MAX_UPLOAD_BYTES). */
const MAX_BYTES = 5 * 1024 * 1024;

const SAMPLE = `policy_number;incident_date;description;estimated_amount;incident_city_plate
TRF-2026-000001;05.10.2026;Kavşakta arkadan çarpma;38.500,00;34
KSK-2026-000003;2026-11-01;"Otoparkta çizilme";12500.75;`;

@Component({
  selector: 'app-claim-import',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule],
  templateUrl: './claim-import.html',
  styleUrl: './claim-import.scss',
})
export class ClaimImport {
  private readonly batch = inject(BatchService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly sample = SAMPLE;
  protected readonly formatBytes = formatBytes;
  protected readonly file = signal<File | null>(null);
  protected readonly dragging = signal(false);
  /** Upload progress 0..100, null when idle. */
  protected readonly progress = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);

  protected onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.select(input.files?.item(0) ?? null);
    input.value = ''; // allow choosing the same file again
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.select(event.dataTransfer?.files.item(0) ?? null);
  }

  protected upload(): void {
    const file = this.file();
    if (!file || this.progress() !== null) {
      return;
    }
    this.error.set(null);
    this.progress.set(0);
    this.batch
      .importClaims(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((100 * event.loaded) / event.total));
          } else if (event.type === HttpEventType.Response && event.body) {
            void this.router.navigate(['/batch/executions', event.body.id]);
          }
        },
        error: (err: unknown) => {
          this.progress.set(null);
          this.error.set(errorMessage(err));
        },
      });
  }

  private select(file: File | null): void {
    this.error.set(null);
    if (!file) {
      return;
    }
    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.file.set(null);
      this.error.set('Sadece .csv dosyası yüklenebilir.');
      return;
    }
    if (file.size > MAX_BYTES) {
      this.file.set(null);
      this.error.set('Dosya en fazla 5 MB olabilir.');
      return;
    }
    this.file.set(file);
  }
}
