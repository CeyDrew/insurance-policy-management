package com.portfolio.insurance.asset;

import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import com.portfolio.insurance.customer.Customer;
import com.portfolio.insurance.customer.CustomerService;
import com.portfolio.insurance.reference.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final VehicleRepository vehicleRepository;
    private final PropertyRepository propertyRepository;
    private final CustomerService customerService;
    private final ReferenceService referenceService;
    private final AssetMapper assetMapper;

    public List<VehicleDto> findVehicles(Long customerId) {
        customerService.getCustomer(customerId);
        return assetMapper.toVehicleDtos(vehicleRepository.findByCustomerIdOrderByIdAsc(customerId));
    }

    public List<PropertyDto> findProperties(Long customerId) {
        customerService.getCustomer(customerId);
        return assetMapper.toPropertyDtos(propertyRepository.findByCustomerIdOrderByIdAsc(customerId));
    }

    @Transactional
    public VehicleDto addVehicle(Long customerId, VehicleRequest request) {
        Customer customer = customerService.getCustomer(customerId);
        Vehicle vehicle = assetMapper.toEntity(request);
        vehicle.setPlateNumber(normalizePlate(request.plateNumber()));
        vehicle.setChassisNumber(request.chassisNumber().toUpperCase(Locale.ROOT));
        vehicle.setCustomer(customer);

        if (vehicleRepository.existsByPlateNumber(vehicle.getPlateNumber())) {
            throw new BusinessException("VEHICLE_DUPLICATE_PLATE", "Bu plakayla kayıtlı bir araç zaten var.");
        }
        if (vehicleRepository.existsByChassisNumber(vehicle.getChassisNumber())) {
            throw new BusinessException("VEHICLE_DUPLICATE_CHASSIS", "Bu şasi numarasıyla kayıtlı bir araç zaten var.");
        }
        return assetMapper.toDto(vehicleRepository.save(vehicle));
    }

    @Transactional
    public PropertyDto addProperty(Long customerId, PropertyRequest request) {
        Property property = assetMapper.toEntity(request);
        property.setCustomer(customerService.getCustomer(customerId));
        property.setCity(referenceService.getCity(request.cityId()));
        return assetMapper.toDto(propertyRepository.save(property));
    }

    /** Entity lookups for the policy feature. */
    public Vehicle getVehicle(Long id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Araç", id));
    }

    public Property getProperty(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Konut", id));
    }

    /** "34abc123" / "34  ABC 123" -> "34 ABC 123". */
    static String normalizePlate(String plate) {
        String compact = plate.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return compact.replaceFirst("^(\\d{2})([A-Z]{1,3})(\\d{2,4})$", "$1 $2 $3");
    }
}
