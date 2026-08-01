package com.java.yincools.persistence;

import com.java.yincools.domain.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findTop20ByOrderByIdDesc();

    Optional<Job> findTopByOrderByIdDesc();

    long countByDateBetween(LocalDate start, LocalDate end);

    List<Job> findByCachedBalanceGreaterThanOrderByCachedBalanceDesc(BigDecimal threshold);

    Optional<Job> findByClientId(String clientId);
}
