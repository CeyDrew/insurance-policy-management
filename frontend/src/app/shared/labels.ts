/** Turkish UI labels for backend enum values. */

export const CUSTOMER_TYPE_LABELS = {
  INDIVIDUAL: 'Bireysel',
  CORPORATE: 'Kurumsal',
} as const;

export const GENDER_LABELS = {
  MALE: 'Erkek',
  FEMALE: 'Kadın',
} as const;

export const USAGE_TYPE_LABELS = {
  PRIVATE: 'Hususi',
  COMMERCIAL: 'Ticari',
  TAXI: 'Taksi',
} as const;

export const CONSTRUCTION_TYPE_LABELS = {
  REINFORCED_CONCRETE: 'Betonarme',
  STEEL: 'Çelik',
  MASONRY: 'Yığma',
  WOOD: 'Ahşap',
} as const;

export const PRODUCT_LABELS = {
  TRAFFIC: 'Trafik',
  CASCO: 'Kasko',
  HOME: 'Konut',
  HEALTH: 'Sağlık',
} as const;

export const POLICY_STATUS_LABELS = {
  QUOTE: 'Teklif',
  ACTIVE: 'Yürürlükte',
  SUSPENDED: 'Askıda',
  CANCELLED: 'İptal',
  EXPIRED: 'Süresi doldu',
  RENEWED: 'Yenilendi',
} as const;

export const INSTALLMENT_STATUS_LABELS = {
  PENDING: 'Bekliyor',
  PAID: 'Ödendi',
  OVERDUE: 'Gecikmede',
  CANCELLED: 'İptal',
} as const;

export const CLAIM_STATUS_LABELS = {
  REPORTED: 'İhbar edildi',
  IN_REVIEW: 'İncelemede',
  APPROVED: 'Onaylandı',
  REJECTED: 'Reddedildi',
  PAID: 'Ödendi',
} as const;

export const DOCUMENT_TYPE_LABELS = {
  ACCIDENT_REPORT: 'Kaza tespit tutanağı',
  PHOTO: 'Fotoğraf',
  INVOICE: 'Fatura',
  EXPERT_REPORT: 'Eksper raporu',
  MEDICAL_REPORT: 'Sağlık raporu',
  OTHER: 'Diğer',
} as const;

export const PAYMENT_METHOD_LABELS = {
  CREDIT_CARD: 'Kredi kartı',
  BANK_TRANSFER: 'Havale / EFT',
  CASH: 'Nakit',
  DIRECT_DEBIT: 'Otomatik ödeme',
} as const;

/** Options for mat-select, in declaration order. */
export function options<K extends string>(
  labels: Record<K, string>,
): { value: K; label: string }[] {
  return (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }));
}
