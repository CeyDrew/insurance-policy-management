export type CustomerType = 'INDIVIDUAL' | 'CORPORATE';
export type Gender = 'MALE' | 'FEMALE';
export type UsageType = 'PRIVATE' | 'COMMERCIAL' | 'TAXI';
export type ConstructionType = 'REINFORCED_CONCRETE' | 'STEEL' | 'MASONRY' | 'WOOD';

export interface CustomerSummary {
  id: number;
  customerType: CustomerType;
  displayName: string;
  identityNumber: string;
  email: string | null;
  phone: string | null;
  cityName: string;
}

export interface Customer {
  id: number;
  customerType: CustomerType;
  displayName: string;
  nationalId: string | null;
  taxNumber: string | null;
  firstName: string | null;
  lastName: string | null;
  companyName: string | null;
  birthDate: string | null;
  gender: Gender | null;
  email: string | null;
  phone: string | null;
  addressLine: string | null;
  cityId: number;
  cityName: string;
  createdAt: string;
  createdBy: string;
  version: number;
}

export interface CustomerRequest {
  customerType: CustomerType;
  nationalId: string | null;
  taxNumber: string | null;
  firstName: string | null;
  lastName: string | null;
  companyName: string | null;
  birthDate: string | null;
  gender: Gender | null;
  email: string | null;
  phone: string | null;
  addressLine: string | null;
  cityId: number;
  version: number | null;
}

export interface CustomerSearch {
  query: string;
  type: CustomerType | null;
  cityId: number | null;
}

export interface Vehicle {
  id: number;
  customerId: number;
  plateNumber: string;
  chassisNumber: string;
  brand: string;
  model: string;
  modelYear: number;
  usageType: UsageType;
  vehicleValue: number;
  description: string;
}

export type VehicleRequest = Omit<Vehicle, 'id' | 'customerId' | 'description'>;

export interface Property {
  id: number;
  customerId: number;
  cityId: number;
  cityName: string;
  addressLine: string;
  buildingYear: number;
  grossAreaM2: number;
  constructionType: ConstructionType;
  propertyValue: number;
  description: string;
}

export type PropertyRequest = Omit<Property, 'id' | 'customerId' | 'cityName' | 'description'>;
