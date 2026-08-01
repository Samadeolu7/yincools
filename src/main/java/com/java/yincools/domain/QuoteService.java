package com.java.yincools.domain;

import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.Quote;
import com.java.yincools.persistence.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A quote is Dad's pitch, not his bookkeeping -- it never calls
 * LedgerService, full stop, until (if) it's converted to a real Job.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepo;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final JobService jobService;

    /** Same vehicle resolution as JobService.createJob -- a quote is very often for a prospect who isn't a customer yet. */
    public Quote createQuote(String customerName, String customerPhone,
                              Long vehicleId, String newVehicleDescription, String newVehiclePlateNumber,
                              String vehicleNote,
                              String workType, String partsNote, BigDecimal amount) {
        Long customerId = customerService.resolveOrCreate(customerName, customerPhone);

        Long resolvedVehicleId = null;
        String resolvedVehicleNote = null;

        if (customerId != null) {
            if (vehicleId != null) {
                resolvedVehicleId = vehicleId;
            } else if (StringUtils.hasText(newVehicleDescription)) {
                resolvedVehicleId = vehicleService.findOrCreate(customerId, newVehicleDescription, newVehiclePlateNumber).getId();
            }
        } else {
            resolvedVehicleNote = vehicleNote;
        }

        Quote quote = new Quote();
        quote.setCustomerId(customerId);
        quote.setVehicleId(resolvedVehicleId);
        quote.setVehicleNote(resolvedVehicleNote);
        quote.setWorkType(workType);
        quote.setPartsNote(partsNote);
        quote.setAmount(amount);
        quote.setDate(LocalDate.now());
        return quoteRepo.save(quote);
    }

    /**
     * Defaults to paid in full -- Dad confirms and adjusts from there via the
     * same Edit Job screen as any other job, rather than retyping it.
     * Idempotent: converting an already-converted quote just returns the
     * existing job instead of creating a second one.
     */
    @Transactional
    public Job convertToJob(Long quoteId) {
        Quote quote = get(quoteId);
        if (quote.getConvertedToJobId() != null) {
            return jobService.get(quote.getConvertedToJobId());
        }

        Job job = jobService.createJobFromResolvedIdentity(
                quote.getCustomerId(), quote.getVehicleId(), quote.getVehicleNote(),
                quote.getWorkType(), quote.getAmount(), quote.getAmount());

        quote.setConvertedToJobId(job.getId());
        quoteRepo.save(quote);
        return job;
    }

    public Quote get(Long quoteId) {
        return quoteRepo.findById(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("No quote with id " + quoteId));
    }

    public List<Quote> recentOpenQuotes() {
        return quoteRepo.findTop20ByConvertedToJobIdIsNullOrderByIdDesc();
    }
}
