package com.example.realtimeetl.invoice;

import java.math.BigDecimal;

public record InvoiceEvent(
	String invoiceId,
	String orderId,
	String customerId,
	BigDecimal amount,
	String currency,
	String status
) {
}
