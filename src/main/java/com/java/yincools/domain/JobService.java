package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.Vehicle;
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
    private final CustomerService customerService;
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
                          String workType, BigDecimal charge, BigDecimal partsCost, String partsNote, BigDecimal paid) {
        return createJob(customerName, customerPhone, vehicleId, newVehicleDescription, newVehiclePlateNumber,
                vehicleNote, workType, charge, partsCost, partsNote, paid, null);
    }

    /** Same as createJob(), plus which supplier the parts cost came from (see LedgerEntry.partsSupplier). */
    @Transactional
    public Job createJob(String customerName, String customerPhone,
                          Long vehicleId, String newVehicleDescription, String newVehiclePlateNumber,
                          String vehicleNote,
                          String workType, BigDecimal charge, BigDecimal partsCost, String partsNote, BigDecimal paid,
                          String partsSupplier) {
        Long customerId = customerService.resolveOrCreate(customerName, customerPhone);

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

        return persistJob(customerId, resolvedVehicleId, resolvedVehicleNote, workType, charge, partsCost, partsNote, paid, partsSupplier);
    }

    /** For QuoteService.convertToJob -- customer/vehicle are already resolved on the quote, so skip re-resolving them. */
    @Transactional
    public Job createJobFromResolvedIdentity(Long customerId, Long vehicleId, String vehicleNote,
                                              String workType, BigDecimal charge, BigDecimal paid) {
        return persistJob(customerId, vehicleId, vehicleNote, workType, charge, BigDecimal.ZERO, null, paid, null);
    }

    /**
     * For the offline job-capture API: if a job with this clientId already
     * exists (the phone retried a submission whose first response never
     * arrived), return it untouched instead of resolving the customer/
     * vehicle and writing the ledger a second time. The shared-cost decision
     * has to live inside this same idempotency check, not in the caller --
     * otherwise a retry would skip creating a duplicate job but still log a
     * duplicate shared-cost entry, since recordSharedPartsCost is a plain
     * append with no idempotency of its own (see LedgerService.recordSharedCost).
     */
    @Transactional
    public Job createJobIdempotent(String clientId, String customerName, String customerPhone,
                                    Long vehicleId, String newVehicleDescription, String newVehiclePlateNumber,
                                    String vehicleNote, String workType, BigDecimal charge,
                                    BigDecimal partsCost, String partsNote, boolean partsCostIsShared, BigDecimal paid) {
        Optional<Job> existing = jobRepo.findByClientId(clientId);
        if (existing.isPresent()) {
            return existing.get();
        }

        boolean hasCustomer = StringUtils.hasText(customerName) || StringUtils.hasText(customerPhone);
        boolean logAsSharedCost = partsCostIsShared && hasCustomer
                && partsCost != null && partsCost.compareTo(BigDecimal.ZERO) != 0;

        Job job = createJob(customerName, customerPhone, vehicleId, newVehicleDescription, newVehiclePlateNumber,
                vehicleNote, workType, charge, logAsSharedCost ? BigDecimal.ZERO : partsCost, partsNote, paid);
        job.setClientId(clientId);
        job = jobRepo.save(job);

        if (logAsSharedCost) {
            recordSharedPartsCost(job.getCustomerId(), partsCost, partsNote);
        }

        return job;
    }

    private Job persistJob(Long customerId, Long vehicleId, String vehicleNote, String workType,
                            BigDecimal charge, BigDecimal partsCost, String partsNote, BigDecimal paid,
                            String partsSupplier) {
        Job job = new Job();
        job.setCustomerId(customerId);
        job.setVehicleId(vehicleId);
        job.setVehicleNote(vehicleNote);
        job.setWorkType(workType);
        job.setDate(LocalDate.now());
        job.setCachedCharge(BigDecimal.ZERO);
        job.setCachedPaid(BigDecimal.ZERO);
        job.setCachedBalance(BigDecimal.ZERO);
        job = jobRepo.save(job);

        ledgerService.record(EntryType.CHARGE, job.getId(), vehicleId, customerId, nullToZero(charge), null);
        ledgerService.record(EntryType.PARTS_COST, job.getId(), vehicleId, customerId, nullToZero(partsCost), partsNote, partsSupplier);
        ledgerService.record(EntryType.PAYMENT, job.getId(), vehicleId, customerId, nullToZero(paid), null);

        return jobRepo.findById(job.getId()).orElseThrow();
    }

    @Transactional
    public void editJob(Long jobId, BigDecimal newCharge, BigDecimal newPartsCost) {
        editJob(jobId, newCharge, newPartsCost, null);
    }

    /** Same as editJob(), plus which supplier the parts cost came from (see LedgerEntry.partsSupplier). */
    @Transactional
    public void editJob(Long jobId, BigDecimal newCharge, BigDecimal newPartsCost, String partsSupplier) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
        ledgerService.adjust(EntryType.CHARGE, jobId, job.getVehicleId(), job.getCustomerId(), newCharge, null);
        ledgerService.adjust(EntryType.PARTS_COST, jobId, job.getVehicleId(), job.getCustomerId(), newPartsCost, null, partsSupplier);
    }

    /**
     * Updates just the charge -- used when a converted quote's total
     * changes after the fact (QuoteService.editQuote). Deliberately
     * narrower than editJob: parts cost and paid are untouched, since an
     * edited quote only ever describes the charge, never those.
     */
    @Transactional
    public void updateCharge(Long jobId, BigDecimal newCharge) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
        ledgerService.adjust(EntryType.CHARGE, jobId, job.getVehicleId(), job.getCustomerId(), newCharge, null);
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
        recordSharedPartsCost(customerId, amount, note, null);
    }

    public void recordSharedPartsCost(Long customerId, BigDecimal amount, String note, String partsSupplier) {
        ledgerService.recordSharedCost(customerId, amount, note, partsSupplier);
    }

    public Job get(Long jobId) {
        return jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
    }

    public Optional<Customer> customerFor(Job job) {
        if (job.getCustomerId() == null) {
            return Optional.empty();
        }
        return customerService.findById(job.getCustomerId());
    }

    public Optional<Customer> findCustomerByPhone(String phone) {
        return customerService.findByPhone(phone);
    }

    public List<Vehicle> vehiclesFor(Long customerId) {
        return vehicleService.vehiclesFor(customerId);
    }

    /** The vehicle's description if it has a persisted Vehicle, else the free-text walk-in note, else null. */
    public String vehicleLabelFor(Job job) {
        return vehicleService.labelFor(job.getVehicleId(), job.getVehicleNote());
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
                .map(id -> customerService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
