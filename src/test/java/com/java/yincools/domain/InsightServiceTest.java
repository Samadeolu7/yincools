package com.java.yincools.domain;

import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
import com.java.yincools.persistence.CustomerRepository;
import com.java.yincools.persistence.JobRepository;
import com.java.yincools.persistence.LedgerRepository;
import com.java.yincools.persistence.VehicleRepository;
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

    @Autowired
    private VehicleRepository vehicleRepository;

    private LedgerService ledgerService;
    private JobService jobService;
    private InsightService insightService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(ledgerRepository, jobRepository);
        VehicleService vehicleService = new VehicleService(vehicleRepository);
        jobService = new JobService(jobRepository, customerRepository, ledgerService, vehicleService);
        insightService = new InsightService(ledgerRepository, jobRepository);
    }

    private Job createWalkIn(String vehicleNote, String workType, BigDecimal charge, BigDecimal partsCost, BigDecimal paid) {
        return jobService.createJob(null, null, null, null, null, vehicleNote, workType, charge, partsCost, paid);
    }

    @Test
    void weeklySummaryOnlySumsEntriesWithinTheWeekAndComputesProfitAsChargeMinusCosts() {
        LocalDate monday = LocalDate.of(2026, 8, 3); // a Monday
        LocalDate sunday = monday.plusDays(6);
        LocalDate lastWeekSunday = monday.minusDays(1);

        // in-week job
        Job inWeek = createWalkIn("Car A", "REGAS",
                new BigDecimal("15000"), new BigDecimal("3000"), new BigDecimal("15000"));
        backdateJob(inWeek, monday.plusDays(2));
        backdateEntries(inWeek.getId(), monday.plusDays(2));

        // out-of-week job (should not count)
        Job outOfWeek = createWalkIn("Car B", "REGAS",
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
        Job paidInFull = createWalkIn("Car A", "REGAS",
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));
        Job smallDebt = createWalkIn("Car B", "REGAS",
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("7000"));
        Job bigDebt = createWalkIn("Car C", "REGAS",
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
