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
 * Read cache, not a source of truth. Only ever written by LedgerService, and
 * fully rebuildable from LedgerEntry at any time -- that rebuild is the
 * nightly balance-check job.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Job {

    @Id
    @GeneratedValue
    private Long id;

    private Long customerId;

    private String vehicleDescription;

    private String workType;

    private LocalDate date;

    private BigDecimal cachedCharge;

    private BigDecimal cachedPaid;

    private BigDecimal cachedBalance;
}
