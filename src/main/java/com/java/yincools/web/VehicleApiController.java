package com.java.yincools.web;

import com.java.yincools.domain.JobService;
import com.java.yincools.domain.VehicleService;
import com.java.yincools.domain.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Small JSON lookups the New Job page's JS uses to auto-select a
 * single-vehicle customer's car, offer a picker for a fleet, or suggest
 * autocomplete text -- all read-only, no CSRF token needed.
 */
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleApiController {

    private final JobService jobService;
    private final VehicleService vehicleService;

    @GetMapping
    public List<VehicleDto> byPhone(@RequestParam String phone) {
        return jobService.findCustomerByPhone(phone)
                .map(customer -> vehicleService.vehiclesFor(customer.getId()))
                .orElseGet(List::of)
                .stream()
                .map(v -> new VehicleDto(v.getId(), v.getDescription(), v.getPlateNumber()))
                .toList();
    }

    @GetMapping("/suggestions")
    public List<String> suggestions() {
        return vehicleService.suggestionList();
    }

    public record VehicleDto(Long id, String description, String plateNumber) {
    }
}
