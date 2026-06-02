package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.example.realtimeetl.etl.EtlContext;
import com.example.realtimeetl.order.OrderCreated;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

class RiskScoreNodeTest {

	@Test
	void rejectsNullProperties() {
		assertThatNullPointerException()
			.isThrownBy(() -> new RiskScoreNode(null))
			.withMessage("properties must not be null");
	}

	@Test
	void calculatesRiskScoreFromConfiguredPolicy() {
		FraudRiskProperties properties = new FraudRiskProperties(
			10,
			new BigDecimal("100.00"),
			30,
			"USD",
			7,
			40);
		RiskScoreNode node = new RiskScoreNode(properties);
		EtlContext context = new EtlContext(RouteNames.FRAUD_REVIEW);

		node.process(MessageBuilder.withPayload(
			new OrderCreated("O-1", "C-1", new BigDecimal("120.00"), "MYR"))
			.build(), context);

		assertThat(context.get(RiskScoreNode.CONTEXT_KEY)).contains(47);
	}

	@Test
	void rejectsInvalidNodeInputsWithClearMessages() {
		RiskScoreNode node = new RiskScoreNode(defaultProperties());

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

	private static FraudRiskProperties defaultProperties() {
		return new FraudRiskProperties(
			10,
			new BigDecimal("100.00"),
			30,
			"USD",
			7,
			40);
	}

	private static OrderCreated validOrder() {
		return new OrderCreated("O-1", "C-1", new BigDecimal("1.00"), "USD");
	}
}
