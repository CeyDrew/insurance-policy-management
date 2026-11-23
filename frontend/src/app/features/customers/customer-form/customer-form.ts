import { Component, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { errorMessage } from '../../../core/http/problem-detail';
import { fromIsoDate, toIsoDate, today } from '../../../core/util/dates';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { GENDER_LABELS, options } from '../../../shared/labels';
import { ReferenceService } from '../../../shared/reference/reference.service';
import { tcknValidator, vknValidator } from '../../../shared/validators/identity-validators';
import { Customer, CustomerRequest, CustomerType, Gender } from '../customer.models';
import { CustomerService } from '../customer.service';

@Component({
  selector: 'app-customer-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './customer-form.html',
  styleUrl: './customer-form.scss',
})
export class CustomerForm implements OnInit {
  /** Route parameter; absent when creating a new customer. */
  readonly id = input<string>();

  private readonly customers = inject(CustomerService);
  private readonly router = inject(Router);

  protected readonly cities = toSignal(inject(ReferenceService).cities$, { initialValue: [] });
  protected readonly genderOptions = options(GENDER_LABELS);
  protected readonly maxBirthDate = today();

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private version: number | null = null;

  protected readonly form = inject(NonNullableFormBuilder).group({
    customerType: ['INDIVIDUAL' as CustomerType, Validators.required],
    nationalId: ['', [Validators.required, tcknValidator]],
    firstName: ['', [Validators.required, Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.maxLength(50)]],
    birthDate: [null as Date | null, Validators.required],
    gender: [null as Gender | null],
    taxNumber: ['', [Validators.required, vknValidator]],
    companyName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    phone: ['', Validators.pattern(/^0\d{10}$/)],
    addressLine: ['', Validators.maxLength(300)],
    cityId: [null as number | null, Validators.required],
  });

  constructor() {
    this.form.controls.customerType.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((type) => this.applyType(type));
    this.applyType(this.form.controls.customerType.value);
  }

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.loading.set(true);
      this.customers.get(Number(id)).subscribe({
        next: (customer) => {
          this.patch(customer);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(errorMessage(err));
          this.loading.set(false);
        },
      });
    }
  }

  protected get isEdit(): boolean {
    return !!this.id();
  }

  protected get isIndividual(): boolean {
    return this.form.controls.customerType.value === 'INDIVIDUAL';
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const request = this.toRequest();
    const id = this.id();
    const call = id ? this.customers.update(Number(id), request) : this.customers.create(request);
    call.subscribe({
      next: (customer) => void this.router.navigate(['/customers', customer.id]),
      error: (err: unknown) => {
        if (!applyServerErrors(this.form, err)) {
          this.error.set(errorMessage(err));
        }
        this.saving.set(false);
      },
    });
  }

  /** Enables only the controls that belong to the selected customer type. */
  private applyType(type: CustomerType): void {
    const individual = ['nationalId', 'firstName', 'lastName', 'birthDate', 'gender'] as const;
    const corporate = ['taxNumber', 'companyName'] as const;
    for (const name of individual) {
      const control = this.form.controls[name];
      if (type === 'INDIVIDUAL') {
        control.enable({ emitEvent: false });
      } else {
        control.disable({ emitEvent: false });
      }
    }
    for (const name of corporate) {
      const control = this.form.controls[name];
      if (type === 'CORPORATE') {
        control.enable({ emitEvent: false });
      } else {
        control.disable({ emitEvent: false });
      }
    }
  }

  private patch(customer: Customer): void {
    this.version = customer.version;
    this.form.patchValue({
      customerType: customer.customerType,
      nationalId: customer.nationalId ?? '',
      firstName: customer.firstName ?? '',
      lastName: customer.lastName ?? '',
      birthDate: fromIsoDate(customer.birthDate),
      gender: customer.gender,
      taxNumber: customer.taxNumber ?? '',
      companyName: customer.companyName ?? '',
      email: customer.email ?? '',
      phone: customer.phone ?? '',
      addressLine: customer.addressLine ?? '',
      cityId: customer.cityId,
    });
    // Changing the type of an existing customer would change their identity; not allowed.
    this.form.controls.customerType.disable();
  }

  private toRequest(): CustomerRequest {
    const v = this.form.getRawValue();
    const individual = v.customerType === 'INDIVIDUAL';
    const blankToNull = (value: string) => (value.trim() === '' ? null : value.trim());
    return {
      customerType: v.customerType,
      nationalId: individual ? v.nationalId : null,
      firstName: individual ? v.firstName.trim() : null,
      lastName: individual ? v.lastName.trim() : null,
      birthDate: individual && v.birthDate ? toIsoDate(v.birthDate) : null,
      gender: individual ? v.gender : null,
      taxNumber: individual ? null : v.taxNumber,
      companyName: individual ? null : v.companyName.trim(),
      email: blankToNull(v.email),
      phone: blankToNull(v.phone),
      addressLine: blankToNull(v.addressLine),
      cityId: v.cityId!,
      version: this.version,
    };
  }
}
