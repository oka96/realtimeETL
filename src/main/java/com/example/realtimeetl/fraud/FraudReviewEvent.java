package com.example.realtimeetl.fraud;

import java.math.BigDecimal;

public record FraudReviewEvent(
	String reviewId,
	String orderId,
	String customerId,
	BigDecimal amount,
	int riskScore,
	String decision
) {
}
