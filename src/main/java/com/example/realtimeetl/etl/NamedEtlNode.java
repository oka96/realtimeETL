package com.example.realtimeetl.etl;

import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;

public final class NamedEtlNode implements EtlNode {

	private static final Pattern NODE_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

	private final String name;
	private final EtlNode delegate;

	public NamedEtlNode(String name, EtlNode delegate) {
		this.name = requireNodeName(name);
		this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
	}

	public String name() {
		return name;
	}

	@Override
	public Message<?> process(Message<?> message, EtlContext context) {
		Objects.requireNonNull(message, "message must not be null");
		Objects.requireNonNull(context, "context must not be null");
		return delegate.process(message, context);
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static String requireNodeName(String value) {
		String nodeName = requireText(value, "Node name must not be blank");
		if (!NODE_NAME_PATTERN.matcher(nodeName).matches()) {
			throw new IllegalArgumentException(
				"Node name must start with a letter and contain only letters, numbers, underscores, or hyphens");
		}
		return nodeName;
	}
}
