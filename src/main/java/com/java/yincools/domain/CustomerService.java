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
        String normalizedPhone = normalizePhone(phone);
        boolean hasPhone = normalizedPhone != null;
        if (!hasName && !hasPhone) {
            return null;
        }

        if (hasPhone) {
            Optional<Customer> existing = customerRepo.findByPhone(normalizedPhone);
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }

        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(normalizedPhone);
        return customerRepo.save(customer).getId();
    }

    public Optional<Customer> findById(Long id) {
        return customerRepo.findById(id);
    }

    public Optional<Customer> findByPhone(String phone) {
        return customerRepo.findByPhone(normalizePhone(phone));
    }

    /**
     * "0803...", "+234 803..." and "234-803..." are the same number typed
     * three different ways -- without this they silently became three
     * different Customer rows, each with its own vehicle history and debt.
     * Converges everything to the local "0..." form (what's already typed
     * most of the time), stripped of spaces/dashes/parens. Doesn't rewrite
     * phone numbers already stored before this normalization existed.
     */
    private static String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 13 && digits.startsWith("234")) {
            digits = "0" + digits.substring(3);
        }
        return digits.isEmpty() ? null : digits;
    }
}
