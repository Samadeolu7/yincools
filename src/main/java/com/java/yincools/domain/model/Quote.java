package com.java.yincools.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Deliberately NOT a LedgerEntry source -- a quote is a proposal, not a
 * financial fact, and the ledger only ever records real facts. This entity
 * lives entirely outside the ledger until (if) it's accepted and converted.
 *
 * The quoted total is never stored here -- it's always the sum of this
 * quote's QuoteItem rows (see QuoteService.totalFor()), so there's nothing
 * to keep in sync as items are added.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Quote {

    @Id
    @GeneratedValue
    private Long id;

    /** Nullable -- a quote can exist before a Customer does. */
    private Long customerId;

    /** Set when there's a persisted Vehicle behind this quote. */
    private Long vehicleId;

    /** Free text fallback, same pattern as Job.vehicleNote. */
    private String vehicleNote;

    private String workType;

    private LocalDate date;

    /** Null until accepted; set by "Convert to Job". */
    private Long convertedToJobId;

    /**
     * Client-generated UUID from the offline quote-capture flow -- same
     * idempotency purpose as Job.clientId: lets a retried submission (phone
     * lost signal before the first response arrived) be recognized as
     * already-processed instead of creating a duplicate quote. Null for
     * quotes created through the normal form.
     */
    private String clientId;
}
