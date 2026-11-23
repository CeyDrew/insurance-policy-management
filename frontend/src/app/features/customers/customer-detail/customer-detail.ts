import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/problem-detail';
import {
  CONSTRUCTION_TYPE_LABELS,
  CUSTOMER_TYPE_LABELS,
  GENDER_LABELS,
  USAGE_TYPE_LABELS,
} from '../../../shared/labels';
import { StatusBadge } from '../../../shared/status-badge/status-badge';
import { PolicyService } from '../../policies/policy.service';
import { PropertyDialog } from '../asset-dialogs/property-dialog';
import { AssetDialogData, VehicleDialog } from '../asset-dialogs/vehicle-dialog';
import { CustomerService } from '../customer.service';

@Component({
  selector: 'app-customer-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    StatusBadge,
  ],
  templateUrl: './customer-detail.html',
  styleUrl: './customer-detail.scss',
})
export class CustomerDetail {
  readonly id = input.required({ transform: numberAttribute });

  private readonly customers = inject(CustomerService);
  private readonly policies = inject(PolicyService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  protected readonly canEdit = this.auth.hasRole('ADMIN', 'AGENT');
  protected readonly canDelete = this.auth.hasRole('ADMIN');
  // Widened to string keys so table rows (typed any by mat-table) can index them.
  protected readonly labels = {
    customerType: CUSTOMER_TYPE_LABELS as Record<string, string>,
    gender: GENDER_LABELS as Record<string, string>,
    usage: USAGE_TYPE_LABELS as Record<string, string>,
    construction: CONSTRUCTION_TYPE_LABELS as Record<string, string>,
  };
  protected readonly deleting = signal(false);

  protected readonly customer = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.customers.get(params),
  });
  protected readonly vehicles = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.customers.vehicles(params),
  });
  protected readonly properties = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.customers.properties(params),
  });
  protected readonly policyPage = rxResource({
    params: () => this.id(),
    stream: ({ params }) =>
      this.policies.search(
        { query: '', status: null, productCode: null, customerId: params },
        { page: 0, size: 50 },
      ),
  });

  protected readonly loadError = computed(() => {
    const error = this.customer.error();
    return error ? errorMessage(error) : null;
  });

  protected readonly vehicleColumns = ['plateNumber', 'vehicle', 'usageType', 'vehicleValue'];
  protected readonly propertyColumns = [
    'address',
    'constructionType',
    'grossAreaM2',
    'propertyValue',
  ];
  protected readonly policyColumns = [
    'policyNumber',
    'productCode',
    'insuredObject',
    'period',
    'grossPremium',
    'status',
  ];

  protected addVehicle(): void {
    this.dialog
      .open(VehicleDialog, { data: { customerId: this.id() } satisfies AssetDialogData })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.vehicles.reload();
        this.snackBar.open('Araç eklendi.', undefined, { duration: 3000 });
      });
  }

  protected addProperty(): void {
    this.dialog
      .open(PropertyDialog, { data: { customerId: this.id() } satisfies AssetDialogData })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.properties.reload();
        this.snackBar.open('Konut eklendi.', undefined, { duration: 3000 });
      });
  }

  protected remove(): void {
    const customer = this.customer.value();
    if (!customer || !confirm(`${customer.displayName} silinsin mi? Bu işlem geri alınamaz.`)) {
      return;
    }
    this.deleting.set(true);
    this.customers.delete(customer.id).subscribe({
      next: () => {
        this.snackBar.open('Müşteri silindi.', undefined, { duration: 3000 });
        void this.router.navigate(['/customers']);
      },
      error: (err: unknown) => {
        this.snackBar.open(errorMessage(err), 'Kapat', { duration: 6000 });
        this.deleting.set(false);
      },
    });
  }
}
