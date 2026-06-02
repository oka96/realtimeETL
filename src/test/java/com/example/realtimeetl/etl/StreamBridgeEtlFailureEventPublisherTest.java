package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.MimeTypeUtils;

class StreamBridgeEtlFailureEventPublisherTest {

	@Test
	void rejectsMissingStreamBridge() {
		assertThatNullPointerException()
			.isThrownBy(() -> new StreamBridgeEtlFailureEventPublisher(null))
			.withMessage("streamBridge must not be null");
	}

	@Test
	void rejectsMissingFailureEvent() {
		assertThatNullPointerException()
			.isThrownBy(() -> StreamBridgeEtlFailureEventPublisher.failureMessage(null))
			.withMessage("event must not be null");
	}

	@Test
	void buildsFailureMessageWithContractAndOperationalHeaders() {
		EtlFailureEvent event = failureEvent(
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L);

		Message<EtlFailureEvent> message = StreamBridgeEtlFailureEventPublisher.failureMessage(event);

		assertThat(message.getPayload()).isSameAs(event);
		assertThat(message.getHeaders())
			.containsEntry(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.containsEntry(EtlHeaders.EVENT_TYPE, EtlFailureEvent.EVENT_TYPE)
			.containsEntry(EtlHeaders.EVENT_VERSION, EtlFailureEvent.EVENT_VERSION)
			.containsEntry(EtlHeaders.ROUTE, "orderInvoice")
			.containsEntry(EtlHeaders.LAST_NODE, "validateOrder")
			.containsEntry(EtlHeaders.NODE_COUNT, 1)
			.containsEntry(EtlHeaders.TRACE_ID, "trace-1")
			.containsEntry(EtlHeaders.FAILURE_TIMESTAMP, 1_780_000_000_500L)
			.containsEntry(EtlHeaders.INPUT_PAYLOAD_TYPE, "com.example.OrderCreated")
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_ID, "source-message-1")
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP, 1_780_000_000_000L);
	}

	@Test
	void omitsOptionalProvenanceHeadersWhenFailureEventDoesNotHaveThem() {
		Message<EtlFailureEvent> message = StreamBridgeEtlFailureEventPublisher.failureMessage(
			failureEvent(null, null, null));

		assertThat(message.getHeaders())
			.doesNotContainKeys(
				EtlHeaders.INPUT_PAYLOAD_TYPE,
				EtlHeaders.SOURCE_MESSAGE_ID,
				EtlHeaders.SOURCE_MESSAGE_TIMESTAMP)
			.containsEntry(EtlHeaders.TRACE_ID, "trace-1")
			.containsEntry(EtlHeaders.FAILURE_TIMESTAMP, 1_780_000_000_500L);
	}

	private static EtlFailureEvent failureEvent(
		String inputPayloadType,
		String sourceMessageId,
		Long sourceMessageTimestamp) {
		return new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			inputPayloadType,
			sourceMessageId,
			sourceMessageTimestamp,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order");
	}
}
