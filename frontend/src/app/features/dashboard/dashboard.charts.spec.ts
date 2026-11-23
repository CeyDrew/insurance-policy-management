import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {
  PRODUCT_COLORS,
  change,
  formatPercent,
  lossRatioChart,
  monthLabel,
  productionChart,
} from './dashboard.charts';
import { LossRatio, MonthlyProduction } from './dashboard.models';
import { DashboardService } from './dashboard.service';

function row(
  month: string,
  productCode: 'TRAFFIC' | 'HOME',
  grossPremium: number,
): MonthlyProduction {
  return {
    month,
    productCode,
    productName: productCode === 'TRAFFIC' ? 'Trafik' : 'Konut',
    policyCount: 1,
    grossPremium,
  };
}

describe('dashboard charts', () => {
  it('stacks one dataset per product in fixed order, coloured by product', () => {
    const config = productionChart(
      [row('2026-01', 'HOME', 300), row('2026-01', 'TRAFFIC', 100), row('2026-02', 'TRAFFIC', 200)],
      '#fff',
    );

    expect(config.data.labels).toEqual(['Oca 26', 'Şub 26']);
    expect(config.data.datasets.map((d) => d.label)).toEqual(['Trafik', 'Konut']);
    expect(config.data.datasets[0].data).toEqual([100, 200]);
    expect(config.data.datasets[1].data).toEqual([300, 0]);
    expect(config.data.datasets[1].backgroundColor).toBe(PRODUCT_COLORS.HOME);
  });

  it('draws loss ratios as percentages', () => {
    const rows: LossRatio[] = [
      {
        productCode: 'CASCO',
        productName: 'Kasko',
        earnedPremium: 1000,
        paidClaims: 500,
        outstandingClaims: 150,
        incurredClaims: 650,
        lossRatio: 0.65,
      },
    ];

    const config = lossRatioChart(rows);

    expect(config.data.datasets[0].data).toEqual([65]);
    expect(config.options?.indexAxis).toBe('y');
  });

  it('formats months, ratios and changes', () => {
    expect(monthLabel('2026-08')).toBe('Ağu 26');
    expect(formatPercent(0.6534)).toBe('%65,3');
    expect(formatPercent(null)).toBe('—');
    expect(change(120, 100)).toBeCloseTo(0.2);
    expect(change(50, 0)).toBeNull();
  });
});

describe('DashboardService', () => {
  it('loads the dashboard and triggers a refresh', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(DashboardService);
    const http = TestBed.inject(HttpTestingController);

    service.get().subscribe();
    http.expectOne({ method: 'GET', url: '/api/dashboard' }).flush({});
    service.refresh().subscribe((result) => expect(result.refreshedAt).toBe('2026-11-23T18:30:00'));
    http
      .expectOne({ method: 'POST', url: '/api/dashboard/refresh' })
      .flush({ refreshedAt: '2026-11-23T18:30:00' });
    http.verify();
  });
});
