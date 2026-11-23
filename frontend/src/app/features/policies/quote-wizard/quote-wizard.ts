import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
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
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatStepperModule } from '@angular/material/stepper';
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
import { addDays, toIsoDate, today } from '../../../core/util/dates';
import { Product } from '../../../shared/reference/reference.models';
import { ReferenceService } from '../../../shared/reference/reference.service';
import { PropertyDialog } from '../../customers/asset-dialogs/property-dialog';
import { AssetDialogData, VehicleDialog } from '../../customers/asset-dialogs/vehicle-dialog';
import { Customer, CustomerSummary } from '../../customers/customer.models';
import { CustomerService } from '../../customers/customer.service';
import { Policy } from '../policy.models';
import { PolicyService } from '../policy.service';

/** Keep in sync with PolicyService.MAX_START_DAYS_AHEAD on the backend. */
const MAX_START_DAYS_AHEAD = 60;

const PRODUCT_ICONS: Record<string, string> = {
  TRAFFIC: 'directions_car',
  CASCO: 'car_crash',
  HOME: 'home',
  HEALTH: 'health_and_safety',
};

/**
 * Quote wizard: customer -> product and insured object -> terms -> priced quote -> issue.
 * The premium comes from the backend premium engine; the wizard never calculates prices itself.
 */
@Component({
  selector: 'app-quote-wizard',
  imports: [
    CurrencyPipe,
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
    MatProgressBarModule,
    MatRadioModule,
    MatSelectModule,
    MatStepperModule,
  ],
  templateUrl: './quote-wizard.html',
  styleUrl: './quote-wizard.scss',
})
export class QuoteWizard implements OnInit {
  /** Optional ?customerId= to start with a preselected customer. */
  readonly customerId = input<string>();

