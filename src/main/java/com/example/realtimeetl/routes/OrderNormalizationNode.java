package com.example.realtimeetl.routes;

import java.util.Objects;

import com.example.realtimeetl.etl.EtlContext;
import com.example.realtimeetl.etl.EtlMessages;
import com.example.realtimeetl.etl.EtlNode;
import com.example.realtimeetl.order.OrderCreated;

import org.springframework.messaging.Message;

public final class OrderNormalizationNode implements EtlNode {

	@Override
	public Message<?> process(Message<?> message, EtlContext context) {
		Objects.requireNonNull(message, "message must not be null");
		Objects.requireNonNull(context, "context must not be null");
		OrderCreated order = orderPayload(message);
		return EtlMessages.replacePayload(message, new OrderCreated(
			order.orderId().trim(),
			order.customerId().trim(),
			order.amount(),
			order.currency().trim().toUpperCase()));
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
