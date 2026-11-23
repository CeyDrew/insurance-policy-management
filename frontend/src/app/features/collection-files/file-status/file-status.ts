import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { CollectionFile } from '../collection-file.models';

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

interface Presentation {
  label: string;
  icon: string;
  tone: Tone;
}

export function presentFile(file: CollectionFile): Presentation {
  switch (file.status) {
    case 'GENERATING':
      return { label: 'Üretiliyor', icon: 'sync', tone: 'info' };
    case 'GENERATED':
      return file.answered
        ? { label: 'Cevaplandı', icon: 'mark_email_read', tone: 'neutral' }
        : { label: 'Banka cevabı bekleniyor', icon: 'schedule_send', tone: 'info' };
    case 'PROCESSING':
      return file.interrupted
        ? { label: 'Yarıda kaldı', icon: 'pause_circle', tone: 'warning' }
        : { label: 'İşleniyor', icon: 'sync', tone: 'info' };
    case 'COMPLETED':
      return { label: 'İşlendi', icon: 'check_circle', tone: 'success' };
    case 'FAILED':
      return { label: 'Reddedildi', icon: 'error', tone: 'danger' };
  }
}

/** Status of a collection file: icon + label, colour only as a second cue. */
@Component({
  selector: 'app-file-status',
  imports: [MatIconModule],
  template: `<span class="status" [class]="'status tone-' + presentation().tone"
    ><mat-icon aria-hidden="true">{{ presentation().icon }}</mat-icon
    >{{ presentation().label }}</span
  >`,
  styles: `
    .status {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 10px 2px 6px;
      border-radius: 12px;
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
    }
    mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
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
export class FileStatus {
  readonly file = input.required<CollectionFile>();
  protected readonly presentation = computed(() => presentFile(this.file()));
}