  private readonly customers = inject(CustomerService);
  private readonly policies = inject(PolicyService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly productIcons = PRODUCT_ICONS;
  protected readonly products = toSignal(inject(ReferenceService).products$, {
    initialValue: [] as Product[],
  });
  protected readonly minStart = today();
  protected readonly maxStart = addDays(today(), MAX_START_DAYS_AHEAD);

  // Step 1: customer
  protected readonly customerSearch = new FormControl<string | CustomerSummary>('', {
    nonNullable: true,
  });
  protected readonly customerForm = this.fb.group({
    customer: [null as Customer | null, Validators.required],
  });
  protected readonly selectedCustomer = signal<Customer | null>(null);
  protected readonly suggestions = toSignal(
    this.customerSearch.valueChanges.pipe(
      filter((value): value is string => typeof value === 'string'),
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((query) =>
        query.trim().length < 2
          ? of([])
          : this.customers.search({ query, type: null, cityId: null }, { page: 0, size: 8 }).pipe(
              map((page) => page.content),
              catchError(() => of([])),
            ),
      ),
    ),
    { initialValue: [] as CustomerSummary[] },
  );

  // Step 2: product and insured object
  protected readonly productForm = this.fb.group({
    product: [null as Product | null, Validators.required],
    vehicleId: [null as number | null],
    propertyId: [null as number | null],
  });
  protected readonly selectedProduct = toSignal(this.productForm.controls.product.valueChanges, {
    initialValue: null,
  });
  protected readonly vehicles = rxResource({
    params: () => this.selectedCustomer()?.id,
    stream: ({ params }) => this.customers.vehicles(params),
  });
  protected readonly properties = rxResource({
    params: () => this.selectedCustomer()?.id,
    stream: ({ params }) => this.customers.properties(params),
  });

  // Step 3: terms
  protected readonly termsForm = this.fb.group({
    startDate: [today(), Validators.required],
    installmentCount: [1, [Validators.required, Validators.min(1)]],
  });
  protected readonly installmentOptions = computed(() => {
    const max = this.selectedProduct()?.maxInstallments ?? 1;
    return Array.from({ length: max }, (_, i) => i + 1);
  });

  // Step 4: quote
  protected readonly quote = signal<Policy | null>(null);
  protected readonly working = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.productForm.controls.product.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((product) => {
        this.updateAssetValidators(product);
        const count = this.termsForm.controls.installmentCount;
        if (product && count.value > product.maxInstallments) {
          count.setValue(product.maxInstallments);
        }
        this.quote.set(null);
      });
    // Any change after pricing invalidates the shown quote.
    this.termsForm.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.quote.set(null));
  }

  ngOnInit(): void {
    const id = this.customerId();
    if (id) {
      this.customers
        .get(Number(id))
        .pipe(catchError(() => EMPTY))
        .subscribe((customer) => this.useCustomer(customer));
    }
  }

  protected displayCustomer(value: string | CustomerSummary | null): string {
    return typeof value === 'string' || value === null ? (value ?? '') : value.displayName;
  }

  protected onCustomerSelected(event: MatAutocompleteSelectedEvent): void {
    const summary = event.option.value as CustomerSummary;
    this.customers.get(summary.id).subscribe((customer) => this.useCustomer(customer));
  }

  protected isProductAvailable(product: Product): boolean {
    return product.assetType !== 'PERSON' || this.selectedCustomer()?.customerType === 'INDIVIDUAL';
  }

  protected addVehicle(): void {
    const customer = this.selectedCustomer();
    if (!customer) {
      return;
    }
    this.dialog
      .open(VehicleDialog, { data: { customerId: customer.id } satisfies AssetDialogData })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe((vehicle) => {
        this.vehicles.reload();
        this.productForm.controls.vehicleId.setValue(vehicle.id);
      });
  }

  protected addProperty(): void {
    const customer = this.selectedCustomer();
    if (!customer) {
      return;
    }
    this.dialog
      .open(PropertyDialog, { data: { customerId: customer.id } satisfies AssetDialogData })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe((property) => {
        this.properties.reload();
        this.productForm.controls.propertyId.setValue(property.id);
      });
  }

  protected calculate(): void {
    const customer = this.selectedCustomer();
    const { product, vehicleId, propertyId } = this.productForm.getRawValue();
    const { startDate, installmentCount } = this.termsForm.getRawValue();
    if (!customer || !product || this.termsForm.invalid || this.working()) {
      return;
    }
    this.working.set(true);
    this.error.set(null);
    this.policies
      .createQuote({
        productId: product.id,
        customerId: customer.id,
        vehicleId: product.assetType === 'VEHICLE' ? vehicleId : null,
        propertyId: product.assetType === 'PROPERTY' ? propertyId : null,
        startDate: toIsoDate(startDate),
        installmentCount,
      })
      .subscribe({
        next: (quote) => {
          this.quote.set(quote);
          this.working.set(false);
        },
        error: (err: unknown) => {
          this.error.set(errorMessage(err));
          this.working.set(false);
        },
      });
  }

  protected issue(): void {
    const quote = this.quote();
    if (!quote || this.working()) {
      return;
    }
    this.working.set(true);
    this.error.set(null);
    this.policies.issue(quote.id).subscribe({
      next: (policy) => void this.router.navigate(['/policies', policy.id]),
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.working.set(false);
      },
    });
  }

  private useCustomer(customer: Customer): void {
    this.selectedCustomer.set(customer);
    this.customerForm.controls.customer.setValue(customer);
    this.customerSearch.setValue(customer.displayName, { emitEvent: false });
    this.productForm.reset();
    this.quote.set(null);
  }

  private updateAssetValidators(product: Product | null): void {
    const { vehicleId, propertyId } = this.productForm.controls;
    vehicleId.setValidators(product?.assetType === 'VEHICLE' ? Validators.required : null);
    propertyId.setValidators(product?.assetType === 'PROPERTY' ? Validators.required : null);
    vehicleId.updateValueAndValidity({ emitEvent: false });
    propertyId.updateValueAndValidity({ emitEvent: false });
  }
}
