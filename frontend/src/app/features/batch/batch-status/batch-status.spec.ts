import { presentStatus } from './batch-status';

describe('presentStatus', () => {
  it('highlights completed executions that skipped items', () => {
    expect(presentStatus('COMPLETED', 'COMPLETED_WITH_SKIPS')).toEqual(
      expect.objectContaining({ label: 'Atlananlarla tamamlandı', tone: 'warning' }),
    );
    expect(presentStatus('COMPLETED', 'COMPLETED').tone).toBe('success');
  });

  it('treats starting and started executions as running', () => {
    expect(presentStatus('STARTING').label).toBe('Çalışıyor');
    expect(presentStatus('STARTED').icon).toBe('sync');
  });

  it('marks failures as danger', () => {
    expect(presentStatus('FAILED', 'FAILED')).toEqual(
      expect.objectContaining({ label: 'Hata', tone: 'danger' }),
    );
  });
});
