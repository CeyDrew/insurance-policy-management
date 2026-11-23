package com.portfolio.insurance.customer;

import com.portfolio.insurance.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customers", description = "Individual and corporate customers")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "Search customers (paged)")
    @GetMapping
    public PageResponse<CustomerSummaryDto> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) CustomerType type,
            @RequestParam(required = false) Long cityId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.search(query, type, cityId, pageable);
    }

    @Operation(summary = "Customer detail")
    @GetMapping("/{id}")
    public CustomerDto findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @Operation(summary = "Create a customer")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public CustomerDto create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @Operation(summary = "Update a customer")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public CustomerDto update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @Operation(summary = "Delete a customer without policies")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }
}
