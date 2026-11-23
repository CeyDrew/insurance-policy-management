package com.portfolio.insurance.asset;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    @Mapping(target = "customerId", source = "customer.id")
    VehicleDto toDto(Vehicle vehicle);

    List<VehicleDto> toVehicleDtos(List<Vehicle> vehicles);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "cityId", source = "city.id")
    @Mapping(target = "cityName", source = "city.name")
    PropertyDto toDto(Property property);

    List<PropertyDto> toPropertyDtos(List<Property> properties);

    @Mapping(target = "customer", ignore = true)
    Vehicle toEntity(VehicleRequest request);

    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "city", ignore = true)
    Property toEntity(PropertyRequest request);
}
