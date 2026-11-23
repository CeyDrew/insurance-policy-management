import { Component, computed, effect, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe, KeyValuePipe, formatDate } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { of } from 'rxjs';
import { errorMessage } from '../../../core/http/problem-detail';
import { DurationPipe } from '../../../shared/format/duration.pipe';
import { BatchStatusBadge } from '../batch-status/batch-status';
import { CLAIM_IMPORT_JOB, isRunning } from '../batch.models';
import { BatchService } from '../batch.service';

const POLL_MS = 1500;

/** Parameter names shown to users; technical ones (inputFile path) are hidden. */
const PARAMETER_LABELS: Record<string, string> = {
  runDate: 'İşlem tarihi',
  month: 'Rapor ayı',
  originalFileName: 'Dosya',
  launchedBy: 'Başlatan',
  launchedAt: 'Başlatılma',
};

@Component({
  selector: 'app-execution-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    KeyValuePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    BatchStatusBadge,
    DurationPipe,
  ],
  templateUrl: './execution-detail.html',
  styleUrl: './execution-detail.scss',
})
export class ExecutionDetail {
  readonly id = input.required({ transform: numberAttribute });

  private readonly batch = inject(BatchService);

  protected readonly parameterLabels = PARAMETER_LABELS;
  protected readonly stepColumns = [
    'name',
    'status',
    'duration',
    'read',
    'filter',
    'write',
    'skip',
    'commit',
    'rollback',
  ];
  protected readonly errorColumns = ['lineNumber', 'message', 'rawLine'];

  protected readonly detail = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.batch.execution(params),
  });

  protected readonly running = computed(() => isRunning(this.detail.value()?.execution.status));
  protected readonly isImport = computed(
    () => this.detail.value()?.execution.jobName === CLAIM_IMPORT_JOB,
  );
  protected readonly loadError = computed(() => {
    const error = this.detail.error();
    return error ? errorMessage(error) : null;
  });

  /** Rejected CSV lines; loaded once the import has finished. */
  protected readonly importErrors = rxResource({
    params: () => {
      const value = this.detail.value();
      return value && !this.running() && value.importErrorCount > 0
        ? value.execution.id
        : undefined;
    },
    stream: ({ params }) => (params === undefined ? of([]) : this.batch.importErrors(params)),
  });

  protected readonly visibleParameters = computed(() => {
    const parameters = this.detail.value()?.execution.parameters ?? {};
    return Object.fromEntries(
      Object.entries(parameters).filter(([name]) => name in PARAMETER_LABELS),
    );
  });

  /** Dates in Turkish format; backend LocalDateTime values carry nanoseconds, which Date cannot parse. */
  protected formatParameter(name: string, value: string): string {
    if (name === 'launchedAt') {
      return formatDate(value.slice(0, 19), 'dd.MM.yyyy HH:mm:ss', 'tr');
    }
    if (name === 'runDate') {
      return formatDate(value, 'dd.MM.yyyy', 'tr');
    }
    return value;
  }

  constructor() {
    // Poll until the execution has finished.
    effect((onCleanup) => {
      if (!this.running()) {
        return;
      }
      const timer = setTimeout(() => this.detail.reload(), POLL_MS);
      onCleanup(() => clearTimeout(timer));
    });
  }
}
