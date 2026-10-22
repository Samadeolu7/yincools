package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.Vehicle;
import com.java.yincools.persistence.CustomerRepository;
import com.java.yincools.persistence.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs automatically on every app boot -- there's no manual script to run
 * or console to open, the next deploy is the "run". Safe to run every time
 * because it's idempotent: it only ever rewrites a phone/plate that isn't
 * already in normalized form (see CustomerService.normalizePhone,
 * VehicleService.normalizePlate), so once everything's been normalized
 * once, every later boot finds nothing left to do and is a no-op.
 *
 * Deliberately does NOT merge customers that normalize to the same phone.
 * Doing that would mean reassigning LedgerEntry.customerId on existing
 * rows -- and LedgerRepository exists specifically so nothing ever updates
 * a ledger row after insert (see LedgerService's class comment). That
 * guarantee is about more than money amounts: once a historical row's
 * attribution can be silently rewritten, "why does this say X" stops
 * being answerable from the rows alone. So a phone collision is only
 * logged, the same way the (future) nightly balance-check job logs a
 * mismatch instead of silently fixing it -- it's a real thing for a human
 * to look at, not something safe to resolve automatically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartupDataMigration implements ApplicationRunner {

    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        normalizeCustomerPhones();
        normalizeVehiclePlates();
        reportPossibleDuplicateCustomers();
    }

    private void normalizeCustomerPhones() {
        int changed = 0;
        for (Customer customer : customerRepo.findAll()) {
            String normalized = CustomerService.normalizePhone(customer.getPhone());
            if (normalized != null && !normalized.equals(customer.getPhone())) {
                String before = customer.getPhone();
                customer.setPhone(normalized);
                customerRepo.save(customer);
                changed++;
                log.info("Migration: normalized customer {} phone \"{}\" -> \"{}\"", customer.getId(), before, normalized);
            }
        }
        if (changed > 0) {
            log.info("Migration: normalized phone format for {} customer(s)", changed);
        }
    }

    private void normalizeVehiclePlates() {
        int changed = 0;
        for (Vehicle vehicle : vehicleRepo.findAll()) {
            String normalized = VehicleService.normalizePlate(vehicle.getPlateNumber());
            if (normalized != null && !normalized.equals(vehicle.getPlateNumber())) {
                String before = vehicle.getPlateNumber();
                vehicle.setPlateNumber(normalized);
                vehicleRepo.save(vehicle);
                changed++;
                log.info("Migration: normalized vehicle {} plate \"{}\" -> \"{}\"", vehicle.getId(), before, normalized);
            }
        }
        if (changed > 0) {
            log.info("Migration: normalized plate format for {} vehicle(s)", changed);
        }
    }

    /**
     * Surfaces customers that would now resolve as one (same normalized
     * phone) but are still two separate rows -- almost always a customer
     * typed in twice before this normalization existed. Left for a human
     * to actually merge (see this class's comment) rather than done here.
     */
    private void reportPossibleDuplicateCustomers() {
        Map<String, List<Customer>> byPhone = customerRepo.findAll().stream()
                .filter(c -> StringUtils.hasText(c.getPhone()))
                .collect(Collectors.groupingBy(Customer::getPhone));

        byPhone.forEach((phone, customers) -> {
            if (customers.size() > 1) {
                List<Long> ids = customers.stream().map(Customer::getId).toList();
                log.warn("Migration: {} customer rows share phone {} (ids {}) -- likely the same person typed in "
                        + "twice before phone numbers were normalized. Not merged automatically; review by hand.",
                        customers.size(), phone, ids);
            }
        });
    }
}
