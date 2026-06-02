package com.example.realtimeetl.etl;

import java.util.Objects;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

@Component
public final class StreamBridgeEtlFailureEventPublisher implements EtlFailureEventPublisher {

	private final StreamBridge streamBridge;

	public StreamBridgeEtlFailureEventPublisher(StreamBridge streamBridge) {
		this.streamBridge = Objects.requireNonNull(streamBridge, "streamBridge must not be null");
	}

	@Override
	public boolean publish(EtlFailureEvent event) {
		return streamBridge.send(BINDING_NAME, failureMessage(event));
	}

	static Message<EtlFailureEvent> failureMessage(EtlFailureEvent event) {
		Objects.requireNonNull(event, "event must not be null");
		MessageBuilder<EtlFailureEvent> builder = MessageBuilder.withPayload(event)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, EtlFailureEvent.EVENT_TYPE)
			.setHeader(EtlHeaders.EVENT_VERSION, EtlFailureEvent.EVENT_VERSION)
			.setHeader(EtlHeaders.ROUTE, event.routeName())
			.setHeader(EtlHeaders.LAST_NODE, event.nodeName())
			.setHeader(EtlHeaders.NODE_COUNT, event.completedNodeCount());
		copyHeader(builder, EtlHeaders.TRACE_ID, event.traceId());
		builder.setHeader(EtlHeaders.FAILURE_TIMESTAMP, event.failureTimestamp());
		copyHeader(builder, EtlHeaders.INPUT_PAYLOAD_TYPE, event.inputPayloadType());
		copyHeader(builder, EtlHeaders.SOURCE_MESSAGE_ID, event.sourceMessageId());
		copyHeader(builder, EtlHeaders.SOURCE_MESSAGE_TIMESTAMP, event.sourceMessageTimestamp());
		return builder.build();
	}

	private static void copyHeader(MessageBuilder<?> builder, String headerName, Object value) {
		if (value != null) {
			builder.setHeader(headerName, value);
		}
	}
}
