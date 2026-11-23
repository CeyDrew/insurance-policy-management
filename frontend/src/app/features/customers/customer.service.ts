import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, PageRequest, toParams } from '../../core/http/page';
import {
  Customer,
  CustomerRequest,
  CustomerSearch,
  CustomerSummary,
  Property,
  PropertyRequest,
  Vehicle,
  VehicleRequest,
} from './customer.models';

const API = '/api/customers';

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);

  search(filter: CustomerSearch, page: PageRequest): Observable<Page<CustomerSummary>> {
    const params = toParams({
      query: filter.query.trim(),
      type: filter.type,
      cityId: filter.cityId,
      page: page.page,
      size: page.size,
      sort: page.sort,
    });
    return this.http.get<Page<CustomerSummary>>(API, { params });
  }

  get(id: number): Observable<Customer> {
    return this.http.get<Customer>(`${API}/${id}`);
  }

  create(request: CustomerRequest): Observable<Customer> {
    return this.http.post<Customer>(API, request);
  }

  update(id: number, request: CustomerRequest): Observable<Customer> {
    return this.http.put<Customer>(`${API}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/${id}`);
  }

  vehicles(customerId: number): Observable<Vehicle[]> {
    return this.http.get<Vehicle[]>(`${API}/${customerId}/vehicles`);
  }

  addVehicle(customerId: number, request: VehicleRequest): Observable<Vehicle> {
    return this.http.post<Vehicle>(`${API}/${customerId}/vehicles`, request);
  }

  properties(customerId: number): Observable<Property[]> {
    return this.http.get<Property[]>(`${API}/${customerId}/properties`);
  }

  addProperty(customerId: number, request: PropertyRequest): Observable<Property> {
    return this.http.post<Property>(`${API}/${customerId}/properties`, request);
  }
}
