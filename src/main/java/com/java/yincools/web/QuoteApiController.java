package com.java.yincools.web;

import com.java.yincools.domain.QuotePartLine;
import com.java.yincools.domain.QuoteService;
import com.java.yincools.domain.model.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Offline quote capture: the New Quote page's JS posts here instead of the
 * normal form endpoint when it wants a JSON response it can retry safely.
 * Same clientId/idempotency shape as JobApiController -- see
 * QuoteService.createQuoteIdempotent.
 *
 * CSRF-exempt (see SecurityConfig) for the same reason as /api/jobs: JSON-
 * only, no CORS policy configured to allow a cross-origin request to reach
 * it. Still requires the same session/remember-me login as everything else.
 */
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteApiController {

    private final QuoteService quoteService;

    @PostMapping
    public QuoteResponse createQuote(@RequestBody QuoteRequest req) {
        boolean hasCustomer = StringUtils.hasText(req.customerName()) || StringUtils.hasText(req.customerPhone());
        List<QuotePartLine> items = req.items() == null ? List.of() : req.items();

        Quote quote = quoteService.createQuoteIdempotent(req.clientId(), req.customerName(), req.customerPhone(),
                req.vehicleId(),
                hasCustomer ? req.vehicleDescription() : null,
                hasCustomer ? req.vehiclePlateNumber() : null,
                hasCustomer ? null : req.vehicleDescription(),
                req.workType(), items);

        return new QuoteResponse(quote.getId());
    }

    public record QuoteRequest(
            String clientId,
            String customerName,
            String customerPhone,
            Long vehicleId,
            String vehicleDescription,
            String vehiclePlateNumber,
            String workType,
            List<QuotePartLine> items
    ) {
    }

    public record QuoteResponse(Long id) {
    }
}
