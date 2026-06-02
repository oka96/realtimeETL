package com.example.realtimeetl.routes;

import java.math.BigDecimal;
import java.util.Objects;

import com.example.realtimeetl.etl.EtlContext;
import com.example.realtimeetl.etl.EtlNode;
import com.example.realtimeetl.order.OrderCreated;

import org.springframework.messaging.Message;

public final class AmountBandNode implements EtlNode {

	public static final String CONTEXT_KEY = "amountBand";
	public static final String SMALL = "SMALL";
	public static final String MEDIUM = "MEDIUM";
	public static final String LARGE = "LARGE";

	private final BigDecimal mediumThreshold;
	private final BigDecimal largeThreshold;

	public AmountBandNode(BigDecimal mediumThreshold, BigDecimal largeThreshold) {
		this.mediumThreshold = Objects.requireNonNull(mediumThreshold, "mediumThreshold must not be null");
		this.largeThreshold = Objects.requireNonNull(largeThreshold, "largeThreshold must not be null");
		if (mediumThreshold.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("mediumThreshold must be positive");
		}
		if (largeThreshold.compareTo(mediumThreshold) <= 0) {
			throw new IllegalArgumentException("largeThreshold must be greater than mediumThreshold");
		}
	}

	@Override
	public Message<?> process(Message<?> message, EtlContext context) {
		Objects.requireNonNull(message, "message must not be null");
		Objects.requireNonNull(context, "context must not be null");
		OrderCreated order = orderPayload(message);
		context.put(CONTEXT_KEY, amountBand(order.amount()));
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

	private String amountBand(BigDecimal amount) {
		if (amount.compareTo(mediumThreshold) < 0) {
			return SMALL;
		}
		if (amount.compareTo(largeThreshold) < 0) {
			return MEDIUM;
		}
		return LARGE;
	}
}
