package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
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

    @Transactional
    public Job createJob(String customerName, String customerPhone, String vehicleDescription,
                          String workType, BigDecimal charge, BigDecimal partsCost, BigDecimal paid) {
        Long customerId = resolveCustomer(customerName, customerPhone);

        Job job = new Job();
        job.setCustomerId(customerId);
        job.setVehicleDescription(vehicleDescription);
        job.setWorkType(workType);
        job.setDate(LocalDate.now());
        job.setCachedCharge(BigDecimal.ZERO);
        job.setCachedPaid(BigDecimal.ZERO);
        job.setCachedBalance(BigDecimal.ZERO);
        job = jobRepo.save(job);

        ledgerService.record(EntryType.CHARGE, job.getId(), customerId, nullToZero(charge), null);
        ledgerService.record(EntryType.PARTS_COST, job.getId(), customerId, nullToZero(partsCost), null);
        ledgerService.record(EntryType.PAYMENT, job.getId(), customerId, nullToZero(paid), null);

        return jobRepo.findById(job.getId()).orElseThrow();
    }

    @Transactional
    public void editJob(Long jobId, BigDecimal newCharge, BigDecimal newPartsCost) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
        ledgerService.adjust(EntryType.CHARGE, jobId, job.getCustomerId(), newCharge, null);
        ledgerService.adjust(EntryType.PARTS_COST, jobId, job.getCustomerId(), newPartsCost, null);
    }

    @Transactional
    public void recordPayment(Long jobId, BigDecimal amountPaidTotal) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
        ledgerService.adjust(EntryType.PAYMENT, jobId, job.getCustomerId(), amountPaidTotal, null);
    }

    @Transactional
    public void voidJob(Long jobId) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));
        ledgerService.adjust(EntryType.CHARGE, jobId, job.getCustomerId(), BigDecimal.ZERO, "voided");
        ledgerService.adjust(EntryType.PAYMENT, jobId, job.getCustomerId(), BigDecimal.ZERO, "voided");
        ledgerService.adjust(EntryType.PARTS_COST, jobId, job.getCustomerId(), BigDecimal.ZERO, "voided");
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
