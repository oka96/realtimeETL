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

class OrderNormalizationNodeTest {

	private final OrderNormalizationNode node = new OrderNormalizationNode();

	@Test
	void trimsIdentifiersAndUppercasesCurrency() {
		Message<OrderCreated> message = MessageBuilder.withPayload(
			new OrderCreated(" O-1 ", " C-1 ", new BigDecimal("1.00"), " myr "))
			.setHeader("source", "test")
			.build();

		Message<?> normalized = node.process(message, new EtlContext("test"));

		assertThat(normalized.getPayload())
			.isEqualTo(new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), "MYR"));
		assertThat(normalized.getHeaders()).containsEntry("source", "test");
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

	private static OrderCreated validOrder() {
		return new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), "MYR");
	}
}
