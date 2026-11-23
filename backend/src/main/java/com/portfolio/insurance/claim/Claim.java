package com.portfolio.insurance.claim;

import com.portfolio.insurance.common.persistence.BaseEntity;
import com.portfolio.insurance.policy.Policy;
import com.portfolio.insurance.reference.City;
import com.portfolio.insurance.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A claim (hasar dosyası). Created by the application in REPORTED status; every later status change is made by
 * PKG_CLAIM (see {@link ClaimLifecycle}), which owns the transition rules.
 */
@Getter
@Entity
@Table(name = "claim")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Claim extends BaseEntity {

    /** Assigned from claim_seq before persisting, because the claim number is derived from it. */
    @Id
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 30)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, updatable = false)
    private Policy policy;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_city_id")
    private City incidentCity;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "estimated_amount", precision = 15, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handler_id")
    private AppUser handler;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    static Claim report(Long id, Policy policy, LocalDate incidentDate, LocalDateTime reportedAt, City incidentCity,
                        String description, BigDecimal estimatedAmount) {
        Claim claim = new Claim();
        claim.id = id;
        claim.claimNumber = "HSR-%d-%06d".formatted(reportedAt.getYear(), id);
        claim.policy = policy;
        claim.incidentDate = incidentDate;
        claim.reportedAt = reportedAt;
        claim.incidentCity = incidentCity;
        claim.description = description;
        claim.estimatedAmount = estimatedAmount;
        claim.status = ClaimStatus.REPORTED;
        return claim;
    }
}
