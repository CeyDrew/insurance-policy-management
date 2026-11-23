package com.portfolio.insurance.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    @Override
    @EntityGraph(attributePaths = "city")
    Page<Customer> findAll(Specification<Customer> spec, Pageable pageable);

    @EntityGraph(attributePaths = "city")
    Optional<Customer> findWithCityById(Long id);

    @Query("select count(c) > 0 from Customer c where c.nationalId = :nationalId and (:excludeId is null or c.id <> :excludeId)")
    boolean existsByNationalId(String nationalId, Long excludeId);

    @Query("select count(c) > 0 from Customer c where c.taxNumber = :taxNumber and (:excludeId is null or c.id <> :excludeId)")
    boolean existsByTaxNumber(String taxNumber, Long excludeId);

    @Query("select count(p) > 0 from Policy p where p.customer.id = :customerId")
    boolean hasPolicies(Long customerId);

    @Modifying
    @Query("delete from Vehicle v where v.customer.id = :customerId")
    void deleteVehicles(Long customerId);

    @Modifying
    @Query("delete from Property p where p.customer.id = :customerId")
    void deleteProperties(Long customerId);
}
