package com.portfolio.insurance.customer;

import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import com.portfolio.insurance.common.web.PageResponse;
import com.portfolio.insurance.reference.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final ReferenceService referenceService;

    public PageResponse<CustomerSummaryDto> search(String query, CustomerType type, Long cityId, Pageable pageable) {
        return PageResponse.from(customerRepository
                .findAll(CustomerSpecifications.matching(query, type, cityId), pageable)
                .map(customerMapper::toSummary));
    }

    public CustomerDto findById(Long id) {
        return customerMapper.toDto(getCustomer(id));
    }

    @Transactional
    public CustomerDto create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(request, customer);
        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerDto update(Long id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        if (request.version() != null && !Objects.equals(request.version(), customer.getVersion())) {
            throw new OptimisticLockingFailureException("Customer %d was modified concurrently".formatted(id));
        }
        apply(request, customer);
        return customerMapper.toDto(customerRepository.saveAndFlush(customer));
    }

    /** Removes a customer and their vehicles/properties; customers with policies are kept for history. */
    @Transactional
    public void delete(Long id) {
        Customer customer = getCustomer(id);
        if (customerRepository.hasPolicies(id)) {
            throw new BusinessException("CUSTOMER_HAS_POLICIES",
                    "Poliçesi veya teklifi bulunan müşteri silinemez.");
        }
        customerRepository.deleteVehicles(id);
        customerRepository.deleteProperties(id);
        customerRepository.delete(customer);
    }

    /** Entity lookup for other features (assets, policies). */
    public Customer getCustomer(Long id) {
        return customerRepository.findWithCityById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Müşteri", id));
    }

    private void apply(CustomerRequest request, Customer customer) {
        customerMapper.update(request, customer);
        customer.setCity(referenceService.getCity(request.cityId()));
        customer.clearFieldsOfOtherType();
        ensureUniqueIdentity(customer);
    }

    private void ensureUniqueIdentity(Customer customer) {
        boolean duplicate = customer.isIndividual()
                ? customerRepository.existsByNationalId(customer.getNationalId(), customer.getId())
                : customerRepository.existsByTaxNumber(customer.getTaxNumber(), customer.getId());
        if (duplicate) {
            throw new BusinessException("CUSTOMER_DUPLICATE_IDENTITY",
                    customer.isIndividual()
                            ? "Bu T.C. kimlik numarasıyla kayıtlı bir müşteri zaten var."
                            : "Bu vergi kimlik numarasıyla kayıtlı bir müşteri zaten var.");
        }
    }
}
