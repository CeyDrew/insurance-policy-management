package com.portfolio.insurance.policy;

import com.portfolio.insurance.asset.Property;
import com.portfolio.insurance.asset.Vehicle;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.persistence.BaseEntity;
import com.portfolio.insurance.customer.Customer;
import com.portfolio.insurance.premium.PremiumResult;
import com.portfolio.insurance.reference.Product;
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
import java.util.EnumSet;
import java.util.Set;

/**
 * A policy and its quote: a quote is a policy in {@link PolicyStatus#QUOTE} status, so issuing it keeps
 * the same number and history. State changes go through the methods below, which enforce the allowed transitions.
 */
@Getter
@Entity
@Table(name = "policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy extends BaseEntity {

    private static final Set<PolicyStatus> CANCELLABLE = EnumSet.of(PolicyStatus.ACTIVE, PolicyStatus.SUSPENDED);

    /**
     * Assigned from policy_seq before persisting (see {@link PolicyRepository#nextId()}), because the policy number
     * is derived from the id and Hibernate captures INSERT values at persist time.
     */
    @Id
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true, length = 30)
    private String policyNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", updatable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", updatable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false, updatable = false)
    private AppUser agent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "ncd_step", nullable = false)
    private Integer ncdStep;

    @Column(name = "net_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal netPremium;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "gross_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossPremium;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;

    @Column(name = "quote_valid_until")
    private LocalDate quoteValidUntil;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renewed_from_policy_id", updatable = false)
    private Policy renewedFrom;

    static Policy quote(Long id, Product product, Customer customer, Vehicle vehicle, Property property, AppUser agent,
                        LocalDate startDate, int ncdStep, int installmentCount, PremiumResult premium,
                        LocalDate quoteValidUntil) {
        Policy policy = new Policy();
        policy.id = id;
        policy.product = product;
        policy.customer = customer;
        policy.vehicle = vehicle;
        policy.property = property;
        policy.agent = agent;
        policy.status = PolicyStatus.QUOTE;
        policy.startDate = startDate;
        policy.endDate = startDate.plusMonths(product.getTermMonths());
        policy.ncdStep = ncdStep;
        policy.installmentCount = installmentCount;
        policy.netPremium = premium.netPremium();
        policy.taxAmount = premium.taxAmount();
        policy.grossPremium = premium.grossPremium();
        policy.quoteValidUntil = quoteValidUntil;
        policy.policyNumber = "%s-%d-%06d".formatted(product.getCode().policyPrefix(), startDate.getYear(), id);
        return policy;
    }

    void issue(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (status != PolicyStatus.QUOTE) {
            throw new BusinessException("POLICY_NOT_QUOTE", "Sadece teklif durumundaki kayıtlar poliçeleştirilebilir.");
        }
        if (quoteValidUntil != null && quoteValidUntil.isBefore(today)) {
            throw new BusinessException("QUOTE_EXPIRED", "Teklifin geçerlilik süresi dolmuş. Yeni teklif alın.");
        }
        if (startDate.isBefore(today)) {
            throw new BusinessException("QUOTE_START_DATE_PASSED",
                    "Teklifin başlangıç tarihi geçmiş. Yeni teklif alın.");
        }
        status = PolicyStatus.ACTIVE;
        issuedAt = now;
    }

    void cancel(String reason, LocalDateTime now, BigDecimal refund) {
        if (!CANCELLABLE.contains(status)) {
            throw new BusinessException("POLICY_NOT_CANCELLABLE", "Sadece yürürlükteki veya askıdaki poliçeler iptal edilebilir.");
        }
        status = PolicyStatus.CANCELLED;
        cancelledAt = now;
        cancelReason = reason;
        refundAmount = refund;
    }
}
