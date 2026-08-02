package com.java.yincools.domain;

import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.Quote;
import com.java.yincools.persistence.CustomerRepository;
import com.java.yincools.persistence.JobRepository;
import com.java.yincools.persistence.LedgerRepository;
import com.java.yincools.persistence.QuoteItemRepository;
import com.java.yincools.persistence.QuoteRepository;
import com.java.yincools.persistence.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class QuoteServiceTest {

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private QuoteItemRepository quoteItemRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        LedgerService ledgerService = new LedgerService(ledgerRepository, jobRepository);
        VehicleService vehicleService = new VehicleService(vehicleRepository);
        CustomerService customerService = new CustomerService(customerRepository);
        JobService jobService = new JobService(jobRepository, customerService, ledgerService, vehicleService);
        quoteService = new QuoteService(quoteRepository, quoteItemRepository, customerService, vehicleService, jobService);
    }

    private List<QuotePartLine> lines(Object... nameAmountPairs) {
        return List.of(new QuotePartLine((String) nameAmountPairs[0], new BigDecimal(nameAmountPairs[1].toString())));
    }

    @Test
    void createQuoteNeverWritesALedgerEntry() {
        quoteService.createQuote("Bode", "08010000000", null, "Camry 2010", null, null,
                "REGAS", lines("Compressor", 20000));

        assertThat(ledgerRepository.findByType(EntryType.CHARGE)).isEmpty();
        assertThat(ledgerRepository.findByType(EntryType.PARTS_COST)).isEmpty();
        assertThat(vehicleRepository.count()).isEqualTo(1);
        assertThat(customerRepository.count()).isEqualTo(1);
    }

    @Test
    void createQuoteForWalkInStoresFreeTextVehicleNote() {
        Quote quote = quoteService.createQuote(null, null, null, null, null, "Blue Corolla",
                "OTHER", lines("Gas", 5000));

        assertThat(quote.getCustomerId()).isNull();
        assertThat(quote.getVehicleId()).isNull();
        assertThat(quote.getVehicleNote()).isEqualTo("Blue Corolla");
    }

    @Test
    void totalIsTheSumOfItemLines() {
        Quote quote = quoteService.createQuote(null, null, null, null, null, "Corolla",
                "REGAS", List.of(
                        new QuotePartLine("Compressor", new BigDecimal("18000")),
                        new QuotePartLine("Gas", new BigDecimal("5000")),
                        new QuotePartLine("Labor", new BigDecimal("3000"))));

        assertThat(quoteService.itemsFor(quote.getId())).hasSize(3);
        assertThat(quoteService.totalFor(quote.getId())).isEqualByComparingTo("26000");
    }

    @Test
    void convertToJobChargesTheQuoteTotalButStartsAtZeroPaid() {
        Quote quote = quoteService.createQuote("Ada", "0800000001", null, "Toyota Hilux", null, null,
                "COMPRESSOR", lines("Compressor", 30000));

        Job job = quoteService.convertToJob(quote.getId());

        // A quote is an estimate -- what's actually paid is a separate
        // decision made after the work is done, never assumed as "paid in full".
        assertThat(job.getCachedCharge()).isEqualByComparingTo("30000");
        assertThat(job.getCachedPaid()).isEqualByComparingTo("0");
        assertThat(job.getCachedBalance()).isEqualByComparingTo("30000");
        assertThat(job.getCustomerId()).isEqualTo(quote.getCustomerId());
        assertThat(job.getVehicleId()).isEqualTo(quote.getVehicleId());

        Quote converted = quoteService.get(quote.getId());
        assertThat(converted.getConvertedToJobId()).isEqualTo(job.getId());
    }

    @Test
    void convertingAnAlreadyConvertedQuoteIsIdempotent() {
        Quote quote = quoteService.createQuote(null, null, null, null, null, "Civic",
                "FAN", lines("Relay", 4000));

        Job first = quoteService.convertToJob(quote.getId());
        Job second = quoteService.convertToJob(quote.getId());

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void recentOpenQuotesExcludesConvertedOnes() {
        Quote open = quoteService.createQuote(null, null, null, null, null, "Car A",
                "OTHER", lines("Hoses", 1000));
        Quote toConvert = quoteService.createQuote(null, null, null, null, null, "Car B",
                "OTHER", lines("Hoses", 2000));
        quoteService.convertToJob(toConvert.getId());

        var openQuotes = quoteService.recentOpenQuotes();

        assertThat(openQuotes).extracting(Quote::getId).containsExactly(open.getId());
    }

    @Test
    void partSuggestionsReturnsDistinctNamesEverUsed() {
        quoteService.createQuote(null, null, null, null, null, "Car A", "OTHER",
                List.of(new QuotePartLine("Compressor", new BigDecimal("1000")),
                        new QuotePartLine("Gas", new BigDecimal("500"))));
        quoteService.createQuote(null, null, null, null, null, "Car B", "OTHER",
                lines("Compressor", 2000));

        assertThat(quoteService.partSuggestions()).containsExactlyInAnyOrder("Compressor", "Gas");
    }
}
