package com.java.yincools.domain;

import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.LedgerEntry;
import com.java.yincools.persistence.JobRepository;
import com.java.yincools.persistence.LedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The only class allowed to write a LedgerEntry. Nobody ever edits money --
 * they only ever add a new fact about it -- so every write here is an
 * INSERT. A "correction" is just a row whose signedAmount is the delta
 * needed to bring the net for (jobId, type) to the newly stated total.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerRepository ledgerRepo;
    private final JobRepository jobRepo;

    @Transactional
    public void adjust(EntryType type, Long jobId, Long customerId, BigDecimal newTotal, String note) {
        BigDecimal currentNet = netFor(type, jobId);
        BigDecimal delta = newTotal.subtract(currentNet);

        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        LedgerEntry entry = new LedgerEntry();
        entry.setType(type);
        entry.setSignedAmount(delta);
        entry.setJobId(jobId);
        entry.setCustomerId(customerId);
        entry.setDate(LocalDate.now());
        entry.setNote(note);
        entry.setCorrection(currentNet.compareTo(BigDecimal.ZERO) != 0);
        entry.setCreatedAt(Instant.now());

        ledgerRepo.save(entry);
        refreshJobCache(jobId);
    }

    /** Recording a brand new fact is mathematically identical to adjusting from a net of zero. */
    public void record(EntryType type, Long jobId, Long customerId, BigDecimal amount, String note) {
        adjust(type, jobId, customerId, amount, note);
    }

    @Transactional
    public void recordShopExpense(BigDecimal amount, String note) {
        LedgerEntry entry = new LedgerEntry();
        entry.setType(EntryType.SHOP_EXPENSE);
        entry.setSignedAmount(amount);
        entry.setJobId(null);
        entry.setCustomerId(null);
        entry.setDate(LocalDate.now());
        entry.setNote(note);
        entry.setCorrection(false);
        entry.setCreatedAt(Instant.now());

        ledgerRepo.save(entry);
    }

    public BigDecimal netFor(EntryType type, Long jobId) {
        return ledgerRepo.findByJobIdAndType(jobId, type).stream()
                .map(LedgerEntry::getSignedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void refreshJobCache(Long jobId) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No job with id " + jobId));

        BigDecimal charge = netFor(EntryType.CHARGE, jobId);
        BigDecimal paid = netFor(EntryType.PAYMENT, jobId);

        job.setCachedCharge(charge);
        job.setCachedPaid(paid);
        job.setCachedBalance(charge.subtract(paid));

        jobRepo.save(job);
    }
}
