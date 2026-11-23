import { ProductCode } from '../../shared/reference/reference.models';

export type PolicyStatus = 'QUOTE' | 'ACTIVE' | 'SUSPENDED' | 'CANCELLED' | 'EXPIRED' | 'RENEWED';
export type InstallmentStatus = 'PENDING' | 'PAID' | 'OVERDUE' | 'CANCELLED';

export interface PolicySummary {
  id: number;
  policyNumber: string;
  productCode: ProductCode;
  productName: string;
  customerId: number;
  customerName: string;
  insuredObject: string;
  status: PolicyStatus;
  startDate: string;
  endDate: string;
  grossPremium: number;
}

export interface Installment {
  id: number;
  installmentNo: number;
  dueDate: string;
  amount: number;
  status: InstallmentStatus;
  paidAt: string | null;
}

export interface Policy {
  id: number;
  policyNumber: string;
  status: PolicyStatus;
  productId: number;
  productCode: ProductCode;
  productName: string;
  customerId: number;
  customerName: string;
  customerIdentityNumber: string;
  vehicleId: number | null;
  propertyId: number | null;
  insuredObject: string;
  agentName: string;
  startDate: string;
  endDate: string;
  ncdStep: number;
  netPremium: number;
  taxAmount: number;
  grossPremium: number;
  installmentCount: number;
  quoteValidUntil: string | null;
  issuedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  refundAmount: number | null;
  renewedFromPolicyId: number | null;
  renewedFromPolicyNumber: string | null;
  createdAt: string;
  installments: Installment[];
}

export interface PolicySearch {
  query: string;
  status: PolicyStatus | null;
  productCode: ProductCode | null;
  customerId: number | null;
}

export interface QuoteRequest {
  productId: number;
  customerId: number;
  vehicleId: number | null;
  propertyId: number | null;
  startDate: string;
  installmentCount: number;
}
