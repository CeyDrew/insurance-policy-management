package com.portfolio.insurance.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    List<Installment> findByPolicyIdOrderByInstallmentNoAsc(Long policyId);
}
