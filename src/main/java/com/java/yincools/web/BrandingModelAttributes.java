package com.java.yincools.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Business name/phone/address for the shared letterhead fragment, available
 * to every view without every controller having to inject and pass them.
 */
@ControllerAdvice
public class BrandingModelAttributes {

    @Value("${app.business.name}")
    private String businessName;

    @Value("${app.business.phone}")
    private String businessPhone;

    @Value("${app.business.address}")
    private String businessAddress;

    @ModelAttribute("businessName")
    public String businessName() {
        return businessName;
    }

    @ModelAttribute("businessPhone")
    public String businessPhone() {
        return businessPhone;
    }

    @ModelAttribute("businessAddress")
    public String businessAddress() {
        return businessAddress;
    }
}
