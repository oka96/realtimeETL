package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.example.realtimeetl.etl.EtlContext;
import com.example.realtimeetl.order.OrderCreated;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

class AmountBandNodeTest {

	@Test
	void classifiesOrderAmountsIntoBands() {
		AmountBandNode node = new AmountBandNode(new BigDecimal("100.00"), new BigDecimal("500.00"));

		assertBand(node, new BigDecimal("99.99"), AmountBandNode.SMALL);
		assertBand(node, new BigDecimal("100.00"), AmountBandNode.MEDIUM);
		assertBand(node, new BigDecimal("499.99"), AmountBandNode.MEDIUM);
		assertBand(node, new BigDecimal("500.00"), AmountBandNode.LARGE);
	}

	@Test
	void rejectsInvalidThresholds() {
		assertThatThrownBy(() -> new AmountBandNode(BigDecimal.ZERO, new BigDecimal("500.00")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("mediumThreshold must be positive");
		assertThatThrownBy(() -> new AmountBandNode(new BigDecimal("100.00"), new BigDecimal("100.00")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("largeThreshold must be greater than mediumThreshold");
	}

	@Test
	void rejectsInvalidNodeInputsWithClearMessages() {
		AmountBandNode node = new AmountBandNode(new BigDecimal("100.00"), new BigDecimal("500.00"));

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

	private void assertBand(AmountBandNode node, BigDecimal amount, String expectedBand) {
		EtlContext context = new EtlContext(RouteNames.ORDER_ANALYTICS);

		node.process(MessageBuilder.withPayload(new OrderCreated("O-1", "C-1", amount, "MYR")).build(), context);

		assertThat(context.get(AmountBandNode.CONTEXT_KEY)).contains(expectedBand);
	}

	private static OrderCreated validOrder() {
		return new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), "MYR");
	}
}
