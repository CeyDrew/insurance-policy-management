export type Direction = 'OUTBOUND' | 'INBOUND';
export type FileStatus = 'GENERATING' | 'GENERATED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type Outcome = 'COLLECTED' | 'REJECTED' | 'ERROR';

export interface CollectionFile {
  id: number;
  direction: Direction;
  requestFileId: number | null;
  fileName: string;
  fileDate: string;
  status: FileStatus;
  recordCount: number | null;
  totalAmount: number | null;
  collectedCount: number;
  collectedAmount: number;
  rejectedCount: number;
  errorCount: number;
  errorMessage: string | null;
  jobExecutionId: number | null;
  createdAt: string;
  createdBy: string;
  completedAt: string | null;
  /** Request file: a result file for it has been applied or is being applied. */
  answered: boolean;
  /** Result file: stopped while being applied, can be restarted. */
  interrupted: boolean;
}

export interface RejectReason {
  code: string;
  description: string;
  count: number;
  amount: number;
}

export interface CollectionFileDetail {
  file: CollectionFile;
  rejectReasons: RejectReason[];
  resultFiles: CollectionFile[];
}

export interface CollectionResult {
  lineNo: number;
  installmentId: number | null;
  policyId: number | null;
  policyNumber: string | null;
  installmentNo: number | null;
  amount: number | null;
  resultCode: string | null;
  outcome: Outcome;
  paymentId: number | null;
  message: string | null;
}

export const DIRECTION_LABELS: Record<Direction, string> = {
  OUTBOUND: 'Talep (bankaya)',
  INBOUND: 'Sonuç (bankadan)',
};

export const OUTCOME_LABELS: Record<Outcome, string> = {
  COLLECTED: 'Tahsil edildi',
  REJECTED: 'Reddedildi',
  ERROR: 'Hatalı satır',
};

export function isBusy(file: CollectionFile): boolean {
  return file.status === 'GENERATING' || (file.status === 'PROCESSING' && !file.interrupted);
}
