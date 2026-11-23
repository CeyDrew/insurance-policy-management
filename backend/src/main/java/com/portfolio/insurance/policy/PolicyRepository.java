package com.portfolio.insurance.policy;

import com.portfolio.insurance.reference.ProductCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {

    @Query(value = "SELECT policy_seq.NEXTVAL FROM dual", nativeQuery = true)
    Long nextId();

    @Override
    @EntityGraph(attributePaths = {"product", "customer", "vehicle", "property"})
    Page<Policy> findAll(Specification<Policy> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "customer", "customer.city", "vehicle", "property", "property.city",
            "agent", "renewedFrom"})
    Optional<Policy> findDetailedById(Long id);

    /**
     * In-force policies of the same product on the same insured object (vehicle, property or, for health, the person)
     * that still run on {@code date}.
     */
    @Query("""
            select count(p) > 0 from Policy p
             where p.product.code = :productCode
               and p.status in :statuses
               and p.endDate > :date
               and ((:vehicleId is not null and p.vehicle.id = :vehicleId)
                 or (:propertyId is not null and p.property.id = :propertyId)
                 or (:vehicleId is null and :propertyId is null and p.customer.id = :customerId))
            """)
    boolean existsInForce(ProductCode productCode, Long customerId, Long vehicleId, Long propertyId,
                          Collection<PolicyStatus> statuses, LocalDate date);

    /** Previous terms on the same insured object, newest first (used to carry over the no-claims step). */
    @Query("""
            select p from Policy p
             where p.product.code = :productCode
               and p.status <> com.portfolio.insurance.policy.PolicyStatus.QUOTE
               and ((:vehicleId is not null and p.vehicle.id = :vehicleId)
                 or (:propertyId is not null and p.property.id = :propertyId)
                 or (:vehicleId is null and :propertyId is null and p.customer.id = :customerId))
             order by p.endDate desc
            """)
    List<Policy> findPreviousTerms(ProductCode productCode, Long customerId, Long vehicleId, Long propertyId,
                                   Pageable pageable);
}
