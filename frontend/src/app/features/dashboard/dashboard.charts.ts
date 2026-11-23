import { ChartConfiguration, TooltipItem } from 'chart.js';
import { ProductCode } from '../../shared/reference/reference.models';
import { LossRatio, MonthlyProduction } from './dashboard.models';

/**
 * Categorical slots in fixed product order. Validated for colour-vision deficiency on the light surface
 * (adjacent pairs ΔE ≥ 9); aqua and yellow fall below 3:1 contrast, so every chart also has a table view.
 * A product keeps its colour on every chart.
 */
export const PRODUCT_COLORS: Record<ProductCode, string> = {
  TRAFFIC: '#2a78d6',
  CASCO: '#eb6834',
  HOME: '#1baf7a',
  HEALTH: '#eda100',
};

export const PRODUCT_ORDER: ProductCode[] = ['TRAFFIC', 'CASCO', 'HOME', 'HEALTH'];

const TEXT_SECONDARY = '#52514e';
const GRID = '#e7e6e1';
const MONTHS = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];

const money = new Intl.NumberFormat('tr-TR', {
  style: 'currency',
  currency: 'TRY',
  maximumFractionDigits: 0,
});
const compactMoney = new Intl.NumberFormat('tr-TR', {
  style: 'currency',
  currency: 'TRY',
  notation: 'compact',
  maximumFractionDigits: 1,
});
const percent = new Intl.NumberFormat('tr-TR', { style: 'percent', maximumFractionDigits: 1 });

export function formatMoney(value: number): string {
  return money.format(value);
}

/** 1_450_000 -> "₺1,5 Mn" */
export function formatCompactMoney(value: number): string {
  return compactMoney.format(value);
}

export function formatPercent(ratio: number | null | undefined): string {
  return ratio === null || ratio === undefined ? '—' : percent.format(ratio);
}

/** "2026-03" -> "Mar 26" */
export function monthLabel(month: string): string {
  const [year, m] = month.split('-');
  return `${MONTHS[Number(m) - 1]} ${year.slice(2)}`;
}

/** Change against the previous value as a ratio; null when there is nothing to compare with. */
export function change(current: number, previous: number): number | null {
  return previous > 0 ? (current - previous) / previous : null;
}

const axisFont = { size: 12 };

/** Stacked monthly gross premium, one segment per product. */
export function productionChart(
  rows: MonthlyProduction[],
  surface: string,
): ChartConfiguration<'bar'> {
  const months = [...new Set(rows.map((r) => r.month))];
  const products = PRODUCT_ORDER.filter((code) => rows.some((r) => r.productCode === code));
  return {
    type: 'bar',
    data: {
      labels: months.map(monthLabel),
      datasets: products.map((code) => ({
        label: rows.find((r) => r.productCode === code)!.productName,
        data: months.map(
          (month) =>
            rows.find((r) => r.month === month && r.productCode === code)?.grossPremium ?? 0,
        ),
        backgroundColor: PRODUCT_COLORS[code],
        // a 1px surface border on every side leaves a 2px gap between segments
        borderColor: surface,
        borderWidth: 1,
        borderSkipped: false,
        maxBarThickness: 36,
      })),
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      scales: {
        x: {
          stacked: true,
          grid: { display: false },
          ticks: { color: TEXT_SECONDARY, font: axisFont },
        },
        y: {
          stacked: true,
          border: { display: false },
          grid: { color: GRID },
          ticks: {
            color: TEXT_SECONDARY,
            font: axisFont,
            maxTicksLimit: 6,
            callback: (v) => formatCompactMoney(+v),
          },
        },
      },
      plugins: {
        legend: {
          position: 'top',
          align: 'start',
          labels: {
            color: TEXT_SECONDARY,
            boxWidth: 12,
            boxHeight: 12,
            useBorderRadius: true,
            borderRadius: 2,
          },
        },
        tooltip: {
          callbacks: {
            label: (item: TooltipItem<'bar'>) =>
              ` ${item.dataset.label}: ${formatMoney(item.parsed.y ?? 0)}`,
            footer: (items: TooltipItem<'bar'>[]) =>
              `Toplam: ${formatMoney(items.reduce((sum, i) => sum + (i.parsed.y ?? 0), 0))}`,
          },
        },
      },
    },
  };
}

/** Loss ratio per product as horizontal bars on a percent axis. */
export function lossRatioChart(rows: LossRatio[]): ChartConfiguration<'bar'> {
  return {
    type: 'bar',
    data: {
      labels: rows.map((r) => r.productName),
      datasets: [
        {
          label: 'Hasar/prim oranı',
          data: rows.map((r) => (r.lossRatio ?? 0) * 100),
          backgroundColor: rows.map((r) => PRODUCT_COLORS[r.productCode]),
          borderRadius: 4,
          borderSkipped: 'start',
          maxBarThickness: 22,
        },
      ],
    },
    options: {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: {
          beginAtZero: true,
          suggestedMax: 100,
          border: { display: false },
          grid: { color: GRID },
          ticks: { color: TEXT_SECONDARY, font: axisFont, callback: (v) => `%${v}` },
        },
        y: { grid: { display: false }, ticks: { color: TEXT_SECONDARY, font: axisFont } },
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (item: TooltipItem<'bar'>) => {
              const row = rows[item.dataIndex];
              return [
                ` Oran: ${formatPercent(row.lossRatio)}`,
                ` Kazanılmış prim: ${formatMoney(row.earnedPremium)}`,
                ` Gerçekleşen hasar: ${formatMoney(row.incurredClaims)}`,
              ];
            },
          },
        },
      },
    },
  };
}
