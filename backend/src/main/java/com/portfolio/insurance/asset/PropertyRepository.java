package com.portfolio.insurance.asset;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    @EntityGraph(attributePaths = "city")
    List<Property> findByCustomerIdOrderByIdAsc(Long customerId);
}
