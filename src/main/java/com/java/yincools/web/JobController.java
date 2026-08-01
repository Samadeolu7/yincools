package com.java.yincools.web;

import com.java.yincools.domain.JobService;
import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.Job;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private static final List<String> WORK_TYPES =
            List.of("REGAS", "COMPRESSOR", "CONDENSER", "FAN", "DIAGNOSIS", "OTHER");
    private static final List<String> QUICK_AMOUNTS =
            List.of("5000", "10000", "15000", "20000");

    private final JobService jobService;

    @GetMapping("/new")
    public String newJobForm(Model model) {
        model.addAttribute("workTypes", WORK_TYPES);
        model.addAttribute("quickAmounts", QUICK_AMOUNTS);
        model.addAttribute("recentCustomers", jobService.recentCustomers());
        return "new-job";
    }

    @PostMapping
    public String createJob(@RequestParam(required = false) String customerName,
                             @RequestParam(required = false) String customerPhone,
                             @RequestParam(required = false) String vehicleDescription,
                             @RequestParam String workType,
                             @RequestParam BigDecimal charge,
                             @RequestParam(required = false) BigDecimal partsCost,
                             @RequestParam(required = false) BigDecimal paid) {
        Job job = jobService.createJob(customerName, customerPhone, vehicleDescription,
                workType, charge, partsCost, paid);
        return "redirect:/jobs/" + job.getId() + "/receipt";
    }

    @GetMapping("/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model) {
        Job job = jobService.get(id);
        Customer customer = jobService.customerFor(job).orElse(null);
        String receiptText = buildReceiptText(job, customer);

        model.addAttribute("job", job);
        model.addAttribute("customer", customer);
        model.addAttribute("receiptText", receiptText);
        model.addAttribute("whatsappLink", whatsAppLink(customer, receiptText));
        return "receipt";
    }

    private String buildReceiptText(Job job, Customer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append("AC Tech Job Receipt\n");
        sb.append(job.getDate()).append("\n");
        if (customer != null && StringUtils.hasText(customer.getName())) {
            sb.append("Customer: ").append(customer.getName()).append("\n");
        }
        if (StringUtils.hasText(job.getVehicleDescription())) {
            sb.append("Vehicle: ").append(job.getVehicleDescription()).append("\n");
        }
        sb.append("Work: ").append(job.getWorkType()).append("\n");
        sb.append("Charge: NGN ").append(job.getCachedCharge()).append("\n");
        sb.append("Paid: NGN ").append(job.getCachedPaid()).append("\n");
        sb.append("Balance: NGN ").append(job.getCachedBalance()).append("\n");
        return sb.toString();
    }

    private String whatsAppLink(Customer customer, String text) {
        if (customer == null || !StringUtils.hasText(customer.getPhone())) {
            return null;
        }
        String digits = customer.getPhone().replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            digits = "234" + digits.substring(1);
        }
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return "https://wa.me/" + digits + "?text=" + encoded;
    }
}
