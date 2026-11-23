/** Spring Batch status of a job or step execution. */
export type BatchStatus =
  | 'STARTING'
  | 'STARTED'
  | 'STOPPING'
  | 'STOPPED'
  | 'COMPLETED'
  | 'FAILED'
  | 'ABANDONED'
  | 'UNKNOWN';

export type JobParameterName = 'runDate' | 'month' | 'inputFile';

export interface ExecutionSummary {
  id: number;
  jobName: string;
  status: BatchStatus;
  exitCode: string;
  exitMessage: string | null;
  startTime: string | null;
  endTime: string | null;
  durationMillis: number | null;
  readCount: number;
  writeCount: number;
  skipCount: number;
  parameters: Record<string, string>;
}

export interface JobSummary {
  name: string;
  title: string;
  description: string;
  parameters: JobParameterName[];
  cron: string | null;
  lastExecution: ExecutionSummary | null;
}

export interface StepDetail {
  id: number;
  name: string;
  status: BatchStatus;
  exitCode: string;
  exitMessage: string | null;
  startTime: string | null;
  endTime: string | null;
  durationMillis: number | null;
  readCount: number;
  filterCount: number;
  writeCount: number;
  readSkipCount: number;
  processSkipCount: number;
  writeSkipCount: number;
  commitCount: number;
  rollbackCount: number;
}

export interface ExecutionDetail {
  execution: ExecutionSummary;
  steps: StepDetail[];
  importErrorCount: number;
}

export interface ImportError {
  lineNumber: number;
  rawLine: string | null;
  message: string;
}

export interface ReportFile {
  name: string;
  sizeBytes: number;
  lastModified: string;
}

export const CLAIM_IMPORT_JOB = 'claimImportJob';
export const COMPLETED_WITH_SKIPS = 'COMPLETED_WITH_SKIPS';

export function isRunning(status: BatchStatus | undefined | null): boolean {
  return status === 'STARTING' || status === 'STARTED' || status === 'STOPPING';
}
