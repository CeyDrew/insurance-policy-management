import { Component, computed, input } from '@angular/core';
import { CLAIM_STATUS_LABELS, INSTALLMENT_STATUS_LABELS, POLICY_STATUS_LABELS } from '../labels';

type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

const TONES: Record<string, Tone> = {
  QUOTE: 'info',
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  CANCELLED: 'danger',
  EXPIRED: 'neutral',
  RENEWED: 'neutral',
  PENDING: 'info',
  PAID: 'success',
  OVERDUE: 'danger',
  REPORTED: 'info',
  IN_REVIEW: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
};

const LABELS: Record<string, string> = {
  ...INSTALLMENT_STATUS_LABELS,
  ...POLICY_STATUS_LABELS,
  ...CLAIM_STATUS_LABELS,
};

/** Coloured pill for policy, installment and claim statuses. */
@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="'badge tone-' + tone()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
    }
    .tone-neutral {
      background: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface-variant);
    }
    .tone-info {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
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
  `,
})
export class StatusBadge {
  readonly status = input.required<string>();
  /** Installment CANCELLED is shown neutral, policy CANCELLED red. */
  readonly kind = input<'policy' | 'installment'>('policy');

  protected readonly label = computed(() => LABELS[this.status()] ?? this.status());
  protected readonly tone = computed<Tone>(() =>
    this.kind() === 'installment' && this.status() === 'CANCELLED'
      ? 'neutral'
      : (TONES[this.status()] ?? 'neutral'),
  );
}
