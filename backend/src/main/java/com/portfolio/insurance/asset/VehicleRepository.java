package com.portfolio.insurance.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByCustomerIdOrderByIdAsc(Long customerId);

    boolean existsByPlateNumber(String plateNumber);

    boolean existsByChassisNumber(String chassisNumber);
}
