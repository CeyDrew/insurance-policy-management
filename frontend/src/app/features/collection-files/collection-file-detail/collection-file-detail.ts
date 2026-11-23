import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { errorMessage } from '../../../core/http/problem-detail';
import { saveBlob } from '../../../shared/files/save-blob';
import { pagedList } from '../../../shared/paged-list/paged-list';
import { DIRECTION_LABELS, OUTCOME_LABELS, Outcome, isBusy } from '../collection-file.models';
import { CollectionFileService } from '../collection-file.service';
import { FileStatus } from '../file-status/file-status';

const POLL_MS = 2000;

@Component({
  selector: 'app-collection-file-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    PercentPipe,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    FileStatus,
  ],
  templateUrl: './collection-file-detail.html',
  styleUrl: './collection-file-detail.scss',
})
export class CollectionFileDetail {
  private readonly service = inject(CollectionFileService);
  private readonly snackBar = inject(MatSnackBar);

  /** Route parameter. */
  readonly id = input.required<string>();

  protected readonly directionLabels = DIRECTION_LABELS;
  // table rows are untyped in the template
  protected readonly outcomeLabels: Record<string, string> = OUTCOME_LABELS;
  protected readonly columns = ['lineNo', 'policy', 'amount', 'code', 'outcome'];

  protected readonly detail = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.service.detail(params),
  });
  protected readonly file = computed(() => this.detail.value()?.file ?? null);
  protected readonly loadError = computed(() =>
    this.detail.error() ? errorMessage(this.detail.error()) : null,
  );

  protected readonly outcome = signal<Outcome | null>(null);
  private readonly resultFilter = computed(() => ({
    id: Number(this.id()),
    outcome: this.outcome(),
  }));
  protected readonly results = pagedList(
    this.resultFilter,
    (filter, page) => this.service.results(filter.id, filter.outcome, page),
    50,
  );

  /** Share of lines per outcome, for the summary bar. */
  protected readonly shares = computed(() => {
    const f = this.file();
    const total = f?.recordCount ?? 0;
    if (!f || f.direction !== 'INBOUND' || total === 0) {
      return null;
    }
    return {
      collected: f.collectedCount / total,
      rejected: f.rejectedCount / total,
      error: f.errorCount / total,
    };
  });

  protected readonly maxReason = computed(() =>
    Math.max(1, ...(this.detail.value()?.rejectReasons ?? []).map((r) => r.count)),
  );

  protected readonly restarting = signal(false);

  constructor() {
    effect((onCleanup) => {
      const f = this.file();
      if (!f || !isBusy(f)) {
        return;
      }
      const timer = setTimeout(() => {
        this.detail.reload();
        this.results.reload();
      }, POLL_MS);
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected download(): void {
    const f = this.file();
    if (!f) {
      return;
    }
    this.service.download(f.id).subscribe({
      next: (blob) => saveBlob(blob, f.fileName),
      error: (err: unknown) => this.snackBar.open(errorMessage(err), 'Kapat', { duration: 5000 }),
    });
  }

  protected restart(): void {
    const f = this.file();
    if (!f) {
      return;
    }
    this.restarting.set(true);
    this.service.restart(f.id).subscribe({
      next: () => {
        this.restarting.set(false);
        this.snackBar.open('İşlem kaldığı yerden devam ediyor.', 'Kapat', { duration: 3000 });
        setTimeout(() => this.detail.reload(), 500);
      },
      error: (err: unknown) => {
        this.restarting.set(false);
        this.snackBar.open(errorMessage(err), 'Kapat', { duration: 6000 });
      },
    });
  }
}
