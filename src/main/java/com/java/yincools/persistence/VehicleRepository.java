package com.java.yincools.persistence;

import com.java.yincools.domain.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByCustomerId(Long customerId);

    Optional<Vehicle> findByCustomerIdAndDescription(Long customerId, String description);

    @Query("select distinct v.description from Vehicle v order by v.description")
    List<String> findDistinctDescriptions();
}
