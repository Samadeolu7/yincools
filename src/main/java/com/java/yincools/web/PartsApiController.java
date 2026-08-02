package com.java.yincools.web;

import com.java.yincools.domain.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The "database of parts" -- distinct part names ever typed on a quote,
 * merged client-side with the static seed list (parts-seed.json). Powers
 * suggestions on both the New Quote itemized table and New Job's parts
 * chips, so a part learned in one place shows up in the other too.
 */
@RestController
@RequestMapping("/api/parts")
@RequiredArgsConstructor
public class PartsApiController {

    private final QuoteService quoteService;

    @GetMapping("/suggestions")
    public List<String> suggestions() {
        return quoteService.partSuggestions();
    }
}
