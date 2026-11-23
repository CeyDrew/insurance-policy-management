import { ProductCode } from '../../shared/reference/reference.models';

export type ClaimStatus = 'REPORTED' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'PAID';
export type DocumentType =
  'ACCIDENT_REPORT' | 'PHOTO' | 'INVOICE' | 'EXPERT_REPORT' | 'MEDICAL_REPORT' | 'OTHER';

export interface ClaimSummary {
  id: number;
  claimNumber: string;
  status: ClaimStatus;
  policyId: number;
  policyNumber: string;
  productName: string;
  customerName: string;
  incidentDate: string;
  reportedAt: string;
  estimatedAmount: number | null;
  approvedAmount: number | null;
  handlerName: string | null;
}

export interface ClaimDocument {
  id: number;
  documentType: DocumentType;
  fileName: string;
  contentType: string;
  fileSize: number;
  createdAt: string;
  createdBy: string;
}

export interface ClaimHistoryEntry {
  changedAt: string;
  changedBy: string;
  operation: 'INSERT' | 'UPDATE' | 'DELETE';
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus | null;
}

export interface Claim {
  id: number;
  claimNumber: string;
  status: ClaimStatus;
  policyId: number;
  policyNumber: string;
  productCode: ProductCode;
  productName: string;
  policyStatus: string;
  ncdStep: number;
  customerId: number;
  customerName: string;
  insuredObject: string;
  incidentDate: string;
  reportedAt: string;
  incidentCityName: string | null;
  description: string;
  estimatedAmount: number | null;
  approvedAmount: number | null;
  paidAmount: number | null;
  handlerId: number | null;
  handlerName: string | null;
  handlerUsername: string | null;
  decisionNote: string | null;
  decidedAt: string | null;
  paidAt: string | null;
  createdBy: string;
  version: number;
  documents: ClaimDocument[];
  history: ClaimHistoryEntry[];
}

export interface ClaimSearch {
  query: string;
  status: ClaimStatus | null;
  mine: boolean;
  policyId: number | null;
}

export interface ReportClaimRequest {
  policyId: number;
  incidentDate: string;
  incidentCityId: number | null;
  description: string;
  estimatedAmount: number;
}
