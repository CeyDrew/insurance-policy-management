import { Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, switchMap } from 'rxjs';
import { errorMessage } from '../../../core/http/problem-detail';
import { saveBlob } from '../../../shared/files/save-blob';
import { DurationPipe, formatBytes } from '../../../shared/format/duration.pipe';
import { BatchStatusBadge } from '../batch-status/batch-status';
import { CLAIM_IMPORT_JOB, JobSummary, ReportFile, isRunning } from '../batch.models';
import { BatchService } from '../batch.service';
import { LaunchDialog } from '../launch-dialog/launch-dialog';

const POLL_RUNNING_MS = 2000;
const POLL_IDLE_MS = 15000;

const JOB_ICONS: Record<string, string> = {
  policyRenewalJob: 'autorenew',
  overdueInstallmentJob: 'schedule',
  overdueInstallmentPlsqlJob: 'bolt',
  policyExpirationJob: 'event_busy',
  claimImportJob: 'upload_file',
  monthlyProductionReportJob: 'table_view',
};

@Component({
  selector: 'app-batch-dashboard',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    BatchStatusBadge,
    DurationPipe,
  ],
  templateUrl: './batch-dashboard.html',
  styleUrl: './batch-dashboard.scss',
})
export class BatchDashboard {
  private readonly batch = inject(BatchService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  protected readonly icons = JOB_ICONS;
  protected readonly claimImportJob = CLAIM_IMPORT_JOB;
  protected readonly formatBytes = formatBytes;
  protected readonly launching = signal<string | null>(null);
  protected readonly isRunning = isRunning;

  protected readonly jobs = rxResource({ stream: () => this.batch.jobs() });
  protected readonly reports = rxResource({ stream: () => this.batch.reports() });

  protected readonly anyRunning = computed(
    () => this.jobs.value()?.some((job) => isRunning(job.lastExecution?.status)) ?? false,
  );
  protected readonly loadError = computed(() => {
    const error = this.jobs.error();
    return error ? errorMessage(error) : null;
  });

  constructor() {
    // Poll quickly while a job runs, slowly otherwise. Re-armed whenever new data arrives.
    effect((onCleanup) => {
      this.jobs.value();
      this.jobs.status(); // also re-arm after a failed refresh
      const timer = setTimeout(
        () => {
          this.jobs.reload();
          if (!this.anyRunning()) {
            this.reports.reload();
          }
        },
        this.anyRunning() ? POLL_RUNNING_MS : POLL_IDLE_MS,
      );
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected run(job: JobSummary): void {
    this.dialog
      .open(LaunchDialog, { data: job })
      .afterClosed()
      .pipe(
        filter((parameters): parameters is Record<string, string> => !!parameters),
        switchMap((parameters) => {
          this.launching.set(job.name);
          return this.batch.launch(job.name, parameters);
        }),
      )
      .subscribe({
        next: (execution) => {
          this.launching.set(null);
          this.jobs.reload();
          this.snackBar
            .open(`${job.title} başlatıldı (#${execution.id}).`, 'İzle', { duration: 5000 })
            .onAction()
            .subscribe(() => void this.router.navigate(['/batch/executions', execution.id]));
        },
        error: (err: unknown) => {
          this.launching.set(null);
          this.snackBar.open(errorMessage(err), 'Kapat', { duration: 6000 });
        },
      });
  }

  protected download(report: ReportFile): void {
    this.batch.downloadReport(report.name).subscribe({
      next: (blob) => saveBlob(blob, report.name),
      error: (err: unknown) => this.snackBar.open(errorMessage(err), 'Kapat', { duration: 6000 }),
    });
  }
}
