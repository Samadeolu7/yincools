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
        String normalizedPlate = normalizePlate(plateNumber);
        Optional<Vehicle> existing = vehicleRepo.findByCustomerIdAndDescription(customerId, description);
        if (existing.isPresent()) {
            Vehicle vehicle = existing.get();
            if (normalizedPlate != null && !normalizedPlate.equals(vehicle.getPlateNumber())) {
                vehicle.setPlateNumber(normalizedPlate);
                return vehicleRepo.save(vehicle);
            }
            return vehicle;
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomerId(customerId);
        vehicle.setDescription(description);
        vehicle.setPlateNumber(normalizedPlate);
        return vehicleRepo.save(vehicle);
    }

    /**
     * With no customer there's no Vehicle row to hold the plate, so it
     * rides along in the free-text note instead (JobService/QuoteService's
     * walk-in path).
     */
    public static String noteWithPlate(String description, String plate) {
        String normalizedPlate = normalizePlate(plate);
        if (normalizedPlate == null) {
            return description;
        }
        return StringUtils.hasText(description) ? description + " (" + normalizedPlate + ")" : normalizedPlate;
    }

    /**
     * Uppercased, stripped of everything but letters/digits, so "abc 123"
     * and "ABC-123" are recognized as the same plate instead of silently
     * creating two different vehicles (or two different walk-in notes).
     */
    private static String normalizePlate(String plate) {
        if (!StringUtils.hasText(plate)) {
            return null;
        }
        String normalized = plate.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return normalized.isEmpty() ? null : normalized;
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
