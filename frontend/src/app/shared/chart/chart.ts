import { Component, ElementRef, effect, input, viewChild } from '@angular/core';
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartConfiguration,
  Legend,
  LinearScale,
  Tooltip,
} from 'chart.js';

// Only what the app draws, so the rest of Chart.js is tree-shaken.
Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip, Legend);
Chart.defaults.font.family = 'Roboto, sans-serif';

/** Thin Chart.js wrapper: draws the given configuration and redraws it when the input changes. */
@Component({
  selector: 'app-chart',
  template: `<canvas #canvas role="img" [attr.aria-label]="label()"></canvas>`,
  styles: `
    :host {
      display: block;
      position: relative;
      height: var(--chart-height, 300px);
    }
  `,
})
export class ChartComponent {
  readonly config = input.required<ChartConfiguration<'bar'>>();
  /** Screen-reader description; the page also offers the data as a table. */
  readonly label = input.required<string>();

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  constructor() {
    effect((onCleanup) => {
      const chart = new Chart(this.canvas().nativeElement, this.config());
      onCleanup(() => chart.destroy());
    });
  }
}
