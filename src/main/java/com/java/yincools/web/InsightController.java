package com.java.yincools.web;

import com.java.yincools.domain.InsightService;
import com.java.yincools.domain.JobService;
import com.java.yincools.domain.model.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;
    private final JobService jobService;

    @GetMapping("/insights/week")
    public String thisWeek(Model model) {
        model.addAttribute("summary", insightService.weeklySummary(LocalDate.now()));
        return "this-week";
    }

    @GetMapping("/insights/debtors")
    public String debtors(Model model) {
        List<DebtorRow> debtors = insightService.debtorList().stream()
                .map(job -> new DebtorRow(job, customerLabel(job)))
                .toList();
        model.addAttribute("debtors", debtors);
        return "debtors";
    }

    private String customerLabel(Job job) {
        return jobService.customerFor(job)
                .map(c -> c.getName() != null && !c.getName().isBlank() ? c.getName() : c.getPhone())
                .orElse(null);
    }

    public record DebtorRow(Job job, String customerLabel) {
    }
}
