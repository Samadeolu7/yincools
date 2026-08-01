package com.java.yincools.domain;

import com.java.yincools.domain.model.Vehicle;
import com.java.yincools.persistence.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepo;

    public Vehicle findOrCreate(Long customerId, String description, String plateNumber) {
        return vehicleRepo.findByCustomerIdAndDescription(customerId, description)
                .orElseGet(() -> {
                    Vehicle vehicle = new Vehicle();
                    vehicle.setCustomerId(customerId);
                    vehicle.setDescription(description);
                    vehicle.setPlateNumber(plateNumber);
                    return vehicleRepo.save(vehicle);
                });
    }

    public List<Vehicle> vehiclesFor(Long customerId) {
        return vehicleRepo.findByCustomerId(customerId);
    }

    public Optional<Vehicle> findById(Long id) {
        return vehicleRepo.findById(id);
    }

    /** Distinct vehicle descriptions typed before -- merged client-side with a static seed list. */
    public List<String> suggestionList() {
        return vehicleRepo.findDistinctDescriptions().stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    /** The vehicle's description if it has a persisted Vehicle, else the free-text fallback note, else null. */
    public String labelFor(Long vehicleId, String fallbackNote) {
        if (vehicleId != null) {
            return findById(vehicleId).map(Vehicle::getDescription).orElse(null);
        }
        return fallbackNote;
    }
}
