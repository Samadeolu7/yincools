package com.java.yincools.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.java.yincools.domain.CustomerService;
import com.java.yincools.domain.QuotePartLine;
import com.java.yincools.domain.QuoteService;
import com.java.yincools.domain.VehicleService;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.Quote;
import com.java.yincools.domain.model.QuoteItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private static final List<String> WORK_TYPES =
            List.of("REGAS", "COMPRESSOR", "CONDENSER", "FAN", "DIAGNOSIS", "OTHER");

    private final QuoteService quoteService;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final ObjectMapper objectMapper;

    @GetMapping("/new")
    public String newQuoteForm(Model model) {
        model.addAttribute("workTypes", WORK_TYPES);
        model.addAttribute("recentQuotes", recentQuoteRows());
        return "new-quote";
    }

    @PostMapping
    public String createQuote(@RequestParam(required = false) String customerName,
                               @RequestParam(required = false) String customerPhone,
                               @RequestParam(required = false) Long vehicleId,
                               @RequestParam(required = false) String vehicleDescription,
                               @RequestParam(required = false) String vehiclePlateNumber,
                               @RequestParam String workType,
                               @RequestParam(required = false) String partsJson) throws Exception {
        boolean hasCustomer = StringUtils.hasText(customerName) || StringUtils.hasText(customerPhone);

        List<QuotePartLine> items = parsePartsJson(partsJson);

        Quote quote = quoteService.createQuote(customerName, customerPhone,
                vehicleId,
                hasCustomer ? vehicleDescription : null,
                hasCustomer ? vehiclePlateNumber : null,
                hasCustomer ? null : vehicleDescription,
                workType, items);

        return "redirect:/quotes/" + quote.getId();
    }

    @GetMapping("/{id}")
    public String preview(@PathVariable Long id, Model model) {
        Quote quote = quoteService.get(id);
        List<QuoteItem> items = quoteService.itemsFor(id);
        model.addAttribute("quote", quote);
        model.addAttribute("items", items);
        model.addAttribute("total", quoteService.totalFor(id));
        model.addAttribute("customer", quote.getCustomerId() != null ? customerService.findById(quote.getCustomerId()).orElse(null) : null);
        model.addAttribute("vehicleLabel", vehicleService.labelFor(quote.getVehicleId(), quote.getVehicleNote()));
        return "quote-preview";
    }

    @PostMapping("/{id}/convert")
    public String convert(@PathVariable Long id) {
        Job job = quoteService.convertToJob(id);
        return "redirect:/jobs/" + job.getId() + "/edit";
    }

    private List<QuotePartLine> parsePartsJson(String partsJson) throws Exception {
        if (!StringUtils.hasText(partsJson)) {
            return List.of();
        }
        return objectMapper.readValue(partsJson, new TypeReference<List<QuotePartLine>>() {
        });
    }

    private List<QuoteRow> recentQuoteRows() {
        return quoteService.recentOpenQuotes().stream()
                .map(q -> new QuoteRow(q, vehicleService.labelFor(q.getVehicleId(), q.getVehicleNote()), quoteService.totalFor(q.getId())))
                .toList();
    }

    public record QuoteRow(Quote quote, String vehicleLabel, BigDecimal total) {
    }
}
