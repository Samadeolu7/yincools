package com.java.yincools.web;

import com.java.yincools.domain.InsightService;
import com.java.yincools.domain.JobService;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.LedgerEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Owner-only oversight: everything tagged as bought on credit from the one
 * regular supplier (see LedgerEntry.partsSupplier), so what Dad agreed to
 * pay can be checked against what the supplier's own tally says later.
 * Gated to ROLE_OWNER in SecurityConfig -- the "shop" account gets a plain
 * 403 here, and it's deliberately not linked from anywhere in Dad's nav.
 */
@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final InsightService insightService;
    private final JobService jobService;

    @GetMapping("/credit")
    public String creditReport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                Model model) {
        LocalDate rangeEnd = end != null ? end : LocalDate.now();
        LocalDate rangeStart = start != null ? start : rangeEnd.minusDays(30);

        List<SupplierEntryRow> rows = insightService.creditSupplierEntries(rangeStart, rangeEnd).stream()
                .map(this::toRow)
                .toList();
        BigDecimal total = rows.stream()
                .map(row -> row.entry().getSignedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("rows", rows);
        model.addAttribute("total", total);
        model.addAttribute("rangeStart", rangeStart);
        model.addAttribute("rangeEnd", rangeEnd);
        return "supplier-report";
    }

    private SupplierEntryRow toRow(LedgerEntry entry) {
        String vehicleLabel = null;
        String customerLabel = null;
        if (entry.getJobId() != null) {
            Job job = jobService.get(entry.getJobId());
            vehicleLabel = jobService.vehicleLabelFor(job);
            customerLabel = jobService.customerFor(job)
                    .map(c -> c.getName() != null && !c.getName().isBlank() ? c.getName() : c.getPhone())
                    .orElse(null);
        }
        return new SupplierEntryRow(entry, customerLabel, vehicleLabel);
    }

    public record SupplierEntryRow(LedgerEntry entry, String customerLabel, String vehicleLabel) {
    }
}
