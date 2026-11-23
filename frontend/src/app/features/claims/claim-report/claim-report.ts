import { Component, OnInit, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import {
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  EMPTY,
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  of,
  switchMap,
} from 'rxjs';
import { errorMessage } from '../../../core/http/problem-detail';
import { toIsoDate, today } from '../../../core/util/dates';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { ReferenceService } from '../../../shared/reference/reference.service';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { Policy, PolicySummary } from '../../policies/policy.models';
import { PolicyService } from '../../policies/policy.service';
import { ClaimService } from '../claim.service';

/** Claim notification (hasar ihbarı) for a policy, chosen by search or passed as ?policyId=. */
@Component({
  selector: 'app-claim-report',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatAutocompleteModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    StatusBadge,
  ],
  templateUrl: './claim-report.html',
  styleUrl: './claim-report.scss',
})
export class ClaimReport implements OnInit {
  /** Optional query parameter. */
  readonly policyId = input<string>();

  private readonly claims = inject(ClaimService);
  private readonly policies = inject(PolicyService);
  private readonly router = inject(Router);

  protected readonly cities = toSignal(inject(ReferenceService).cities$, { initialValue: [] });
  protected readonly maxDate = today();
  protected readonly policy = signal<Policy | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly policySearch = new FormControl<string | PolicySummary>('', {
    nonNullable: true,
  });
  protected readonly suggestions = toSignal(
    this.policySearch.valueChanges.pipe(
      filter((value): value is string => typeof value === 'string'),
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((query) =>
        query.trim().length < 2
          ? of([])
          : this.policies
              .search(
                { query, status: null, productCode: null, customerId: null },
                { page: 0, size: 8 },
              )
              .pipe(
                map((page) => page.content.filter((p) => p.status !== 'QUOTE')),
                catchError(() => of([])),
              ),
      ),
    ),
    { initialValue: [] as PolicySummary[] },
  );

  protected readonly form = inject(NonNullableFormBuilder).group({
    incidentDate: [today(), Validators.required],
    incidentCityId: [null as number | null],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
    estimatedAmount: [null as number | null, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    const id = this.policyId();
    if (id) {
      this.policies
        .get(Number(id))
        .pipe(catchError(() => EMPTY))
        .subscribe((policy) => this.usePolicy(policy));
    }
  }

  protected displayPolicy(value: string | PolicySummary | null): string {
    return typeof value === 'string' || value === null ? (value ?? '') : value.policyNumber;
  }

  protected onPolicySelected(event: MatAutocompleteSelectedEvent): void {
    const summary = event.option.value as PolicySummary;
    this.policies.get(summary.id).subscribe((policy) => this.usePolicy(policy));
  }

  protected submit(): void {
    const policy = this.policy();
    if (!policy || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      if (!policy) {
        this.error.set('Önce poliçeyi seçin.');
      }
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.claims
      .report({
        policyId: policy.id,
        incidentDate: toIsoDate(v.incidentDate),
        incidentCityId: v.incidentCityId,
        description: v.description.trim(),
        estimatedAmount: v.estimatedAmount!,
      })
      .subscribe({
        next: (claim) => void this.router.navigate(['/claims', claim.id]),
        error: (err: unknown) => {
          if (!applyServerErrors(this.form, err)) {
            this.error.set(errorMessage(err));
          }
          this.saving.set(false);
        },
      });
  }

  private usePolicy(policy: Policy): void {
    this.policy.set(policy);
    this.policySearch.setValue(policy.policyNumber, { emitEvent: false });
    this.error.set(null);
  }
}
