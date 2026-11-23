package com.portfolio.insurance.asset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Customer assets", description = "Vehicles and properties owned by a customer")
@RestController
@RequestMapping("/api/customers/{customerId}")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @Operation(summary = "List the customer's vehicles")
    @GetMapping("/vehicles")
    public List<VehicleDto> vehicles(@PathVariable Long customerId) {
        return assetService.findVehicles(customerId);
    }

    @Operation(summary = "Add a vehicle to the customer")
    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public VehicleDto addVehicle(@PathVariable Long customerId, @Valid @RequestBody VehicleRequest request) {
        return assetService.addVehicle(customerId, request);
    }

    @Operation(summary = "List the customer's properties")
    @GetMapping("/properties")
    public List<PropertyDto> properties(@PathVariable Long customerId) {
        return assetService.findProperties(customerId);
    }

    @Operation(summary = "Add a property to the customer")
    @PostMapping("/properties")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public PropertyDto addProperty(@PathVariable Long customerId, @Valid @RequestBody PropertyRequest request) {
        return assetService.addProperty(customerId, request);
    }
}
