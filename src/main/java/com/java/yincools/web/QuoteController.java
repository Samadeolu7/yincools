package com.java.yincools.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.java.yincools.domain.CustomerService;
import com.java.yincools.domain.QuotePartLine;
import com.java.yincools.domain.QuoteService;
import com.java.yincools.domain.VehicleService;
import com.java.yincools.domain.model.Customer;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.Quote;
import com.java.yincools.domain.model.QuoteItem;
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
@RequestMapping("/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private static final List<String> WORK_TYPES =
            List.of("REGAS", "COMPRESSOR", "CONDENSER", "FAN", "DIAGNOSIS", "OTHER");

    private final QuoteService quoteService;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final ObjectMapper objectMapper;

    @Value("${app.business.name}")
    private String businessName;

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
        BigDecimal total = quoteService.totalFor(id);
        Customer customer = quote.getCustomerId() != null ? customerService.findById(quote.getCustomerId()).orElse(null) : null;
        String vehicleLabel = vehicleService.labelFor(quote.getVehicleId(), quote.getVehicleNote());
        String quoteText = buildQuoteText(quote, customer, vehicleLabel, items, total);

        model.addAttribute("quote", quote);
        model.addAttribute("quoteNumber", String.format("QT-%06d", quote.getId()));
        model.addAttribute("items", items);
        model.addAttribute("total", total);
        model.addAttribute("customer", customer);
        model.addAttribute("vehicleLabel", vehicleLabel);
        model.addAttribute("quoteText", quoteText);
        model.addAttribute("whatsappLink", whatsAppLink(customer, quoteText));
        model.addAttribute("mailtoLink", mailtoLink(businessName + " Quote", quoteText));
        return "quote-preview";
    }

    @PostMapping("/{id}/convert")
    public String convert(@PathVariable Long id) {
        Job job = quoteService.convertToJob(id);
        return "redirect:/jobs/" + job.getId() + "/edit";
    }

    /**
     * A quote is editable at any time -- including after it's already
     * become a job (see QuoteService.editQuote). Same form as New Quote,
     * just prefilled with the current customer/vehicle/work type/items.
     */
    @GetMapping("/{id}/edit")
    public String editQuoteForm(@PathVariable Long id, Model model) throws Exception {
        Quote quote = quoteService.get(id);
        Customer customer = quote.getCustomerId() != null ? customerService.findById(quote.getCustomerId()).orElse(null) : null;
        String vehiclePlateNumber = quote.getVehicleId() != null
                ? vehicleService.findById(quote.getVehicleId()).map(v -> v.getPlateNumber()).orElse(null)
                : null;
        List<QuotePartLine> lines = quoteService.itemsFor(id).stream()
                .map(item -> new QuotePartLine(item.getPartName(), item.getAmount()))
                .toList();

        model.addAttribute("quote", quote);
        model.addAttribute("workTypes", WORK_TYPES);
        model.addAttribute("customer", customer);
        model.addAttribute("vehicleLabel", vehicleService.labelFor(quote.getVehicleId(), quote.getVehicleNote()));
        model.addAttribute("vehiclePlateNumber", vehiclePlateNumber);
        model.addAttribute("itemsJson", objectMapper.writeValueAsString(lines));
        return "edit-quote";
    }

    @PostMapping("/{id}/edit")
    public String editQuote(@PathVariable Long id,
                             @RequestParam(required = false) String customerName,
                             @RequestParam(required = false) String customerPhone,
                             @RequestParam(required = false) Long vehicleId,
                             @RequestParam(required = false) String vehicleDescription,
                             @RequestParam(required = false) String vehiclePlateNumber,
                             @RequestParam String workType,
                             @RequestParam(required = false) String partsJson) throws Exception {
        boolean hasCustomer = StringUtils.hasText(customerName) || StringUtils.hasText(customerPhone);
        List<QuotePartLine> items = parsePartsJson(partsJson);

        quoteService.editQuote(id, customerName, customerPhone,
                vehicleId,
                hasCustomer ? vehicleDescription : null,
                hasCustomer ? vehiclePlateNumber : null,
                hasCustomer ? null : vehicleDescription,
                workType, items);

        return "redirect:/quotes/" + id;
    }

    private String buildQuoteText(Quote quote, Customer customer, String vehicleLabel, List<QuoteItem> items, BigDecimal total) {
        StringBuilder sb = new StringBuilder();
        sb.append(businessName).append(" Quote\n");
        sb.append(quote.getDate()).append("\n");
        if (customer != null && StringUtils.hasText(customer.getName())) {
            sb.append("Customer: ").append(customer.getName()).append("\n");
        }
        if (StringUtils.hasText(vehicleLabel)) {
            sb.append("Vehicle: ").append(vehicleLabel).append("\n");
        }
        sb.append("Work: ").append(quote.getWorkType()).append("\n\n");
        for (QuoteItem item : items) {
            sb.append(item.getPartName()).append(": NGN ").append(item.getAmount()).append("\n");
        }
        sb.append("\nTotal: NGN ").append(total).append("\n");
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

    private String mailtoLink(String subject, String body) {
        String encodedSubject = URLEncoder.encode(subject, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8).replace("+", "%20");
        return "mailto:?subject=" + encodedSubject + "&body=" + encodedBody;
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
