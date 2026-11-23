import { Component, computed, effect, inject, input, linkedSignal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { map } from 'rxjs';
import { errorMessage } from '../../../core/http/problem-detail';
import { DurationPipe } from '../../../shared/format/duration.pipe';
import { BatchStatusBadge } from '../batch-status/batch-status';
import { ExecutionSummary, isRunning } from '../batch.models';
import { BatchService } from '../batch.service';

const POLL_MS = 2000;

@Component({
  selector: 'app-job-history',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    BatchStatusBadge,
    DurationPipe,
  ],
  templateUrl: './job-history.html',
  styleUrl: '../../../shared/styles/list-page.scss',
})
export class JobHistory {
  /** Route parameter. */
  readonly name = input.required<string>();

  private readonly batch = inject(BatchService);

  protected readonly columns = [
    'id',
    'status',
    'parameters',
    'startTime',
    'duration',
    'read',
    'write',
    'skip',
  ];

  /** Resets to the first page when another job is opened. */
  private readonly paging = linkedSignal({
    source: this.name,
    computation: () => ({ page: 0, size: 20 }),
  });

  protected readonly job = rxResource({
    params: () => this.name(),
    stream: ({ params }) =>
      this.batch.jobs().pipe(map((jobs) => jobs.find((job) => job.name === params) ?? null)),
  });

  protected readonly history = rxResource({
    params: () => ({ name: this.name(), ...this.paging() }),
    stream: ({ params }) => this.batch.executions(params.name, params.page, params.size),
  });

  protected readonly loadError = computed(() => {
    const error = this.history.error();
    return error ? errorMessage(error) : null;
  });

  constructor() {
    effect((onCleanup) => {
      const running = this.history
        .value()
        ?.content.some((execution) => isRunning(execution.status));
      if (!running) {
        return;
      }
      const timer = setTimeout(() => this.history.reload(), POLL_MS);
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected onPage(event: PageEvent): void {
    this.paging.set({ page: event.pageIndex, size: event.pageSize });
  }

  /** Business parameters worth showing in the list. */
  protected describeParameters(execution: ExecutionSummary): string {
    const p = execution.parameters;
    return p['runDate'] ?? p['month'] ?? p['originalFileName'] ?? '—';
  }
}
