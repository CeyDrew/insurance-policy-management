package com.portfolio.insurance.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    List<Installment> findByPolicyIdOrderByInstallmentNoAsc(Long policyId);

    /** Unpaid installments of a cancelled policy are no longer due. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Installment i set i.status = com.portfolio.insurance.policy.InstallmentStatus.CANCELLED,
                   i.version = i.version + 1
             where i.policy.id = :policyId
               and i.status in (com.portfolio.insurance.policy.InstallmentStatus.PENDING,
                                com.portfolio.insurance.policy.InstallmentStatus.OVERDUE)
            """)
    int cancelOpenInstallments(Long policyId);
}
