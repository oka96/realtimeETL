package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.example.realtimeetl.etl.EtlContext;
import com.example.realtimeetl.order.OrderCreated;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class OrderValidationNodeTest {

	private final OrderValidationNode node = new OrderValidationNode();

	@Test
	void acceptsValidOrder() {
		Message<OrderCreated> message = MessageBuilder.withPayload(
			new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), " myr "))
			.build();

		assertThat(node.process(message, new EtlContext("test"))).isSameAs(message);
	}

	@Test
	void rejectsInvalidNodeInputsWithClearMessages() {
		assertThatNullPointerException()
			.isThrownBy(() -> node.process(null, new EtlContext("test")))
			.withMessage("message must not be null");
		assertThatNullPointerException()
			.isThrownBy(() -> node.process(MessageBuilder.withPayload(validOrder()).build(), null))
			.withMessage("context must not be null");
		assertThatThrownBy(() -> node.process(MessageBuilder.withPayload("not-order").build(), new EtlContext("test")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Expected OrderCreated payload but received java.lang.String");
	}

	@Test
	void rejectsInvalidOrdersWithClearMessages() {
		assertInvalid(new OrderCreated("", "C-1", new BigDecimal("1.00"), "MYR"), "Order id is required");
		assertInvalid(new OrderCreated("O-1", "", new BigDecimal("1.00"), "MYR"), "Customer id is required");
		assertInvalid(new OrderCreated("O-1", "C-1", BigDecimal.ZERO, "MYR"), "Order amount must be positive");
		assertInvalid(new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), ""), "Currency is required");
		assertInvalid(
			new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), "MY"),
			"Currency must be a three-letter code");
		assertInvalid(
			new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), "12$"),
			"Currency must be a three-letter code");
	}

	private void assertInvalid(OrderCreated order, String message) {
		assertThatThrownBy(() -> node.process(MessageBuilder.withPayload(order).build(), new EtlContext("test")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(message);
	}

	private static OrderCreated validOrder() {
		return new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), "MYR");
	}
}
