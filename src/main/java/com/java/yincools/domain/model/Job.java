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

    /** Set when there's a persisted Vehicle behind this job. */
    private Long vehicleId;

    /** Free text for a walk-in with no customer -- never becomes a Vehicle row. */
    private String vehicleNote;

    private String workType;

    private LocalDate date;

    private BigDecimal cachedCharge;

    private BigDecimal cachedPaid;

    private BigDecimal cachedBalance;

    /**
     * Client-generated UUID from the offline job-capture flow -- lets a
     * retried submission (phone lost signal before the first response
     * arrived) be recognized as already-processed instead of creating a
     * duplicate job. Null for jobs created through the normal form.
     */
    private String clientId;
}
