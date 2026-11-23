package com.portfolio.insurance.policy;

import com.portfolio.insurance.asset.AssetService;
import com.portfolio.insurance.asset.Property;
import com.portfolio.insurance.asset.Vehicle;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import com.portfolio.insurance.common.web.PageResponse;
import com.portfolio.insurance.customer.Customer;
import com.portfolio.insurance.customer.CustomerService;
import com.portfolio.insurance.premium.PremiumEngine;
import com.portfolio.insurance.premium.PremiumRequest;
import com.portfolio.insurance.premium.PremiumResult;
import com.portfolio.insurance.reference.Product;
import com.portfolio.insurance.reference.ProductCode;
import com.portfolio.insurance.reference.ReferenceService;
import com.portfolio.insurance.user.AppUser;
import com.portfolio.insurance.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    /** Quotes can be requested for policies starting within this many days. */
    static final int MAX_START_DAYS_AHEAD = 60;
    static final int QUOTE_VALIDITY_DAYS = 15;
    static final int ENTRY_NCD_STEP = 4;

    private final PolicyRepository policyRepository;
    private final InstallmentRepository installmentRepository;
    private final PolicyMapper policyMapper;
    private final CustomerService customerService;
    private final AssetService assetService;
    private final ReferenceService referenceService;
    private final AppUserRepository userRepository;
    private final PremiumEngine premiumEngine;
    private final Clock clock;

    public PageResponse<PolicySummaryDto> search(String query, PolicyStatus status, ProductCode productCode,
                                                 Long customerId, Pageable pageable) {
        return PageResponse.from(policyRepository
                .findAll(PolicySpecifications.matching(query, status, productCode, customerId), pageable)
                .map(policyMapper::toSummary));
    }

    public PolicyDto findById(Long id) {
        return toDto(getPolicy(id));
    }

    /** Validates the request, prices it with the premium engine and stores it as a QUOTE. */
    @Transactional
    public PolicyDto createQuote(QuoteRequest request, String username) {
        LocalDate today = LocalDate.now(clock);
        Product product = referenceService.getProduct(request.productId());
        Customer customer = customerService.getCustomer(request.customerId());

        if (!product.isActive()) {
            throw new BusinessException("PRODUCT_INACTIVE", "Bu ürün satışa kapalı.");
        }
        if (request.startDate().isAfter(today.plusDays(MAX_START_DAYS_AHEAD))) {
            throw new BusinessException("START_DATE_TOO_FAR",
                    "Başlangıç tarihi en fazla %d gün sonrası olabilir.".formatted(MAX_START_DAYS_AHEAD));
        }
        if (request.installmentCount() > product.getMaxInstallments()) {
            throw new BusinessException("TOO_MANY_INSTALLMENTS",
                    "Bu üründe en fazla %d taksit yapılabilir.".formatted(product.getMaxInstallments()));
        }

        InsuredObject insured = resolveInsuredObject(product, customer, request);
        if (policyRepository.existsInForce(product.getCode(), customer.getId(), insured.vehicleId(),
                insured.propertyId(), EnumSet.of(PolicyStatus.ACTIVE, PolicyStatus.SUSPENDED), request.startDate())) {
            throw new BusinessException("POLICY_ALREADY_IN_FORCE",
                    "Bu sigortalı için başlangıç tarihinde yürürlükte bir poliçe var.");
        }

        int ncdStep = previousNcdStep(product.getCode(), customer.getId(), insured);
        PremiumResult premium = premiumEngine.calculatePremium(new PremiumRequest(product.getCode(), customer.getId(),
                insured.vehicleId(), insured.propertyId(), ncdStep, request.startDate()));

        Policy policy = Policy.quote(policyRepository.nextId(), product, customer, insured.vehicle(),
                insured.property(), currentUser(username), request.startDate(), ncdStep, request.installmentCount(),
                premium, today.plusDays(QUOTE_VALIDITY_DAYS));
        policyRepository.saveAndFlush(policy);
        return toDto(policy);
    }

    /** QUOTE -> ACTIVE and creation of the installment plan. */
    @Transactional
    public PolicyDto issue(Long id) {
        Policy policy = getPolicy(id);
        policy.issue(LocalDateTime.now(clock));
        policyRepository.flush();
        premiumEngine.generateInstallments(policy.getId());
        return toDto(policy);
    }

    /** ACTIVE/SUSPENDED -> CANCELLED with a day-based refund; open installments are cancelled. */
    @Transactional
    public PolicyDto cancel(Long id, CancelPolicyRequest request) {
        Policy policy = getPolicy(id);
        LocalDateTime now = LocalDateTime.now(clock);
        BigDecimal refund = premiumEngine.calculateRefund(policy.getId(), now.toLocalDate());
        policy.cancel(request.reason().trim(), now, refund);
        policyRepository.flush();
        installmentRepository.cancelOpenInstallments(policy.getId());
        return toDto(getPolicy(id));
    }

    private Policy getPolicy(Long id) {
        return policyRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Poliçe", id));
    }

    private PolicyDto toDto(Policy policy) {
        return policyMapper.toDto(policy, installmentRepository.findByPolicyIdOrderByInstallmentNoAsc(policy.getId()));
    }

    private AppUser currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", username));
    }

    private InsuredObject resolveInsuredObject(Product product, Customer customer, QuoteRequest request) {
        return switch (product.getAssetType()) {
            case VEHICLE -> {
                if (request.vehicleId() == null) {
                    throw new BusinessException("VEHICLE_REQUIRED", "Bu ürün için araç seçilmelidir.");
                }
                Vehicle vehicle = assetService.getVehicle(request.vehicleId());
                requireOwner(customer, vehicle.getCustomer(), "Seçilen araç bu müşteriye ait değil.");
                yield new InsuredObject(vehicle, null);
            }
            case PROPERTY -> {
                if (request.propertyId() == null) {
                    throw new BusinessException("PROPERTY_REQUIRED", "Bu ürün için konut seçilmelidir.");
                }
                Property property = assetService.getProperty(request.propertyId());
                requireOwner(customer, property.getCustomer(), "Seçilen konut bu müşteriye ait değil.");
                yield new InsuredObject(null, property);
            }
            case PERSON -> {
                if (!customer.isIndividual()) {
                    throw new BusinessException("INDIVIDUAL_REQUIRED", "Sağlık sigortası sadece bireysel müşterilere yapılır.");
                }
                yield new InsuredObject(null, null);
            }
        };
    }

    private static void requireOwner(Customer customer, Customer owner, String message) {
        if (!Objects.equals(customer.getId(), owner.getId())) {
            throw new BusinessException("ASSET_OWNER_MISMATCH", message);
        }
    }

    /** No-claims step carries over from the previous term on the same object; new business starts at step 4. */
    private int previousNcdStep(ProductCode productCode, Long customerId, InsuredObject insured) {
        return policyRepository.findPreviousTerms(productCode, customerId, insured.vehicleId(), insured.propertyId(),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(Policy::getNcdStep)
                .orElse(ENTRY_NCD_STEP);
    }

    private record InsuredObject(Vehicle vehicle, Property property) {

        Long vehicleId() {
            return vehicle == null ? null : vehicle.getId();
        }

        Long propertyId() {
            return property == null ? null : property.getId();
        }
    }
}
