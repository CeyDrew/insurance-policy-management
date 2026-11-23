export type ProductCode = 'TRAFFIC' | 'CASCO' | 'HOME' | 'HEALTH';
export type AssetType = 'VEHICLE' | 'PROPERTY' | 'PERSON';

export interface City {
  id: number;
  plateCode: number;
  name: string;
}

export interface Product {
  id: number;
  code: ProductCode;
  name: string;
  description: string;
  assetType: AssetType;
  maxInstallments: number;
  termMonths: number;
}
