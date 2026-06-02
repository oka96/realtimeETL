package com.example.realtimeetl.routes;

import java.util.Objects;

import com.example.realtimeetl.etl.EtlContext;
import com.example.realtimeetl.etl.EtlNode;
import com.example.realtimeetl.order.OrderCreated;

import org.springframework.messaging.Message;

public final class RiskScoreNode implements EtlNode {

	public static final String CONTEXT_KEY = "riskScore";

	private final FraudRiskProperties properties;

	public RiskScoreNode(FraudRiskProperties properties) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	@Override
	public Message<?> process(Message<?> message, EtlContext context) {
		Objects.requireNonNull(message, "message must not be null");
		Objects.requireNonNull(context, "context must not be null");
		OrderCreated order = orderPayload(message);
		int score = properties.baseScore();
		if (order.amount().compareTo(properties.highAmountThreshold()) >= 0) {
			score += properties.highAmountScore();
		}
		if (!properties.domesticCurrency().equalsIgnoreCase(order.currency())) {
			score += properties.foreignCurrencyScore();
		}
		context.put(CONTEXT_KEY, score);
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
