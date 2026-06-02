package com.example.realtimeetl.analytics;

import java.math.BigDecimal;

public record OrderAnalyticsEvent(
	String orderId,
	String customerId,
	BigDecimal amount,
	String currency,
	String amountBand
) {
}
