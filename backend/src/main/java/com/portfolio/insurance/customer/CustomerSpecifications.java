package com.portfolio.insurance.customer;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    /**
     * @param query free text matched against name, company title, TCKN/VKN, e-mail and phone
     */
    static Specification<Customer> matching(String query, CustomerType type, Long cityId) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query)) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern),
                        cb.like(cb.lower(root.get("companyName")), pattern),
                        cb.like(root.get("nationalId"), pattern),
                        cb.like(root.get("taxNumber"), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(root.get("phone"), pattern)));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("customerType"), type));
            }
            if (cityId != null) {
                predicates.add(cb.equal(root.get("city").get("id"), cityId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
