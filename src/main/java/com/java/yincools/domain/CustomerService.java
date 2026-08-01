package com.java.yincools.domain;

import com.java.yincools.domain.model.Customer;
import com.java.yincools.persistence.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Shared by JobService and QuoteService -- both need to find-or-create a
 * customer by phone the same way, since a quote and a job resolve customers
 * identically.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepo;

    public Long resolveOrCreate(String name, String phone) {
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

    public Optional<Customer> findById(Long id) {
        return customerRepo.findById(id);
    }

    public Optional<Customer> findByPhone(String phone) {
        return customerRepo.findByPhone(phone);
    }
}
