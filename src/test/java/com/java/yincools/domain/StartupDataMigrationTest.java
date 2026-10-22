package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.Vehicle;
import com.java.yincools.persistence.CustomerRepository;
import com.java.yincools.persistence.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StartupDataMigrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private StartupDataMigration migration;

    @BeforeEach
    void setUp() {
        migration = new StartupDataMigration(customerRepository, vehicleRepository);
    }

    /** Simulates a row saved before phone normalization existed. */
    private Customer saveRawCustomer(String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        return customerRepository.save(customer);
    }

    private Vehicle saveRawVehicle(Long customerId, String description, String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomerId(customerId);
        vehicle.setDescription(description);
        vehicle.setPlateNumber(plate);
        return vehicleRepository.save(vehicle);
    }

    @Test
    void normalizesAPhoneThatWasStoredBeforeNormalizationExisted() {
        Customer customer = saveRawCustomer("Bode", "+234 803 123 4567");

        migration.run(null);

        Customer reloaded = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(reloaded.getPhone()).isEqualTo("08031234567");
    }

    @Test
    void normalizesAPlateThatWasStoredBeforeNormalizationExisted() {
        Vehicle vehicle = saveRawVehicle(1L, "Camry 2010", "abc-123");

        migration.run(null);

        Vehicle reloaded = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(reloaded.getPlateNumber()).isEqualTo("ABC123");
    }

    @Test
    void runningItTwiceLeavesAlreadyNormalizedDataUnchanged() {
        saveRawCustomer("Bode", "0803 123 4567");
        saveRawVehicle(1L, "Camry 2010", "abc-123");

        migration.run(null);
        migration.run(null);

        assertThat(customerRepository.findAll()).hasSize(1);
        assertThat(customerRepository.findAll().get(0).getPhone()).isEqualTo("08031234567");
        assertThat(vehicleRepository.findAll()).hasSize(1);
        assertThat(vehicleRepository.findAll().get(0).getPlateNumber()).isEqualTo("ABC123");
    }

    @Test
    void neverMergesOrDeletesCustomersThatShareANormalizedPhone() {
        saveRawCustomer("Bode", "08031234567");
        saveRawCustomer("Someone else typing the same number in later", "+234 803 123 4567");

        migration.run(null);

        assertThat(customerRepository.count()).isEqualTo(2);
    }
}
