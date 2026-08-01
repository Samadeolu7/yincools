package com.java.yincools.domain;

import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
import com.java.yincools.persistence.JobRepository;
import com.java.yincools.persistence.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LedgerServiceTest {

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private JobRepository jobRepository;

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(ledgerRepository, jobRepository);
    }

    private Long newJob() {
        Job job = new Job();
        job.setCachedCharge(BigDecimal.ZERO);
        job.setCachedPaid(BigDecimal.ZERO);
        job.setCachedBalance(BigDecimal.ZERO);
        return jobRepository.save(job).getId();
    }

    @Test
    void repeatedCorrectionsToChargeAlwaysLeaveNetEqualToLastStatedTotal() {
        Long jobId = newJob();

        ledgerService.record(EntryType.CHARGE, jobId, null, null, new BigDecimal("15000"), "initial entry");
        assertNetEquals("15000", EntryType.CHARGE, jobId);
        assertCachedChargeEquals("15000", jobId);

        ledgerService.adjust(EntryType.CHARGE, jobId, null, null, new BigDecimal("12000"), "corrected amount");
        assertNetEquals("12000", EntryType.CHARGE, jobId);
        assertCachedChargeEquals("12000", jobId);

        ledgerService.adjust(EntryType.CHARGE, jobId, null, null, new BigDecimal("20000"), "corrected again");
        assertNetEquals("20000", EntryType.CHARGE, jobId);
        assertCachedChargeEquals("20000", jobId);

        ledgerService.adjust(EntryType.CHARGE, jobId, null, null, new BigDecimal("18500"), "one more correction");
        assertNetEquals("18500", EntryType.CHARGE, jobId);
        assertCachedChargeEquals("18500", jobId);

        // four total corrections, all inserts, nothing ever updated/deleted
        assertThat(ledgerRepository.findByJobIdAndType(jobId, EntryType.CHARGE)).hasSize(4);
    }

    @Test
    void firstEntryForAJobIsNotFlaggedAsACorrection() {
        Long jobId = newJob();

        ledgerService.record(EntryType.CHARGE, jobId, null, null, new BigDecimal("15000"), "initial entry");

        var entries = ledgerRepository.findByJobIdAndType(jobId, EntryType.CHARGE);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).isCorrection()).isFalse();
    }

    @Test
    void subsequentAdjustmentsAreFlaggedAsCorrections() {
        Long jobId = newJob();

        ledgerService.record(EntryType.CHARGE, jobId, null, null, new BigDecimal("15000"), "initial entry");
        ledgerService.adjust(EntryType.CHARGE, jobId, null, null, new BigDecimal("12000"), "corrected amount");

        var entries = ledgerRepository.findByJobIdAndType(jobId, EntryType.CHARGE);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(1).isCorrection()).isTrue();
    }

    @Test
    void adjustingToTheSameTotalIsANoOp() {
        Long jobId = newJob();

        ledgerService.record(EntryType.CHARGE, jobId, null, null, new BigDecimal("15000"), "initial entry");
        ledgerService.adjust(EntryType.CHARGE, jobId, null, null, new BigDecimal("15000"), "no real change");

        assertThat(ledgerRepository.findByJobIdAndType(jobId, EntryType.CHARGE)).hasSize(1);
    }

    @Test
    void balanceReflectsChargeAndPaymentAcrossCorrections() {
        Long jobId = newJob();

        ledgerService.record(EntryType.CHARGE, jobId, null, null, new BigDecimal("20000"), null);
        ledgerService.record(EntryType.PAYMENT, jobId, null, null, new BigDecimal("5000"), null);
        assertCachedBalanceEquals("15000", jobId);

        // dad mis-typed the payment; corrects it to the real total paid so far
        ledgerService.adjust(EntryType.PAYMENT, jobId, null, null, new BigDecimal("20000"), "full payment");
        assertCachedBalanceEquals("0", jobId);

        ledgerService.adjust(EntryType.CHARGE, jobId, null, null, new BigDecimal("18000"), "job was smaller than quoted");
        assertCachedBalanceEquals("-2000", jobId);
    }

    @Test
    void partsCostAndChargeAreIndependentLedgersForTheSameJob() {
        Long jobId = newJob();

        ledgerService.record(EntryType.CHARGE, jobId, null, null, new BigDecimal("15000"), null);
        ledgerService.record(EntryType.PARTS_COST, jobId, null, null, new BigDecimal("4000"), null);

        assertNetEquals("15000", EntryType.CHARGE, jobId);
        assertNetEquals("4000", EntryType.PARTS_COST, jobId);
    }

    @Test
    void shopExpenseHasNoJobOrCustomerAndDoesNotTouchAnyJobCache() {
        Long jobId = newJob();
        ledgerService.record(EntryType.CHARGE, jobId, null, null, new BigDecimal("15000"), null);

        ledgerService.recordShopExpense(new BigDecimal("3000"), "rent");

        var expenses = ledgerRepository.findByType(EntryType.SHOP_EXPENSE);
        assertThat(expenses).hasSize(1);
        assertThat(expenses.get(0).getJobId()).isNull();
        assertThat(expenses.get(0).getCustomerId()).isNull();

        // unrelated job's cache is untouched
        assertCachedChargeEquals("15000", jobId);
    }

    @Test
    void sharedPartsCostHasNoJobOrVehicleButIsTiedToACustomer() {
        Long customerId = 42L;

        ledgerService.recordSharedCost(customerId, new BigDecimal("6000"), "shared gas can");

        var entries = ledgerRepository.findByCustomerId(customerId);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getType()).isEqualTo(EntryType.PARTS_COST);
        assertThat(entries.get(0).getJobId()).isNull();
        assertThat(entries.get(0).getVehicleId()).isNull();
        assertThat(entries.get(0).getSignedAmount()).isEqualByComparingTo("6000");
    }

    @Test
    void sharedPartsCostForDifferentCustomersDoesNotNetAgainstEachOther() {
        ledgerService.recordSharedCost(1L, new BigDecimal("6000"), "shared gas can");
        ledgerService.recordSharedCost(2L, new BigDecimal("2500"), "shared gas can");

        assertThat(ledgerRepository.findByCustomerId(1L)).hasSize(1)
                .allSatisfy(e -> assertThat(e.getSignedAmount()).isEqualByComparingTo("6000"));
        assertThat(ledgerRepository.findByCustomerId(2L)).hasSize(1)
                .allSatisfy(e -> assertThat(e.getSignedAmount()).isEqualByComparingTo("2500"));
    }

    private void assertNetEquals(String expected, EntryType type, Long jobId) {
        assertThat(ledgerService.netFor(type, jobId)).isEqualByComparingTo(expected);
    }

    private void assertCachedChargeEquals(String expected, Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getCachedCharge()).isEqualByComparingTo(expected);
    }

    private void assertCachedBalanceEquals(String expected, Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getCachedBalance()).isEqualByComparingTo(expected);
    }
}
