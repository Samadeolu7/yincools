package com.java.yincools.persistence;

import com.java.yincools.domain.model.EntryType;
import com.java.yincools.domain.model.LedgerEntry;
import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Deliberately extends the bare marker interface Repository, not
 * JpaRepository/CrudRepository. Only the methods declared below exist --
 * there is no update() or delete() to call, by construction, not convention.
 * If a bug ever tries to mutate a ledger row, it fails to compile.
 */
public interface LedgerRepository extends Repository<LedgerEntry, Long> {

    LedgerEntry save(LedgerEntry entry);

    Optional<LedgerEntry> findById(Long id);

    List<LedgerEntry> findByJobIdAndType(Long jobId, EntryType type);

    List<LedgerEntry> findByJobId(Long jobId);

    List<LedgerEntry> findByCustomerId(Long customerId);

    List<LedgerEntry> findByType(EntryType type);

    List<LedgerEntry> findByDateBetween(LocalDate start, LocalDate end);
}
