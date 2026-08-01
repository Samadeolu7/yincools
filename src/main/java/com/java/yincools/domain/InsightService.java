package com.java.yincools.domain;

import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.LedgerEntry;
import com.java.yincools.persistence.JobRepository;
import com.java.yincools.persistence.LedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only queries against the same ledger LedgerService writes to. New
 * insights are new methods here -- the write path never changes.
 */
@Service
@RequiredArgsConstructor
public class InsightService {

    private final LedgerRepository ledgerRepo;
    private final JobRepository jobRepo;

    public WeeklySummary weeklySummary(LocalDate today) {
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<LedgerEntry> entries = ledgerRepo.findByDateBetween(weekStart, weekEnd);

        BigDecimal charged = sumByType(entries, EntryType.CHARGE);
        BigDecimal paid = sumByType(entries, EntryType.PAYMENT);
        BigDecimal partsCost = sumByType(entries, EntryType.PARTS_COST);
        BigDecimal shopExpenses = sumByType(entries, EntryType.SHOP_EXPENSE);
        BigDecimal profit = charged.subtract(partsCost).subtract(shopExpenses);

        int jobCount = (int) jobRepo.countByDateBetween(weekStart, weekEnd);

        return new WeeklySummary(weekStart, weekEnd, jobCount, charged, paid, partsCost, shopExpenses, profit);
    }

    public List<Job> debtorList() {
        return jobRepo.findByCachedBalanceGreaterThanOrderByCachedBalanceDesc(BigDecimal.ZERO);
    }

    private BigDecimal sumByType(List<LedgerEntry> entries, EntryType type) {
        return entries.stream()
                .filter(e -> e.getType() == type)
                .map(LedgerEntry::getSignedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
