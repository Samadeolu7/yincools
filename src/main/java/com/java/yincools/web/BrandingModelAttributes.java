package com.java.yincools.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.List;

/**
 * Business letterhead details for the shared letterhead fragment, available
 * to every view without every controller having to inject and pass them.
 */
@ControllerAdvice
public class BrandingModelAttributes {

    @Value("${app.business.name}")
    private String businessName;

    @Value("${app.business.suffix}")
    private String businessSuffix;

    @Value("${app.business.tagline}")
    private String businessTagline;

    @Value("${app.business.address}")
    private String businessAddress;

    @Value("${app.business.whatsapp}")
    private String businessWhatsapp;

    @Value("${app.business.tel}")
    private String businessTel;

    @Value("${app.business.email}")
    private String businessEmail;

    @Value("${app.business.bankers}")
    private String businessBankersRaw;

    @Value("${app.credit-supplier.name}")
    private String creditSupplierName;

    @ModelAttribute("businessName")
    public String businessName() {
        return businessName;
    }

    @ModelAttribute("businessSuffix")
    public String businessSuffix() {
        return businessSuffix;
    }

    @ModelAttribute("businessTagline")
    public String businessTagline() {
        return businessTagline;
    }

    @ModelAttribute("businessAddress")
    public String businessAddress() {
        return businessAddress;
    }

    @ModelAttribute("businessWhatsapp")
    public String businessWhatsapp() {
        return businessWhatsapp;
    }

    @ModelAttribute("businessTel")
    public String businessTel() {
        return businessTel;
    }

    @ModelAttribute("businessEmail")
    public String businessEmail() {
        return businessEmail;
    }

    @ModelAttribute("businessBankers")
    public List<String> businessBankers() {
        return StringUtils.hasText(businessBankersRaw) ? Arrays.asList(businessBankersRaw.split("\\|")) : List.of();
    }

    @ModelAttribute("creditSupplierName")
    public String creditSupplierName() {
        return creditSupplierName;
    }
}
