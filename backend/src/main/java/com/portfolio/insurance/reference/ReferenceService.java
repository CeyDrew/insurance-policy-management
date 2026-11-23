package com.portfolio.insurance.reference;

import com.portfolio.insurance.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceService {

    private final CityRepository cityRepository;
    private final ProductRepository productRepository;
    private final ReferenceMapper mapper;

    public List<CityDto> findCities() {
        return mapper.toCityDtos(cityRepository.findAllByOrderByPlateCodeAsc());
    }

    public List<ProductDto> findActiveProducts() {
        return mapper.toProductDtos(productRepository.findAllByActiveTrueOrderByIdAsc());
    }

    /** Entity lookup for other features (customer, asset, policy services). */
    public City getCity(Long id) {
        return cityRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("İl", id));
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ürün", id));
    }
}
