package com.portfolio.insurance.claim;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    @Query(value = "SELECT claim_seq.NEXTVAL FROM dual", nativeQuery = true)
    Long nextId();

    @Override
    @EntityGraph(attributePaths = {"policy", "policy.product", "policy.customer", "handler"})
    Page<Claim> findAll(Specification<Claim> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"policy", "policy.product", "policy.customer", "policy.vehicle",
            "policy.property", "policy.property.city", "incidentCity", "handler"})
    Optional<Claim> findDetailedById(Long id);

    @Query("""
            select count(c) > 0 from Claim c
             where c.policy.id = :policyId and c.incidentDate = :incidentDate
               and lower(c.description) = lower(:description)
            """)
    boolean existsDuplicate(Long policyId, LocalDate incidentDate, String description);
}
