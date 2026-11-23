package com.portfolio.insurance.reference;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Reference data", description = "Cities and insurance products")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceService referenceService;

    @Operation(summary = "List the 81 provinces")
    @GetMapping("/cities")
    public List<CityDto> cities() {
        return referenceService.findCities();
    }

    @Operation(summary = "List active products")
    @GetMapping("/products")
    public List<ProductDto> products() {
        return referenceService.findActiveProducts();
    }
}
