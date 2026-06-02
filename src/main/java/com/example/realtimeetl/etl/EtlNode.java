package com.example.realtimeetl.etl;

import org.springframework.messaging.Message;

@FunctionalInterface
public interface EtlNode {

	/**
	 * Processes a non-null message inside a non-null route context and returns the
	 * next message for the route.
	 */
	Message<?> process(Message<?> message, EtlContext context);
}
