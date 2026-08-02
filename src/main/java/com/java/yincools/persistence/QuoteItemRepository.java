package com.java.yincools.persistence;

import com.java.yincools.domain.model.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuoteItemRepository extends JpaRepository<QuoteItem, Long> {

    List<QuoteItem> findByQuoteId(Long quoteId);

    /** The "database of parts" the suggestion lists grow from -- every distinct name ever typed on a quote. */
    @Query("select distinct qi.partName from QuoteItem qi order by qi.partName")
    List<String> findDistinctPartNames();
}
