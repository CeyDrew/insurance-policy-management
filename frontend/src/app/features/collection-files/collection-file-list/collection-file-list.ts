import { Component, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';
import { errorMessage } from '../../../core/http/problem-detail';
import { saveBlob } from '../../../shared/files/save-blob';
import { pagedList } from '../../../shared/paged-list/paged-list';
import { ExecutionSummary } from '../../batch/batch.models';
import { CollectionFile, DIRECTION_LABELS, Direction, isBusy } from '../collection-file.models';
import { CollectionFileService } from '../collection-file.service';
import { FileStatus } from '../file-status/file-status';

const POLL_MS = 2000;
/** Same limit as the backend upload (CollectionFileService.MAX_UPLOAD_BYTES). */
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

@Component({
  selector: 'app-collection-file-list',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    FileStatus,
  ],
  templateUrl: './collection-file-list.html',
  styleUrl: './collection-file-list.scss',
})
export class CollectionFileList {
  private readonly service = inject(CollectionFileService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly columns = ['file', 'status', 'records', 'collected', 'rejected', 'actions'];
  // table rows are untyped in the template
  protected readonly directionLabels: Record<string, string> = DIRECTION_LABELS;
  protected readonly direction = signal<Direction | null>(null);
  protected readonly list = pagedList(this.direction, (direction, page) =>
    this.service.list(direction, page),
  );
  /** Id of the file whose action is in progress, to disable its buttons. */
  protected readonly working = signal<number | 'request' | 'upload' | null>(null);
  protected readonly busy = computed(() => this.list.rows().some(isBusy));

  constructor() {
    // While a file is being generated or applied, follow its progress.
    effect((onCleanup) => {
      if (!this.busy()) {
        return;
      }
      const timer = setTimeout(() => this.list.reload(), POLL_MS);
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected createRequest(): void {
    this.run('request', this.service.createRequest(null), 'Talep dosyası üretiliyor.');
  }

  protected onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      this.snackBar.open(
        'Dosya en fazla 10 MB olabilir; büyük dosyaları inbox klasörüne bırakın.',
        'Kapat',
        { duration: 6000 },
      );
      return;
    }
    this.run('upload', this.service.uploadResult(file), 'Sonuç dosyası işleniyor.');
  }

  protected simulate(file: CollectionFile): void {
    this.run(
      file.id,
      this.service.simulateBankAnswer(file.id),
      'Banka simülatörü cevap dosyasını üretti, işleniyor.',
    );
  }

  protected restart(file: CollectionFile): void {
    this.run(file.id, this.service.restart(file.id), 'İşlem kaldığı yerden devam ediyor.');
  }

  protected download(file: CollectionFile): void {
    this.service.download(file.id).subscribe({
      next: (blob) => saveBlob(blob, file.fileName),
      error: (err: unknown) => this.snackBar.open(errorMessage(err), 'Kapat', { duration: 5000 }),
    });
  }

  protected canSimulate(file: CollectionFile): boolean {
    return file.direction === 'OUTBOUND' && file.status === 'GENERATED' && !file.answered;
  }

  private run(
    key: number | 'request' | 'upload',
    call: Observable<ExecutionSummary>,
    done: string,
  ) {
    this.working.set(key);
    call.subscribe({
      next: () => {
        this.working.set(null);
        this.snackBar.open(done, 'Kapat', { duration: 3000 });
        // a new file appears once the job has registered it
        setTimeout(() => this.list.reload(), 500);
      },
      error: (err: unknown) => {
        this.working.set(null);
        this.snackBar.open(errorMessage(err), 'Kapat', { duration: 6000 });
      },
    });
  }
}
