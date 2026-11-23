import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/problem-detail';
import { ChartComponent } from '../../shared/chart/chart';
import { StatusBadge } from '../../shared/status-badge/status-badge';
import {
  PRODUCT_COLORS,
  change,
  formatCompactMoney,
  formatMoney,
  formatPercent,
  lossRatioChart,
  monthLabel,
  productionChart,
} from './dashboard.charts';
import { DashboardService } from './dashboard.service';

interface Tile {
  label: string;
  value: string;
  detail?: string;
  icon: string;
  link?: string;
  /** Change against the same period last year, as a ratio. */
  trend?: number | null;
}

@Component({
  selector: 'app-dashboard',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    ChartComponent,
    StatusBadge,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly service = inject(DashboardService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  protected readonly data = rxResource({ stream: () => this.service.get() });
  protected readonly isAdmin = this.auth.hasRole('ADMIN');
  protected readonly isOffice = this.auth.hasRole('ADMIN', 'AGENT');
  protected readonly refreshing = signal(false);
  protected readonly loadError = computed(() =>
    this.data.error() ? errorMessage(this.data.error()) : null,
  );

  protected readonly colors = PRODUCT_COLORS;
  protected readonly money = formatMoney;
  protected readonly percent = formatPercent;
  protected readonly monthLabel = monthLabel;

  protected readonly tiles = computed<Tile[]>(() => {
    const k = this.data.value()?.kpis;
    if (!k) {
      return [];
    }
    return [
      {
        label: 'Aktif poliçe',
        value: k.activePolicies.toLocaleString('tr-TR'),
        detail:
          k.suspendedPolicies > 0 ? `${k.suspendedPolicies} poliçe askıda` : 'Askıda poliçe yok',
        icon: 'verified',
        link: '/policies',
      },
      {
        label: 'Prim üretimi (yılbaşından beri)',
        value: formatCompactMoney(k.grossWrittenYtd),
        detail: `Geçen yıl aynı dönem ${formatCompactMoney(k.grossWrittenPreviousYtd)}`,
        icon: 'trending_up',
        trend: change(k.grossWrittenYtd, k.grossWrittenPreviousYtd),
      },
      {
        label: 'Hasar/prim oranı',
        value: formatPercent(k.lossRatio),
        detail: `${formatCompactMoney(k.incurredClaims)} hasar / ${formatCompactMoney(k.earnedPremium)} kazanılmış prim`,
        icon: 'balance',
      },
      {
        label: 'Açık hasar dosyası',
        value: k.openClaims.toLocaleString('tr-TR'),
        detail: `Muallak ${formatCompactMoney(k.outstandingClaims)}`,
        icon: 'car_crash',
        link: '/claims',
      },
      {
        label: 'Gecikmiş taksit',
        value: k.overdueInstallments.toLocaleString('tr-TR'),
        detail: `Toplam ${formatCompactMoney(k.overdueAmount)}`,
        icon: 'schedule',
        link: this.isOffice ? '/collections' : undefined,
      },
      {
        label: 'Teklif dönüşümü (12 ay)',
        value: formatPercent(k.quoteConversion),
        detail: 'Poliçeleşen teklif oranı',
        icon: 'swap_horiz',
      },
      {
        label: 'Yenileme bekleyen',
        value: k.renewalsDue.toLocaleString('tr-TR'),
        detail: '30 gün içinde bitecek aktif poliçe',
        icon: 'autorenew',
      },
    ];
  });

  protected readonly production = computed(() => this.data.value()?.monthlyProduction ?? []);
  protected readonly productionMonths = computed(() => [
    ...new Set(this.production().map((r) => r.month)),
  ]);
  protected readonly productionConfig = computed(() =>
    productionChart(this.production(), this.surface()),
  );
  protected readonly lossRatioConfig = computed(() =>
    lossRatioChart(this.data.value()?.lossRatios ?? []),
  );

  protected readonly productMix = computed(() => {
    const mix = this.data.value()?.productMix ?? [];
    const total = mix.reduce((sum, p) => sum + p.activeGrossPremium, 0);
    return mix.map((p) => ({ ...p, share: total > 0 ? p.activeGrossPremium / total : 0 }));
  });

  protected readonly claimStatuses = computed(() => {
    const counts = this.data.value()?.claimStatuses ?? [];
    const max = Math.max(1, ...counts.map((c) => c.count));
    return counts.map((c) => ({ ...c, width: c.count / max }));
  });

  protected productionAt(month: string, product: string): number {
    return (
      this.production().find((r) => r.month === month && r.productCode === product)?.grossPremium ??
      0
    );
  }

  protected monthTotal(month: string): number {
    return this.production()
      .filter((r) => r.month === month)
      .reduce((sum, r) => sum + r.grossPremium, 0);
  }

  protected refresh(): void {
    this.refreshing.set(true);
    this.service.refresh().subscribe({
      next: () => {
        this.refreshing.set(false);
        this.data.reload();
        this.snackBar.open('Rapor verileri yeniden hesaplandı.', 'Kapat', { duration: 3000 });
      },
      error: (error) => {
        this.refreshing.set(false);
        this.snackBar.open(errorMessage(error), 'Kapat', { duration: 5000 });
      },
    });
  }

  /** Card surface colour, used for the gaps between stacked bar segments. Read as a resolved rgb() value:
   *  the --mat-sys-surface token itself is a light-dark() expression that canvas cannot parse. */
  private surface(): string {
    return getComputedStyle(document.body).backgroundColor || '#ffffff';
  }
}
