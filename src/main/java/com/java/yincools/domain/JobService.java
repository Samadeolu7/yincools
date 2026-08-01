package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.Vehicle;
import com.java.yincools.persistence.CustomerRepository;
import com.java.yincools.persistence.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dad-facing verbs. This is the only vocabulary the UI ever speaks --
 * "ledger" and "correction" stay entirely inside LedgerService.
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepo;
    private final CustomerRepository customerRepo;
    private final LedgerService ledgerService;
    private final VehicleService vehicleService;

    /**
     * vehicleId picks an existing vehicle for the resolved customer;
     * newVehicleDescription/newVehiclePlateNumber create one instead (used
     * once, then it's an existing pick next time). Neither is used for a
     * walk-in with no customer -- vehicleNote is the whole vehicle field
     * there, free text, never becomes a Vehicle row.
     */
    @Transactional
    public Job createJob(String customerName, String customerPhone,
                          Long vehicleId, String newVehicleDescription, String newVehiclePlateNumber,
                          String vehicleNote,
                          String workType, BigDecimal charge, BigDecimal partsCost, BigDecimal paid) {
        Long customerId = resolveCustomer(customerName, customerPhone);

        Long resolvedVehicleId = null;
        String resolvedVehicleNote = null;

        if (customerId != null) {
            if (vehicleId != null) {
                resolvedVehicleId = vehicleId;
            } else if (StringUtils.hasText(newVehicleDescription)) {
                resolvedVehicleId = vehicleService.findOrCreate(customerId, newVehicleDescription, newVehiclePlateNumber).getId();
            }
        } else {
            resolvedVehicleNote = vehicleNote;
        }

        Job job = new Job();
        job.setCustomerId(customerId);
        job.setVehicleId(resolvedVehicleId);
        job.setVehicleNote(resolvedVehicleNote);
        job.setWorkType(workType);
        job.setDate(LocalDate.now());
        job.setCachedCharge(BigDecimal.ZERO);
        job.setCachedPaid(BigDecimal.ZERO);
        job.setCachedBalance(BigDecimal.ZERO);
        job = jobRepo.save(job);

        ledgerService.record(EntryType.CHARGE, job.getId(), resolvedVehicleId, customerId, nullToZero(charge), null);
        ledgerService.record(EntryType.PARTS_COST, job.getId(), resolvedVehicleId, customerId, nullToZero(partsCost), null);
        ledgerService.record(EntryType.PAYMENT, job.getId(), resolvedVehicleId, customerId, nullToZero(paid), null);

        return jobRepo.findById(job.getId()).orElseThrow();
    }

    @Transactional
    public void editJob(Long jobId, BigDecimal newCharge, BigDecimal newPartsCost) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
        ledgerService.adjust(EntryType.CHARGE, jobId, job.getVehicleId(), job.getCustomerId(), newCharge, null);
        ledgerService.adjust(EntryType.PARTS_COST, jobId, job.getVehicleId(), job.getCustomerId(), newPartsCost, null);
    }

    @Transactional
    public void recordPayment(Long jobId, BigDecimal amountPaidTotal) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
        ledgerService.adjust(EntryType.PAYMENT, jobId, job.getVehicleId(), job.getCustomerId(), amountPaidTotal, null);
    }

    @Transactional
    public void voidJob(Long jobId) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
        ledgerService.adjust(EntryType.CHARGE, jobId, job.getVehicleId(), job.getCustomerId(), BigDecimal.ZERO, "voided");
        ledgerService.adjust(EntryType.PAYMENT, jobId, job.getVehicleId(), job.getCustomerId(), BigDecimal.ZERO, "voided");
        ledgerService.adjust(EntryType.PARTS_COST, jobId, job.getVehicleId(), job.getCustomerId(), BigDecimal.ZERO, "voided");
    }

    /** Logs a parts cost tied to a customer's visit but not to any one car (§7's "shared visit" case). */
    public void recordSharedPartsCost(Long customerId, BigDecimal amount, String note) {
        ledgerService.recordSharedCost(customerId, amount, note);
    }

    public Job get(Long jobId) {
        return jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
    }

    public Optional<Customer> customerFor(Job job) {
        if (job.getCustomerId() == null) {
            return Optional.empty();
        }
        return customerRepo.findById(job.getCustomerId());
    }

    public Optional<Customer> findCustomerByPhone(String phone) {
        return customerRepo.findByPhone(phone);
    }

    public List<Vehicle> vehiclesFor(Long customerId) {
        return vehicleService.vehiclesFor(customerId);
    }

    /** The vehicle's description if it has a persisted Vehicle, else the free-text walk-in note, else null. */
    public String vehicleLabelFor(Job job) {
        if (job.getVehicleId() != null) {
            return vehicleService.findById(job.getVehicleId()).map(Vehicle::getDescription).orElse(null);
        }
        return job.getVehicleNote();
    }

    public Optional<Job> lastJob() {
        return jobRepo.findTopByOrderByIdDesc();
    }

    /** Parts cost isn't cached on Job -- it's a small, infrequently-read sum, so it's read straight from the ledger. */
    public BigDecimal partsCostFor(Long jobId) {
        return ledgerService.netFor(EntryType.PARTS_COST, jobId);
    }

    public List<Customer> recentCustomers() {
        return jobRepo.findTop20ByOrderByIdDesc().stream()
                .map(Job::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .map(id -> customerRepo.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private Long resolveCustomer(String name, String phone) {
        boolean hasName = StringUtils.hasText(name);
        boolean hasPhone = StringUtils.hasText(phone);
        if (!hasName && !hasPhone) {
            return null;
        }

        if (hasPhone) {
            Optional<Customer> existing = customerRepo.findByPhone(phone);
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }

        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        return customerRepo.save(customer).getId();
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
