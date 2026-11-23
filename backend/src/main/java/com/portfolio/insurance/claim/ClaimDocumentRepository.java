package com.portfolio.insurance.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {

    List<ClaimDocument> findByClaimIdOrderByIdAsc(Long claimId);

    Optional<ClaimDocument> findByIdAndClaimId(Long id, Long claimId);
}
