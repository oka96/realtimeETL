package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class EtlMessagesTest {

	@Test
	void replacesPayloadAndPreservesHeaders() {
		Message<?> input = MessageBuilder.withPayload("raw")
			.setHeader("source", "orders")
			.build();

		Message<?> output = EtlMessages.replacePayload(input, "mapped");

		assertThat(output.getPayload()).isEqualTo("mapped");
		assertThat(output.getHeaders()).containsEntry("source", "orders");
	}

	@Test
	void mapsPayloadAndPreservesHeaders() {
		Message<?> input = MessageBuilder.withPayload("raw")
			.setHeader("source", "orders")
			.build();

		Message<?> output = EtlMessages.mapPayload(input, payload -> payload + "-mapped");

		assertThat(output.getPayload()).isEqualTo("raw-mapped");
		assertThat(output.getHeaders()).containsEntry("source", "orders");
	}

	@Test
	void rejectsNullMessageWhenReplacingPayload() {
		assertThatThrownBy(() -> EtlMessages.replacePayload(null, "mapped"))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("message must not be null");
	}

	@Test
	void rejectsNullReplacementPayload() {
		Message<?> input = MessageBuilder.withPayload("raw").build();

		assertThatThrownBy(() -> EtlMessages.replacePayload(input, null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("replacement payload must not be null");
	}

	@Test
	void rejectsNullPayloadTransformer() {
		Message<?> input = MessageBuilder.withPayload("raw").build();

		assertThatThrownBy(() -> EtlMessages.mapPayload(input, null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("payload transformer must not be null");
	}
}
