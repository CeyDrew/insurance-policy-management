import { formatBytes, formatDuration } from './duration.pipe';

describe('formatDuration', () => {
  it('shows seconds with one decimal under a minute', () => {
    expect(formatDuration(850)).toBe('0,9 sn');
    expect(formatDuration(38_200)).toBe('38,2 sn');
  });

  it('shows minutes and seconds, or hours and minutes', () => {
    expect(formatDuration(126_000)).toBe('2 dk 6 sn');
    expect(formatDuration(120_000)).toBe('2 dk');
    expect(formatDuration(3_726_000)).toBe('1 sa 2 dk');
  });

  it('shows a dash for missing values', () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(undefined)).toBe('—');
  });
});

describe('formatBytes', () => {
  it('uses binary units with a Turkish decimal comma', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1,5 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5 MB');
  });
});
