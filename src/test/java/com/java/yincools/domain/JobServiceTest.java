package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.Job;
import com.java.yincools.persistence.CustomerRepository;
import com.java.yincools.persistence.JobRepository;
import com.java.yincools.persistence.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JobServiceTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        LedgerService ledgerService = new LedgerService(ledgerRepository, jobRepository);
        jobService = new JobService(jobRepository, customerRepository, ledgerService);
    }

    @Test
    void createJobStoresChargePartsCostAndPaymentAndComputesBalance() {
        Job job = jobService.createJob("Bode", "08010000000", "Camry 2010", "REGAS",
                new BigDecimal("15000"), new BigDecimal("2000"), new BigDecimal("5000"));

        assertThat(job.getCachedCharge()).isEqualByComparingTo("15000");
        assertThat(job.getCachedPaid()).isEqualByComparingTo("5000");
        assertThat(job.getCachedBalance()).isEqualByComparingTo("10000");
        assertThat(job.getCustomerId()).isNotNull();
    }

    @Test
    void secondJobForSamePhoneReusesExistingCustomer() {
        Job first = jobService.createJob("Bode", "08010000000", "Camry 2010", "REGAS",
                new BigDecimal("15000"), BigDecimal.ZERO, BigDecimal.ZERO);
        Job second = jobService.createJob("Bode", "08010000000", "Camry 2010", "DIAGNOSIS",
                new BigDecimal("3000"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(second.getCustomerId()).isEqualTo(first.getCustomerId());
        assertThat(customerRepository.count()).isEqualTo(1);
    }

    @Test
    void jobWithNoCustomerInfoIsAllowed() {
        Job job = jobService.createJob(null, null, "Random car", "OTHER",
                new BigDecimal("5000"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(job.getCustomerId()).isNull();
    }

    @Test
    void editJobCorrectsChargeAndPartsCostWithoutTouchingPayment() {
        Job job = jobService.createJob(null, null, "Corolla", "COMPRESSOR",
                new BigDecimal("20000"), new BigDecimal("6000"), new BigDecimal("20000"));

        jobService.editJob(job.getId(), new BigDecimal("18000"), new BigDecimal("6000"));

        Job updated = jobService.get(job.getId());
        assertThat(updated.getCachedCharge()).isEqualByComparingTo("18000");
        assertThat(updated.getCachedPaid()).isEqualByComparingTo("20000");
        assertThat(updated.getCachedBalance()).isEqualByComparingTo("-2000");
    }

    @Test
    void recordPaymentUpdatesTotalPaidToTheNewStatedAmount() {
        Job job = jobService.createJob(null, null, "Civic", "FAN",
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("3000"));

        jobService.recordPayment(job.getId(), new BigDecimal("10000"));

        Job updated = jobService.get(job.getId());
        assertThat(updated.getCachedPaid()).isEqualByComparingTo("10000");
        assertThat(updated.getCachedBalance()).isEqualByComparingTo("0");
    }

    @Test
    void voidJobZeroesOutTheBalanceButKeepsTheRow() {
        Job job = jobService.createJob(null, null, "Sienna", "CONDENSER",
                new BigDecimal("25000"), new BigDecimal("5000"), new BigDecimal("10000"));

        jobService.voidJob(job.getId());

        Job voided = jobService.get(job.getId());
        assertThat(voided.getCachedCharge()).isEqualByComparingTo("0");
        assertThat(voided.getCachedPaid()).isEqualByComparingTo("0");
        assertThat(voided.getCachedBalance()).isEqualByComparingTo("0");
        assertThat(jobRepository.findById(job.getId())).isPresent();
    }

    @Test
    void recentCustomersReturnsMostRecentlyUsedFirstWithoutDuplicates() {
        Job j1 = jobService.createJob("Ada", "0800000001", "Car A", "OTHER", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        jobService.createJob(null, null, "Car B", "OTHER", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        jobService.createJob("Ada", "0800000001", "Car A again", "OTHER", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        Job j4 = jobService.createJob("Chidi", "0800000002", "Car C", "OTHER", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);

        List<Customer> recent = jobService.recentCustomers();

        assertThat(recent).extracting(Customer::getId)
                .containsExactly(j4.getCustomerId(), j1.getCustomerId());
    }
}
