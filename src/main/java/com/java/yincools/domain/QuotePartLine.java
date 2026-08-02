package com.java.yincools.domain;

import java.math.BigDecimal;

/** One row of a quote's itemized part/charge table -- "Compressor: NGN 5,000". */
public record QuotePartLine(String partName, BigDecimal amount) {
}
