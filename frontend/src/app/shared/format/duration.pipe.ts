import { Pipe, PipeTransform } from '@angular/core';

/** 850 -> "0,9 sn", 38200 -> "38,2 sn", 126000 -> "2 dk 6 sn", 3_726_000 -> "1 sa 2 dk". */
export function formatDuration(millis: number | null | undefined): string {
  if (millis === null || millis === undefined || millis < 0) {
    return '—';
  }
  const totalSeconds = Math.round(millis / 1000);
  if (millis < 60_000) {
    return `${(millis / 1000).toLocaleString('tr-TR', { maximumFractionDigits: 1, minimumFractionDigits: 1 })} sn`;
  }
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours} sa ${minutes} dk`;
  }
  return seconds > 0 ? `${minutes} dk ${seconds} sn` : `${minutes} dk`;
}

@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(millis: number | null | undefined): string {
    return formatDuration(millis);
  }
}

/** 1536 -> "1,5 KB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toLocaleString('tr-TR', { maximumFractionDigits: 1 })} ${units[unit]}`;
}
