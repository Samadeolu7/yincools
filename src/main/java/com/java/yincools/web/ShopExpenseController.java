package com.java.yincools.web;

import com.java.yincools.domain.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class ShopExpenseController {

    private final LedgerService ledgerService;

    @GetMapping("/expenses/new")
    public String newExpenseForm() {
        return "new-expense";
    }

    @PostMapping("/expenses")
    public String createExpense(@RequestParam BigDecimal amount,
                                 @RequestParam(required = false) String note) {
        ledgerService.recordShopExpense(amount, note);
        return "redirect:/expenses/new?saved";
    }
}
