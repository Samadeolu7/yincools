package com.java.yincools.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Deliberately NOT a LedgerEntry source -- a quote is a proposal, not a
 * financial fact, and the ledger only ever records real facts. This entity
 * lives entirely outside the ledger until (if) it's accepted and converted.
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

    /** Built from tapped parts chips plus optional free text. */
    private String partsNote;

    /** Single quoted total -- no per-part pricing. */
    private BigDecimal amount;

    private LocalDate date;

    /** Null until accepted; set by "Convert to Job". */
    private Long convertedToJobId;
}
