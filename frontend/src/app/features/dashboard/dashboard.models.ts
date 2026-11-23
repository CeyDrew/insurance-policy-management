import { ClaimStatus } from '../claims/claim.models';
import { ProductCode } from '../../shared/reference/reference.models';

export interface Kpis {
  activePolicies: number;
  suspendedPolicies: number;
  grossWrittenYtd: number;
  grossWrittenPreviousYtd: number;
  earnedPremium: number;
  incurredClaims: number;
  lossRatio: number | null;
  openClaims: number;
  outstandingClaims: number;
  overdueInstallments: number;
  overdueAmount: number;
  quoteConversion: number | null;
  renewalsDue: number;
}

export interface MonthlyProduction {
  /** yyyy-MM */
  month: string;
  productCode: ProductCode;
  productName: string;
  policyCount: number;
  grossPremium: number;
}

export interface ProductMix {
  productCode: ProductCode;
  productName: string;
  activePolicies: number;
  activeGrossPremium: number;
}

export interface LossRatio {
  productCode: ProductCode;
  productName: string;
  earnedPremium: number;
  paidClaims: number;
  outstandingClaims: number;
  incurredClaims: number;
  lossRatio: number | null;
}

export interface AgentPerformance {
  agentName: string;
  quoteCount: number;
  issuedCount: number;
  conversionRate: number | null;
  grossWritten: number;
  cancelledCount: number;
  lossRatio: number | null;
}

export interface ClaimStatusCount {
  status: ClaimStatus;
  count: number;
}

export interface Dashboard {
  generatedAt: string;
  productionRefreshedAt: string | null;
  kpis: Kpis;
  monthlyProduction: MonthlyProduction[];
  productMix: ProductMix[];
  lossRatios: LossRatio[];
  agents: AgentPerformance[];
  claimStatuses: ClaimStatusCount[];
}
