package com.example.realtimeetl.routes;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

import com.example.realtimeetl.etl.EtlContext;
import com.example.realtimeetl.etl.EtlNode;
import com.example.realtimeetl.order.OrderCreated;

import org.springframework.messaging.Message;

public final class OrderValidationNode implements EtlNode {

	private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("[A-Za-z]{3}");

	@Override
	public Message<?> process(Message<?> message, EtlContext context) {
		Objects.requireNonNull(message, "message must not be null");
		Objects.requireNonNull(context, "context must not be null");
		OrderCreated order = orderPayload(message);
		if (order.orderId() == null || order.orderId().isBlank()) {
			throw new IllegalArgumentException("Order id is required");
		}
		if (order.customerId() == null || order.customerId().isBlank()) {
			throw new IllegalArgumentException("Customer id is required");
		}
		if (order.amount() == null || order.amount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Order amount must be positive");
		}
		if (order.currency() == null || order.currency().isBlank()) {
			throw new IllegalArgumentException("Currency is required");
		}
		if (!CURRENCY_CODE_PATTERN.matcher(order.currency().trim()).matches()) {
			throw new IllegalArgumentException("Currency must be a three-letter code");
		}
		return message;
	}

	private static OrderCreated orderPayload(Message<?> message) {
		Object payload = message.getPayload();
		if (!(payload instanceof OrderCreated order)) {
			throw new IllegalArgumentException(
				"Expected OrderCreated payload but received %s".formatted(actualType(payload)));
		}
		return order;
	}

	private static String actualType(Object payload) {
		return payload == null ? "null" : payload.getClass().getName();
	}
}
