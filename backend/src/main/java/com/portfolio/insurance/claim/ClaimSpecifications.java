package com.portfolio.insurance.claim;

import com.portfolio.insurance.customer.Customer;
import com.portfolio.insurance.policy.Policy;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    /**
     * @param query           free text matched against claim number, policy number and customer name/title
     * @param handlerUsername only claims assigned to this user
     */
    static Specification<Claim> matching(String query, ClaimStatus status, Long policyId, String handlerUsername) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query)) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                Join<Claim, Policy> policy = root.join("policy");
                Join<Policy, Customer> customer = policy.join("customer");
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("claimNumber")), pattern),
                        cb.like(cb.lower(policy.get("policyNumber")), pattern),
                        cb.like(cb.lower(cb.concat(cb.concat(customer.get("firstName"), " "), customer.get("lastName"))), pattern),
                        cb.like(cb.lower(customer.get("companyName")), pattern)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (policyId != null) {
                predicates.add(cb.equal(root.get("policy").get("id"), policyId));
            }
            if (handlerUsername != null) {
                predicates.add(cb.equal(root.join("handler").get("username"), handlerUsername));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
