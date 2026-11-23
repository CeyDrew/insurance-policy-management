import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable, filter, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/problem-detail';
import { saveBlob } from '../../../shared/files/save-blob';
import { formatBytes } from '../../../shared/format/duration.pipe';
import { CLAIM_STATUS_LABELS, DOCUMENT_TYPE_LABELS, options } from '../../../shared/labels';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { Claim, ClaimDocument, ClaimStatus, DocumentType } from '../claim.models';
import { ClaimService } from '../claim.service';
import {
  Decision,
  DecisionDialog,
  DecisionDialogData,
  DecisionResult,
} from '../decision-dialog/decision-dialog';

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

@Component({
  selector: 'app-claim-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTooltipModule,
    StatusBadge,
  ],
  templateUrl: './claim-detail.html',
  styleUrl: './claim-detail.scss',
})
export class ClaimDetail {
  readonly id = input.required({ transform: numberAttribute });

  private readonly claims = inject(ClaimService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  protected readonly documentTypes = options(DOCUMENT_TYPE_LABELS);
  protected readonly documentLabels: Record<string, string> = DOCUMENT_TYPE_LABELS;
  protected readonly statusLabels: Record<string, string> = CLAIM_STATUS_LABELS;
  protected readonly formatBytes = formatBytes;
  protected readonly busy = signal(false);
  protected readonly uploading = signal(false);

  protected readonly uploadForm = inject(NonNullableFormBuilder).group({
    documentType: ['ACCIDENT_REPORT' as DocumentType],
  });

  protected readonly claim = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.claims.get(params),
  });

  protected readonly loadError = computed(() => {
    const error = this.claim.error();
    return error ? errorMessage(error) : null;
  });

  private readonly isHandlerRole = this.auth.hasRole('ADMIN', 'CLAIM_HANDLER');
  private readonly isAdmin = this.auth.hasRole('ADMIN');

  /** May decide: the assigned handler or an admin (PKG_CLAIM enforces the same rule). */
  protected readonly canDecide = computed(() => {
    const claim = this.claim.value();
    if (!claim || !this.isHandlerRole) {
      return false;
    }
    return this.isAdmin || claim.handlerUsername === this.auth.currentUser()?.username;
  });
  protected readonly canAssign = computed(() => {
    const status = this.claim.value()?.status;
    return (
      this.isHandlerRole && (status === 'REPORTED' || (status === 'IN_REVIEW' && !this.canDecide()))
    );
  });
  protected readonly isClosed = computed(() => {
    const status = this.claim.value()?.status;
    return status === 'REJECTED' || status === 'PAID';
  });

  protected statusLabel(status: ClaimStatus | null): string {
    return status ? this.statusLabels[status] : '';
  }

  protected assign(claim: Claim): void {
    this.run(this.claims.assignToMe(claim.id), 'Dosya size atandı.');
  }

  protected decide(claim: Claim, decision: Decision): void {
    this.dialog
      .open(DecisionDialog, { data: { claim, decision } satisfies DecisionDialogData })
      .afterClosed()
      .pipe(
        filter((result): result is DecisionResult => !!result),
        switchMap((result) => {
          this.busy.set(true);
          switch (decision) {
            case 'approve':
              return this.claims.approve(claim.id, result.approvedAmount!, result.note ?? null);
            case 'reject':
              return this.claims.reject(claim.id, result.note!);
            default:
              return this.claims.pay(claim.id);
          }
        }),
      )
      .subscribe({
        next: (updated) => this.done(updated, MESSAGES[decision]),
        error: (err: unknown) => this.failed(err),
      });
  }

  protected onFileSelected(claim: Claim, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      this.snackBar.open('Dosya en fazla 10 MB olabilir.', 'Kapat', { duration: 5000 });
      return;
    }
    this.uploading.set(true);
    this.claims
      .uploadDocument(claim.id, this.uploadForm.getRawValue().documentType, file)
      .subscribe({
        next: () => {
          this.uploading.set(false);
          this.claim.reload();
          this.snackBar.open('Evrak yüklendi.', undefined, { duration: 3000 });
        },
        error: (err: unknown) => {
          this.uploading.set(false);
          this.snackBar.open(errorMessage(err), 'Kapat', { duration: 6000 });
        },
      });
  }

  protected download(claim: Claim, document: ClaimDocument): void {
    this.claims.downloadDocument(claim.id, document.id).subscribe({
      next: (blob) => saveBlob(blob, document.fileName),
      error: (err: unknown) => this.failed(err),
    });
  }

  private run(call: Observable<Claim>, message: string): void {
    this.busy.set(true);
    call.subscribe({
      next: (updated) => this.done(updated, message),
      error: (err: unknown) => this.failed(err),
    });
  }

  private done(updated: Claim, message: string): void {
    this.claim.set(updated);
    this.busy.set(false);
    this.snackBar.open(message, undefined, { duration: 3000 });
  }

  private failed(err: unknown): void {
    this.busy.set(false);
    this.snackBar.open(errorMessage(err), 'Kapat', { duration: 6000 });
  }
}

const MESSAGES: Record<Decision, string> = {
  approve: 'Hasar onaylandı.',
  reject: 'Hasar reddedildi.',
  pay: 'Ödeme yapıldı, dosya kapandı.',
};
