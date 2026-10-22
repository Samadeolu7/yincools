package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.persistence.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CustomerServiceTest {

    @Autowired
    private CustomerRepository customerRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository);
    }

    @Test
    void sameNumberTypedThreeDifferentWaysResolvesToOneCustomer() {
        Long first = customerService.resolveOrCreate("Bode", "08031234567");
        Long second = customerService.resolveOrCreate("Bode", "+234 803 123 4567");
        Long third = customerService.resolveOrCreate("Bode", "234-803-123-4567");

        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
        assertThat(customerRepository.count()).isEqualTo(1);
    }

    @Test
    void storesThePhoneInLocalFormRegardlessOfHowItWasTyped() {
        Long id = customerService.resolveOrCreate("Bode", "+234 803 123 4567");

        Customer customer = customerService.findById(id).orElseThrow();
        assertThat(customer.getPhone()).isEqualTo("08031234567");
    }

    @Test
    void findByPhoneMatchesRegardlessOfFormatting() {
        customerService.resolveOrCreate("Bode", "08031234567");

        assertThat(customerService.findByPhone("234 803 123 4567")).isPresent();
        assertThat(customerService.findByPhone("0803-123-4567")).isPresent();
    }

    @Test
    void differentNumbersStillResolveToDifferentCustomers() {
        Long first = customerService.resolveOrCreate("Bode", "08031234567");
        Long second = customerService.resolveOrCreate("Ada", "08079999999");

        assertThat(second).isNotEqualTo(first);
        assertThat(customerRepository.count()).isEqualTo(2);
    }
}
