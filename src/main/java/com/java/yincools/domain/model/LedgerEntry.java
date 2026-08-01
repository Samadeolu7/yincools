package com.java.yincools.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only. No row in this table is ever updated or deleted after insert --
 * see LedgerRepository, which does not expose update/delete methods at all.
 * A correction is just another row whose signedAmount is the delta needed to
 * bring the net for (jobId, type) to the newly stated total.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    private BigDecimal signedAmount;

    /** Null for SHOP_EXPENSE, and for costs shared across a visit rather than tied to one job. */
    private Long jobId;

    /** Null wherever jobId is null, for the same reason. */
    private Long vehicleId;

    /** Null for SHOP_EXPENSE only. */
    private Long customerId;

    private LocalDate date;

    private String note;

    private boolean correction;

    private Instant createdAt;
}
