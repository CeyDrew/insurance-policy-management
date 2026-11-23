import { InstallmentStatus, PolicyStatus } from '../policies/policy.models';

export type PaymentMethod = 'CREDIT_CARD' | 'BANK_TRANSFER' | 'CASH' | 'DIRECT_DEBIT';

export interface InstallmentRow {
  id: number;
  policyId: number;
  policyNumber: string;
  policyStatus: PolicyStatus;
  customerId: number;
  customerName: string;
  installmentNo: number;
  dueDate: string;
  amount: number;
  status: InstallmentStatus;
  paidAt: string | null;
  daysOverdue: number;
}

export interface InstallmentSearch {
  query: string;
  /** null together with unpaidOnly lists every installment */
  status: InstallmentStatus | null;
  unpaidOnly: boolean;
  policyId: number | null;
}

export interface PaymentResult {
  paymentId: number;
  referenceNo: string;
  amount: number;
  paymentMethod: PaymentMethod;
  installmentId: number;
  installmentNo: number;
  policyId: number;
  policyNumber: string;
  policyReactivated: boolean;
}

/** What the payment dialog needs to show about the installment being collected. */
export interface PaymentTarget {
  installmentId: number;
  installmentNo: number;
  policyNumber: string;
  customerName?: string;
  dueDate: string;
  amount: number;
}
