package com.portfolio.insurance.customer;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "cityId", source = "city.id")
    @Mapping(target = "cityName", source = "city.name")
    CustomerDto toDto(Customer customer);

    @Mapping(target = "cityName", source = "city.name")
    CustomerSummaryDto toSummary(Customer customer);

    /** Copies editable fields; id, city, version and audit columns are handled by the service. */
    @Mapping(target = "city", ignore = true)
    void update(CustomerRequest request, @MappingTarget Customer customer);
}
