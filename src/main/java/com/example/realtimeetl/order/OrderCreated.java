package com.example.realtimeetl.order;

import java.math.BigDecimal;

public record OrderCreated(
	String orderId,
	String customerId,
	BigDecimal amount,
	String currency
) {
}
