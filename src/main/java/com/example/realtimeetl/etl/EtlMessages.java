package com.example.realtimeetl.etl;

import java.util.Objects;
import java.util.function.Function;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public final class EtlMessages {

	private EtlMessages() {
	}

	public static Message<?> replacePayload(Message<?> message, Object payload) {
		Objects.requireNonNull(message, "message must not be null");
		Objects.requireNonNull(payload, "replacement payload must not be null");
		return MessageBuilder.withPayload(payload)
			.copyHeaders(message.getHeaders())
			.build();
	}

	public static Message<?> mapPayload(Message<?> message, Function<Object, Object> transformer) {
		Objects.requireNonNull(message, "message must not be null");
		Objects.requireNonNull(transformer, "payload transformer must not be null");
		return replacePayload(message, transformer.apply(message.getPayload()));
	}
}
