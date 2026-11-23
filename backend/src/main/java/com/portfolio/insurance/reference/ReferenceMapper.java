package com.portfolio.insurance.reference;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReferenceMapper {

    CityDto toDto(City city);

    List<CityDto> toCityDtos(List<City> cities);

    ProductDto toDto(Product product);

    List<ProductDto> toProductDtos(List<Product> products);
}
