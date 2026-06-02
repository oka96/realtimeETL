package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class NamedEtlNodeTest {

	@Test
	void delegatesToCustomNodeWhenInputsAreValid() {
		Message<?> input = MessageBuilder.withPayload("raw").build();
		EtlContext context = new EtlContext("customRoute");
		NamedEtlNode node = new NamedEtlNode(
			"uppercase",
			(message, routeContext) -> EtlMessages.replacePayload(message, message.getPayload().toString().toUpperCase()));

		Message<?> output = node.process(input, context);

		assertThat(output.getPayload()).isEqualTo("RAW");
		assertThat(node.name()).isEqualTo("uppercase");
	}

	@Test
	void rejectsNullMessageBeforeCallingCustomNode() {
		AtomicBoolean delegateCalled = new AtomicBoolean(false);
		NamedEtlNode node = new NamedEtlNode("customNode", (message, context) -> {
			delegateCalled.set(true);
			return message;
		});

		assertThatNullPointerException()
			.isThrownBy(() -> node.process(null, new EtlContext("customRoute")))
			.withMessage("message must not be null");
		assertThat(delegateCalled).isFalse();
	}

	@Test
	void rejectsNullContextBeforeCallingCustomNode() {
		AtomicBoolean delegateCalled = new AtomicBoolean(false);
		NamedEtlNode node = new NamedEtlNode("customNode", (message, context) -> {
			delegateCalled.set(true);
			return message;
		});

		assertThatNullPointerException()
			.isThrownBy(() -> node.process(MessageBuilder.withPayload("raw").build(), null))
			.withMessage("context must not be null");
		assertThat(delegateCalled).isFalse();
	}
}
