package com.java.yincools.persistence;

import com.java.yincools.domain.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findTop20ByConvertedToJobIdIsNullOrderByIdDesc();
}
