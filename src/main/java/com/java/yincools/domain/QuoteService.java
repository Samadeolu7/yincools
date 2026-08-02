package com.java.yincools.domain;

import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.Quote;
import com.java.yincools.domain.model.QuoteItem;
import com.java.yincools.persistence.QuoteItemRepository;
import com.java.yincools.persistence.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A quote is Dad's pitch, not his bookkeeping -- it never calls
 * LedgerService, full stop, until (if) it's converted to a real Job.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepo;
    private final QuoteItemRepository quoteItemRepo;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final JobService jobService;

    /** Same vehicle resolution as JobService.createJob -- a quote is very often for a prospect who isn't a customer yet. */
    @Transactional
    public Quote createQuote(String customerName, String customerPhone,
                              Long vehicleId, String newVehicleDescription, String newVehiclePlateNumber,
                              String vehicleNote,
                              String workType, List<QuotePartLine> items) {
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
        quote.setDate(LocalDate.now());
        quote = quoteRepo.save(quote);

        saveItems(quote.getId(), items);

        return quote;
    }

    /**
     * A quote is editable at any time, including after it's already become
     * a job -- there was never a real reason to freeze it, and Dad often
     * only learns the real part price (or realizes he under/over-quoted)
     * after the work is under way. Customer/vehicle/work type only ever
     * change the quote's own record. The items/total are different: if this
     * quote already has a job, its whole reason for existing was to become
     * that job's charge, so a changed total pushes straight onto the job's
     * CHARGE ledger entry (JobService.updateCharge) rather than leaving the
     * two silently disagreeing about the price.
     */
    @Transactional
    public Quote editQuote(Long quoteId, String customerName, String customerPhone,
                            Long vehicleId, String newVehicleDescription, String newVehiclePlateNumber,
                            String vehicleNote, String workType, List<QuotePartLine> items) {
        Quote quote = get(quoteId);

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

        quote.setCustomerId(customerId);
        quote.setVehicleId(resolvedVehicleId);
        quote.setVehicleNote(resolvedVehicleNote);
        quote.setWorkType(workType);
        quoteRepo.save(quote);

        quoteItemRepo.deleteAll(quoteItemRepo.findByQuoteId(quoteId));
        saveItems(quoteId, items);

        if (quote.getConvertedToJobId() != null) {
            jobService.updateCharge(quote.getConvertedToJobId(), totalFor(quoteId));
        }

        return quote;
    }

    private void saveItems(Long quoteId, List<QuotePartLine> items) {
        for (QuotePartLine line : items) {
            if (!StringUtils.hasText(line.partName()) || line.amount() == null) {
                continue;
            }
            QuoteItem item = new QuoteItem();
            item.setQuoteId(quoteId);
            item.setPartName(line.partName());
            item.setAmount(line.amount());
            quoteItemRepo.save(item);
        }
    }

    /**
     * For the offline quote-capture API: same idempotency shape as
     * JobService.createJobIdempotent -- if a quote with this clientId
     * already exists (the phone retried a submission whose first response
     * never arrived), return it untouched instead of creating a second one.
     */
    @Transactional
    public Quote createQuoteIdempotent(String clientId, String customerName, String customerPhone,
                                        Long vehicleId, String newVehicleDescription, String newVehiclePlateNumber,
                                        String vehicleNote, String workType, List<QuotePartLine> items) {
        Optional<Quote> existing = quoteRepo.findByClientId(clientId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Quote quote = createQuote(customerName, customerPhone, vehicleId, newVehicleDescription, newVehiclePlateNumber,
                vehicleNote, workType, items);
        quote.setClientId(clientId);
        return quoteRepo.save(quote);
    }

    /**
     * Charge is the quote's total (sum of its items) -- what's actually
     * paid is a separate decision made once the work is done, not baked
     * into the estimate, so this always starts a job at zero paid. Dad
     * fills that in from the normal Edit Job screen, same as any other
     * correction. Idempotent: converting an already-converted quote just
     * returns the existing job instead of creating a second one.
     */
    @Transactional
    public Job convertToJob(Long quoteId) {
        Quote quote = get(quoteId);
        if (quote.getConvertedToJobId() != null) {
            return jobService.get(quote.getConvertedToJobId());
        }

        BigDecimal total = totalFor(quoteId);
        Job job = jobService.createJobFromResolvedIdentity(
                quote.getCustomerId(), quote.getVehicleId(), quote.getVehicleNote(),
                quote.getWorkType(), total, BigDecimal.ZERO);

        quote.setConvertedToJobId(job.getId());
        quoteRepo.save(quote);
        return job;
    }

    public Quote get(Long quoteId) {
        return quoteRepo.findById(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("No quote with id " + quoteId));
    }

    public List<QuoteItem> itemsFor(Long quoteId) {
        return quoteItemRepo.findByQuoteId(quoteId);
    }

    public BigDecimal totalFor(Long quoteId) {
        return itemsFor(quoteId).stream()
                .map(QuoteItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Quote> recentOpenQuotes() {
        return quoteRepo.findTop20ByConvertedToJobIdIsNullOrderByIdDesc();
    }

    /** The static seed list (parts-seed.json) merged client-side with this -- the "database of parts" that grows from usage. */
    public List<String> partSuggestions() {
        return quoteItemRepo.findDistinctPartNames().stream()
                .filter(StringUtils::hasText)
                .toList();
    }
}
