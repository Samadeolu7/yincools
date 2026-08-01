package com.java.yincools.web;

import com.java.yincools.domain.JobService;
import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.business.name}")
    private String businessName;

    @GetMapping("/new")
    public String newJobForm(Model model) {
        model.addAttribute("workTypes", WORK_TYPES);
        model.addAttribute("quickAmounts", QUICK_AMOUNTS);
        model.addAttribute("recentCustomers", jobService.recentCustomers());
        jobService.lastJob().ifPresent(job -> {
            model.addAttribute("lastJob", job);
            model.addAttribute("lastJobCustomer", jobService.customerFor(job).orElse(null));
            model.addAttribute("lastJobVehicleLabel", jobService.vehicleLabelFor(job));
        });
        return "new-job";
    }

    @PostMapping
    public String createJob(@RequestParam(required = false) String customerName,
                             @RequestParam(required = false) String customerPhone,
                             @RequestParam(required = false) Long vehicleId,
                             @RequestParam(required = false) String vehicleDescription,
                             @RequestParam(required = false) String vehiclePlateNumber,
                             @RequestParam(defaultValue = "false") boolean sharedPartsCost,
                             @RequestParam String workType,
                             @RequestParam BigDecimal charge,
                             @RequestParam(required = false) BigDecimal partsCost,
                             @RequestParam(required = false) String partsNote,
                             @RequestParam(required = false) BigDecimal paid) {
        boolean hasCustomer = StringUtils.hasText(customerName) || StringUtils.hasText(customerPhone);
        boolean logAsSharedCost = sharedPartsCost && hasCustomer
                && partsCost != null && partsCost.compareTo(BigDecimal.ZERO) != 0;

        Job job = jobService.createJob(customerName, customerPhone,
                vehicleId,
                hasCustomer ? vehicleDescription : null,
                hasCustomer ? vehiclePlateNumber : null,
                hasCustomer ? null : vehicleDescription,
                workType, charge, logAsSharedCost ? BigDecimal.ZERO : partsCost, partsNote, paid);

        if (logAsSharedCost) {
            jobService.recordSharedPartsCost(job.getCustomerId(), partsCost, partsNote);
        }

        return "redirect:/jobs/" + job.getId() + "/receipt";
    }

    @GetMapping("/{id}/edit")
    public String editJobForm(@PathVariable Long id, Model model) {
        Job job = jobService.get(id);
        model.addAttribute("job", job);
        model.addAttribute("customer", jobService.customerFor(job).orElse(null));
        model.addAttribute("vehicleLabel", jobService.vehicleLabelFor(job));
        model.addAttribute("partsCost", jobService.partsCostFor(id));
        model.addAttribute("workTypes", WORK_TYPES);
        model.addAttribute("quickAmounts", QUICK_AMOUNTS);
        return "edit-job";
    }

    @PostMapping("/{id}/edit")
    public String editJob(@PathVariable Long id,
                           @RequestParam BigDecimal charge,
                           @RequestParam(required = false) BigDecimal partsCost,
                           @RequestParam(required = false) BigDecimal paid) {
        jobService.editJob(id, charge, partsCost == null ? BigDecimal.ZERO : partsCost);
        jobService.recordPayment(id, paid == null ? BigDecimal.ZERO : paid);
        return "redirect:/jobs/" + id + "/receipt";
    }

    @PostMapping("/{id}/void")
    public String voidJob(@PathVariable Long id) {
        jobService.voidJob(id);
        return "redirect:/jobs/new";
    }

    @GetMapping("/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model) {
        Job job = jobService.get(id);
        Customer customer = jobService.customerFor(job).orElse(null);
        String vehicleLabel = jobService.vehicleLabelFor(job);
        String receiptText = buildReceiptText(job, customer, vehicleLabel);

        model.addAttribute("job", job);
        model.addAttribute("customer", customer);
        model.addAttribute("receiptText", receiptText);
        model.addAttribute("whatsappLink", whatsAppLink(customer, receiptText));
        return "receipt";
    }

    private String buildReceiptText(Job job, Customer customer, String vehicleLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append(businessName).append(" Job Receipt\n");
        sb.append(job.getDate()).append("\n");
        if (customer != null && StringUtils.hasText(customer.getName())) {
            sb.append("Customer: ").append(customer.getName()).append("\n");
        }
        if (StringUtils.hasText(vehicleLabel)) {
            sb.append("Vehicle: ").append(vehicleLabel).append("\n");
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
