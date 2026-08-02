package com.java.yincools.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One part/charge line on a Quote -- "Compressor: NGN 5,000". A quote's
 * total is always the sum of its items, never stored separately, so there's
 * nothing to keep in sync. Immutable once created, same as the Quote it
 * belongs to.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class QuoteItem {

    @Id
    @GeneratedValue
    private Long id;

    private Long quoteId;

    private String partName;

    private BigDecimal amount;
}
