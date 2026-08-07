package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.WorkType;
import com.java.yincools.persistence.CustomerRepository;
import com.java.yincools.persistence.JobRepository;
import com.java.yincools.persistence.LedgerRepository;
import com.java.yincools.persistence.VehicleRepository;
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

    @Autowired
    private VehicleRepository vehicleRepository;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        LedgerService ledgerService = new LedgerService(ledgerRepository, jobRepository);
        VehicleService vehicleService = new VehicleService(vehicleRepository);
        CustomerService customerService = new CustomerService(customerRepository);
        jobService = new JobService(jobRepository, customerService, ledgerService, vehicleService);
    }

    /** Walk-in: no customer, vehicle is free text on the job. */
    private Job createWalkIn(String vehicleNote, WorkType workType, BigDecimal charge, BigDecimal partsCost, BigDecimal paid) {
        return jobService.createJob(null, null, null, null, null, vehicleNote, workType, charge, partsCost, null, paid);
    }

    /** Customer job with a new vehicle description (creates or reuses a Vehicle row). */
    private Job createForCustomer(String name, String phone, String vehicleDescription, WorkType workType,
                                   BigDecimal charge, BigDecimal partsCost, BigDecimal paid) {
        return jobService.createJob(name, phone, null, vehicleDescription, null, null, workType, charge, partsCost, null, paid);
    }

    @Test
    void createJobStoresChargePartsCostAndPaymentAndComputesBalance() {
        Job job = createForCustomer("Bode", "08010000000", "Camry 2010", WorkType.REGAS,
                new BigDecimal("15000"), new BigDecimal("2000"), new BigDecimal("5000"));

        assertThat(job.getCachedCharge()).isEqualByComparingTo("15000");
        assertThat(job.getCachedPaid()).isEqualByComparingTo("5000");
        assertThat(job.getCachedBalance()).isEqualByComparingTo("10000");
        assertThat(job.getCustomerId()).isNotNull();
        assertThat(job.getVehicleId()).isNotNull();
    }

    @Test
    void secondJobForSamePhoneAndVehicleReusesExistingCustomerAndVehicle() {
        Job first = createForCustomer("Bode", "08010000000", "Camry 2010", WorkType.REGAS,
                new BigDecimal("15000"), BigDecimal.ZERO, BigDecimal.ZERO);
        Job second = createForCustomer("Bode", "08010000000", "Camry 2010", WorkType.DIAGNOSIS,
                new BigDecimal("3000"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(second.getCustomerId()).isEqualTo(first.getCustomerId());
        assertThat(second.getVehicleId()).isEqualTo(first.getVehicleId());
        assertThat(customerRepository.count()).isEqualTo(1);
        assertThat(vehicleRepository.count()).isEqualTo(1);
    }

    @Test
    void secondVehicleForSameCustomerCreatesASeparateVehicleRow() {
        Job first = createForCustomer("Ada", "0800000001", "Toyota Hilux", WorkType.REGAS,
                new BigDecimal("15000"), BigDecimal.ZERO, BigDecimal.ZERO);
        Job second = createForCustomer("Ada", "0800000001", "Honda Accord", WorkType.REGAS,
                new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(second.getCustomerId()).isEqualTo(first.getCustomerId());
        assertThat(second.getVehicleId()).isNotEqualTo(first.getVehicleId());
        assertThat(vehicleRepository.count()).isEqualTo(2);
    }

    @Test
    void pickingAnExistingVehicleIdSkipsCreatingANewOne() {
        Job first = createForCustomer("Ada", "0800000001", "Toyota Hilux", WorkType.REGAS,
                new BigDecimal("15000"), BigDecimal.ZERO, BigDecimal.ZERO);

        Job second = jobService.createJob("Ada", "0800000001", first.getVehicleId(), null, null, null,
                WorkType.DIAGNOSIS, new BigDecimal("3000"), BigDecimal.ZERO, null, BigDecimal.ZERO);

        assertThat(second.getVehicleId()).isEqualTo(first.getVehicleId());
        assertThat(vehicleRepository.count()).isEqualTo(1);
    }

    @Test
    void jobWithNoCustomerInfoIsAllowedAndVehicleIsFreeText() {
        Job job = createWalkIn("Random car", WorkType.OTHER,
                new BigDecimal("5000"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(job.getCustomerId()).isNull();
        assertThat(job.getVehicleId()).isNull();
        assertThat(job.getVehicleNote()).isEqualTo("Random car");
        assertThat(vehicleRepository.count()).isZero();
    }

    @Test
    void vehicleLabelForPrefersThePersistedVehicleAndFallsBackToTheWalkInNote() {
        Job withVehicle = createForCustomer("Bode", "08010000000", "Camry 2010", WorkType.REGAS,
                new BigDecimal("15000"), BigDecimal.ZERO, BigDecimal.ZERO);
        Job walkIn = createWalkIn("Blue Corolla", WorkType.OTHER, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(jobService.vehicleLabelFor(withVehicle)).isEqualTo("Camry 2010");
        assertThat(jobService.vehicleLabelFor(walkIn)).isEqualTo("Blue Corolla");
    }

    @Test
    void editJobCorrectsChargeAndPartsCostWithoutTouchingPayment() {
        Job job = createWalkIn("Corolla", WorkType.COMPRESSOR,
                new BigDecimal("20000"), new BigDecimal("6000"), new BigDecimal("20000"));

        jobService.editJob(job.getId(), new BigDecimal("18000"), new BigDecimal("6000"));

        Job updated = jobService.get(job.getId());
        assertThat(updated.getCachedCharge()).isEqualByComparingTo("18000");
        assertThat(updated.getCachedPaid()).isEqualByComparingTo("20000");
        assertThat(updated.getCachedBalance()).isEqualByComparingTo("-2000");
    }

    @Test
    void recordPaymentUpdatesTotalPaidToTheNewStatedAmount() {
        Job job = createWalkIn("Civic", WorkType.FAN,
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("3000"));

        jobService.recordPayment(job.getId(), new BigDecimal("10000"));

        Job updated = jobService.get(job.getId());
        assertThat(updated.getCachedPaid()).isEqualByComparingTo("10000");
        assertThat(updated.getCachedBalance()).isEqualByComparingTo("0");
    }

    @Test
    void voidJobZeroesOutTheBalanceButKeepsTheRow() {
        Job job = createWalkIn("Sienna", WorkType.CONDENSER,
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
        Job j1 = createForCustomer("Ada", "0800000001", "Car A", WorkType.OTHER, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        createWalkIn("Car B", WorkType.OTHER, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        createForCustomer("Ada", "0800000001", "Car A again", WorkType.OTHER, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        Job j4 = createForCustomer("Chidi", "0800000002", "Car C", WorkType.OTHER, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);

        List<Customer> recent = jobService.recentCustomers();

        assertThat(recent).extracting(Customer::getId)
                .containsExactly(j4.getCustomerId(), j1.getCustomerId());
    }

    @Test
    void lastJobReturnsTheMostRecentlyCreatedJob() {
        createWalkIn("Car A", WorkType.OTHER, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        Job second = createWalkIn("Car B", WorkType.OTHER, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(jobService.lastJob()).isPresent();
        assertThat(jobService.lastJob().get().getId()).isEqualTo(second.getId());
    }

    @Test
    void partsCostForReflectsCorrectionsMadeThroughEditJob() {
        Job job = createWalkIn("Car C", WorkType.OTHER,
                new BigDecimal("10000"), new BigDecimal("2000"), BigDecimal.ZERO);
        assertThat(jobService.partsCostFor(job.getId())).isEqualByComparingTo("2000");

        jobService.editJob(job.getId(), new BigDecimal("10000"), new BigDecimal("2500"));

        assertThat(jobService.partsCostFor(job.getId())).isEqualByComparingTo("2500");
    }

    @Test
    void recordSharedPartsCostIsTiedToCustomerNotAnyJob() {
        Job job = createForCustomer("Fleet Co", "0800000009", "Truck 1", WorkType.OTHER,
                new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO);

        jobService.recordSharedPartsCost(job.getCustomerId(), new BigDecimal("6000"), "shared gas can");

        assertThat(ledgerRepository.findByCustomerId(job.getCustomerId())).hasSize(2);
        // the job's own parts cost is untouched by the shared entry
        assertThat(jobService.partsCostFor(job.getId())).isEqualByComparingTo("0");
    }

    @Test
    void partsNoteFromTappedChipsLandsOnThePartsCostLedgerEntry() {
        Job job = jobService.createJob(null, null, null, null, null, "Corolla",
                WorkType.REGAS, new BigDecimal("10000"), new BigDecimal("4000"), "Compressor, Relay", BigDecimal.ZERO);

        var entries = ledgerRepository.findByJobIdAndType(job.getId(), com.java.yincools.domain.model.EntryType.PARTS_COST);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getNote()).isEqualTo("Compressor, Relay");
    }

    @Test
    void createJobIdempotentRetryWithSameClientIdReturnsTheSameJobWithoutDuplicating() {
        Job first = jobService.createJobIdempotent("client-abc", "Bode", "08010000000",
                null, "Camry 2010", null, null, WorkType.REGAS,
                new BigDecimal("15000"), BigDecimal.ZERO, null, false, new BigDecimal("15000"));

        Job retry = jobService.createJobIdempotent("client-abc", "Bode", "08010000000",
                null, "Camry 2010", null, null, WorkType.REGAS,
                new BigDecimal("15000"), BigDecimal.ZERO, null, false, new BigDecimal("15000"));

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(jobRepository.count()).isEqualTo(1);
        assertThat(customerRepository.count()).isEqualTo(1);
        assertThat(vehicleRepository.count()).isEqualTo(1);
    }

    @Test
    void createJobIdempotentRetryDoesNotDuplicateASharedPartsCostEntry() {
        jobService.createJobIdempotent("client-shared", "Fleet Co", "0800000009",
                null, "Truck 1", null, null, WorkType.REGAS,
                new BigDecimal("10000"), new BigDecimal("6000"), "shared gas can", true, new BigDecimal("10000"));

        Job retry = jobService.createJobIdempotent("client-shared", "Fleet Co", "0800000009",
                null, "Truck 1", null, null, WorkType.REGAS,
                new BigDecimal("10000"), new BigDecimal("6000"), "shared gas can", true, new BigDecimal("10000"));

        // CHARGE + PAYMENT (tied to the job) + one shared PARTS_COST entry -- a
        // duplicated retry would produce 6, not 3.
        assertThat(ledgerRepository.findByCustomerId(retry.getCustomerId())).hasSize(3);
    }
}
