import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { BatchStatus, COMPLETED_WITH_SKIPS, isRunning } from '../batch.models';

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

interface Presentation {
  label: string;
  icon: string;
  tone: Tone;
}

/** Turkish label, icon and colour of a job/step execution, taking the exit code into account. */
export function presentStatus(status: BatchStatus, exitCode?: string | null): Presentation {
  if (status === 'COMPLETED' && exitCode === COMPLETED_WITH_SKIPS) {
    return { label: 'Atlananlarla tamamlandı', icon: 'rule', tone: 'warning' };
  }
  if (isRunning(status)) {
    return {
      label: status === 'STOPPING' ? 'Durduruluyor' : 'Çalışıyor',
      icon: 'sync',
      tone: 'info',
    };
  }
  switch (status) {
    case 'COMPLETED':
      return { label: 'Tamamlandı', icon: 'check_circle', tone: 'success' };
    case 'FAILED':
      return { label: 'Hata', icon: 'error', tone: 'danger' };
    case 'STOPPED':
      return { label: 'Durduruldu', icon: 'stop_circle', tone: 'neutral' };
    case 'ABANDONED':
      return { label: 'Terk edildi', icon: 'block', tone: 'neutral' };
    default:
      return { label: 'Bilinmiyor', icon: 'help', tone: 'neutral' };
  }
}

@Component({
  selector: 'app-batch-status',
  imports: [MatIconModule],
  template: `
    <span class="status" [class]="'status tone-' + presentation().tone">
      <mat-icon [class.spin]="presentation().icon === 'sync'">{{ presentation().icon }}</mat-icon>
      {{ presentation().label }}
    </span>
  `,
  styles: `
    .status {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 10px 2px 6px;
      border-radius: 12px;
      font: var(--mat-sys-label-medium);
      white-space: nowrap;

      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
      }
    }
    .tone-success {
      background: #d7f5dd;
      color: #0d5a1f;
    }
    .tone-warning {
      background: #ffefc2;
      color: #6b4b00;
    }
    .tone-danger {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .tone-info {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .tone-neutral {
      background: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface-variant);
    }
    .spin {
      animation: spin 1.2s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .spin {
        animation: none;
      }
    }
  `,
})
export class BatchStatusBadge {
  readonly status = input.required<BatchStatus>();
  readonly exitCode = input<string | null>();

  protected readonly presentation = computed(() => presentStatus(this.status(), this.exitCode()));
}
