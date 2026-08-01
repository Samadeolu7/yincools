package com.java.yincools.domain;

import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
import com.java.yincools.persistence.CustomerRepository;
import com.java.yincools.persistence.JobRepository;
import com.java.yincools.persistence.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class InsightServiceTest {

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private LedgerService ledgerService;
    private JobService jobService;
    private InsightService insightService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(ledgerRepository, jobRepository);
        jobService = new JobService(jobRepository, customerRepository, ledgerService);
        insightService = new InsightService(ledgerRepository, jobRepository);
    }

    @Test
    void weeklySummaryOnlySumsEntriesWithinTheWeekAndComputesProfitAsChargeMinusCosts() {
        LocalDate monday = LocalDate.of(2026, 8, 3); // a Monday
        LocalDate sunday = monday.plusDays(6);
        LocalDate lastWeekSunday = monday.minusDays(1);

        // in-week job
        Job inWeek = jobService.createJob(null, null, "Car A", "REGAS",
                new BigDecimal("15000"), new BigDecimal("3000"), new BigDecimal("15000"));
        backdateJob(inWeek, monday.plusDays(2));
        backdateEntries(inWeek.getId(), monday.plusDays(2));

        // out-of-week job (should not count)
        Job outOfWeek = jobService.createJob(null, null, "Car B", "REGAS",
                new BigDecimal("99999"), new BigDecimal("1"), new BigDecimal("1"));
        backdateJob(outOfWeek, lastWeekSunday);
        backdateEntries(outOfWeek.getId(), lastWeekSunday);

        ledgerService.recordShopExpense(new BigDecimal("2000"), "rent");
        backdateShopExpenses(monday.plusDays(3));

        WeeklySummary summary = insightService.weeklySummary(monday.plusDays(3));

        assertThat(summary.weekStart()).isEqualTo(monday);
        assertThat(summary.weekEnd()).isEqualTo(sunday);
        assertThat(summary.jobCount()).isEqualTo(1);
        assertThat(summary.totalCharged()).isEqualByComparingTo("15000");
        assertThat(summary.totalPaid()).isEqualByComparingTo("15000");
        assertThat(summary.totalPartsCost()).isEqualByComparingTo("3000");
        assertThat(summary.totalShopExpenses()).isEqualByComparingTo("2000");
        // profit = charged - partsCost - shopExpenses = 15000 - 3000 - 2000
        assertThat(summary.profit()).isEqualByComparingTo("10000");
    }

    @Test
    void debtorListReturnsOnlyJobsWithPositiveBalanceOrderedHighestFirst() {
        Job paidInFull = jobService.createJob(null, null, "Car A", "REGAS",
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));
        Job smallDebt = jobService.createJob(null, null, "Car B", "REGAS",
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("7000"));
        Job bigDebt = jobService.createJob(null, null, "Car C", "REGAS",
                new BigDecimal("20000"), BigDecimal.ZERO, new BigDecimal("0"));

        var debtors = insightService.debtorList();

        assertThat(debtors).extracting(Job::getId)
                .containsExactly(bigDebt.getId(), smallDebt.getId());
        assertThat(debtors).extracting(Job::getId).doesNotContain(paidInFull.getId());
    }

    private void backdateJob(Job job, LocalDate date) {
        job.setDate(date);
        jobRepository.save(job);
    }

    private void backdateEntries(Long jobId, LocalDate date) {
        ledgerRepository.findByJobId(jobId).forEach(entry -> {
            entry.setDate(date);
            ledgerRepository.save(entry);
        });
    }

    private void backdateShopExpenses(LocalDate date) {
        ledgerRepository.findByType(EntryType.SHOP_EXPENSE).forEach(entry -> {
            entry.setDate(date);
            ledgerRepository.save(entry);
        });
    }
}
