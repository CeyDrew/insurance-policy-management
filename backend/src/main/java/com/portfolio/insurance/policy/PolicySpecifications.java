package com.portfolio.insurance.policy;

import com.portfolio.insurance.asset.Vehicle;
import com.portfolio.insurance.customer.Customer;
import com.portfolio.insurance.reference.ProductCode;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PolicySpecifications {

    private PolicySpecifications() {
    }

    /**
     * @param query free text matched against policy number, customer name/title and plate number
     */
    static Specification<Policy> matching(String query, PolicyStatus status, ProductCode productCode, Long customerId) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query)) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                Join<Policy, Customer> customer = root.join("customer");
                Join<Policy, Vehicle> vehicle = root.join("vehicle", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("policyNumber")), pattern),
                        cb.like(cb.lower(cb.concat(cb.concat(customer.get("firstName"), " "), customer.get("lastName"))), pattern),
                        cb.like(cb.lower(customer.get("companyName")), pattern),
                        cb.like(cb.lower(cb.function("REPLACE", String.class, vehicle.get("plateNumber"), cb.literal(" "),
                                cb.literal(""))), pattern.replace(" ", ""))));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (productCode != null) {
                predicates.add(cb.equal(root.get("product").get("code"), productCode));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
