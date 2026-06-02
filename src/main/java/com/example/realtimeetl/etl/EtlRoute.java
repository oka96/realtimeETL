package com.example.realtimeetl.etl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

public final class EtlRoute {

	private static final Pattern ROUTE_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
	private static final String INPUT_PAYLOAD_VALIDATION_NODE = "validateInputPayload";

	private final String name;
	private final Class<?> inputType;
	private final Class<?> outputType;
	private final List<NamedEtlNode> nodes;
	private final Map<String, Object> inputContractHeaders;
	private final Map<String, Object> contractHeaders;

	private EtlRoute(
		String name,
		Class<?> inputType,
		Class<?> outputType,
		List<NamedEtlNode> nodes,
		Map<String, Object> inputContractHeaders,
		Map<String, Object> contractHeaders) {
		this.name = requireRouteName(name);
		this.inputType = Objects.requireNonNull(inputType, "inputType must not be null");
		this.outputType = Objects.requireNonNull(outputType, "outputType must not be null");
		if (nodes.isEmpty()) {
			throw new IllegalArgumentException("Route must contain at least one node");
		}
		validateUniqueNodeNames(name, nodes);
		this.nodes = List.copyOf(nodes);
		this.inputContractHeaders = immutableCustomHeaders(inputContractHeaders);
		this.contractHeaders = immutableCustomHeaders(contractHeaders);
	}

	public static Builder builder(String name) {
		return new Builder(name);
	}

	public String name() {
		return name;
	}

	public List<NamedEtlNode> nodes() {
		return nodes;
	}

	public Class<?> inputType() {
		return inputType;
	}

	public Class<?> outputType() {
		return outputType;
	}

	public Map<String, Object> contractHeaders() {
		return contractHeaders;
	}

	public Map<String, Object> inputContractHeaders() {
		return inputContractHeaders;
	}

	public Message<?> execute(Message<?> input) {
		Objects.requireNonNull(input, "input message must not be null");
		long startedAt = System.nanoTime();
		String traceId = resolveTraceId(input);
		String sourceMessageId = sourceMessageId(input);
		Long sourceMessageTimestamp = input.getHeaders().getTimestamp();
		String inputPayloadType = actualType(input.getPayload());
		validateInputPayload(input.getPayload(), traceId, inputPayloadType, sourceMessageId, sourceMessageTimestamp);
		MessageBuilder<?> inputBuilder = MessageBuilder.fromMessage(input)
			.setHeader(EtlHeaders.ROUTE, name)
			.setHeader(EtlHeaders.TRACE_ID, traceId)
			.setHeader(EtlHeaders.INPUT_PAYLOAD_TYPE, inputPayloadType);
		copySourceMessageHeaders(inputBuilder, sourceMessageId, sourceMessageTimestamp);
		Message<?> current = inputBuilder.build();
		EtlContext context = new EtlContext(name);
		int processedNodes = 0;
		String lastNodeName = null;
		Map<String, Long> nodeDurationsNanos = new LinkedHashMap<>();

		for (NamedEtlNode node : nodes) {
			long nodeStartedAt = System.nanoTime();
			try {
				current = node.process(current, context);
			}
			catch (EtlRouteExecutionException ex) {
				nodeDurationsNanos.put(node.name(), System.nanoTime() - nodeStartedAt);
				throw routeFailure(
					node.name(),
					traceId,
					inputPayloadType,
					sourceMessageId,
					sourceMessageTimestamp,
					processedNodes,
					nodeDurationsNanos,
					ex.getCause());
			}
			catch (RuntimeException ex) {
				nodeDurationsNanos.put(node.name(), System.nanoTime() - nodeStartedAt);
				throw routeFailure(
					node.name(),
					traceId,
					inputPayloadType,
					sourceMessageId,
					sourceMessageTimestamp,
					processedNodes,
					nodeDurationsNanos,
					ex);
			}
			if (current == null) {
				nodeDurationsNanos.put(node.name(), System.nanoTime() - nodeStartedAt);
				throw routeFailure(
					node.name(),
					traceId,
					inputPayloadType,
					sourceMessageId,
					sourceMessageTimestamp,
					processedNodes,
					nodeDurationsNanos,
					new IllegalStateException("Node returned null"));
			}
			nodeDurationsNanos.put(node.name(), System.nanoTime() - nodeStartedAt);
			processedNodes++;
			lastNodeName = node.name();
			current = MessageBuilder.fromMessage(current)
				.setHeader(EtlHeaders.ROUTE, name)
				.setHeader(EtlHeaders.TRACE_ID, traceId)
				.setHeader(EtlHeaders.LAST_NODE, node.name())
				.build();
		}

		Message<?> output = MessageBuilder.fromMessage(current)
			.setHeader(EtlHeaders.NODE_COUNT, processedNodes)
			.setHeader(EtlHeaders.NODE_DURATIONS_NANOS, immutableNodeDurations(nodeDurationsNanos))
			.setHeader(EtlHeaders.DURATION_NANOS, System.nanoTime() - startedAt)
			.setHeader(EtlHeaders.OUTPUT_PAYLOAD_TYPE, current.getPayload().getClass().getName())
			.build();
		validateOutputContractHeaders(
			output,
			lastNodeName,
			traceId,
			sourceMessageId,
			sourceMessageTimestamp,
			inputPayloadType,
			processedNodes,
			nodeDurationsNanos);
		validateOutputPayload(
			output.getPayload(),
			lastNodeName,
			traceId,
			sourceMessageId,
			sourceMessageTimestamp,
			inputPayloadType,
			processedNodes,
			nodeDurationsNanos);
		return output;
	}

	public <I, O> Function<Message<I>, Message<O>> toFunction(Class<I> inputType, Class<O> outputType) {
		Objects.requireNonNull(inputType, "inputType must not be null");
		Objects.requireNonNull(outputType, "outputType must not be null");
		if (!this.inputType.equals(inputType) || !this.outputType.equals(outputType)) {
			throw new IllegalArgumentException("Route '%s' is declared as %s -> %s but function requested %s -> %s"
				.formatted(
					name,
					this.inputType.getName(),
					this.outputType.getName(),
					inputType.getName(),
					outputType.getName()));
		}

		return input -> {
			Objects.requireNonNull(input, "input message must not be null");
			Message<?> output = execute(input);
			Object outputPayload = output.getPayload();
			if (!outputType.isInstance(outputPayload)) {
				String actualType = outputPayload == null ? "null" : outputPayload.getClass().getName();
				throw new IllegalStateException("Route '%s' produced payload type %s but expected %s".formatted(
					name, actualType, outputType.getName()));
			}

			return MessageBuilder.withPayload(outputType.cast(outputPayload))
				.copyHeaders(output.getHeaders())
				.build();
		};
	}

	public static final class Builder {

		private final String name;
		private Class<?> inputType = Object.class;
		private Class<?> outputType = Object.class;
		private final List<NamedEtlNode> nodes = new ArrayList<>();
		private final Map<String, Object> inputContractHeaders = new LinkedHashMap<>();
		private final Map<String, Object> contractHeaders = new LinkedHashMap<>();

		private Builder(String name) {
			this.name = name;
		}

		public Builder node(String name, EtlNode node) {
			Objects.requireNonNull(node, "node must not be null");
			nodes.add(new NamedEtlNode(name, node));
			return this;
		}

		public Builder payloadTypes(Class<?> inputType, Class<?> outputType) {
			this.inputType = Objects.requireNonNull(inputType, "inputType must not be null");
			this.outputType = Objects.requireNonNull(outputType, "outputType must not be null");
			validatePayloadType(this.inputType);
			validatePayloadType(this.outputType);
			return this;
		}

		public Builder transformPayload(String name, Function<Object, Object> transformer) {
			Objects.requireNonNull(transformer, "transformer must not be null");
			return node(name, (message, context) -> EtlMessages.mapPayload(message, transformer));
		}

		public Builder requireInputHeaders(String name, Map<String, Object> values) {
			Map<String, Object> requiredValues = immutableCustomHeaders(values);
			requiredValues.forEach((headerName, value) -> {
				Object existingValue = inputContractHeaders.get(headerName);
				if (existingValue != null && !existingValue.equals(value)) {
					throw new IllegalArgumentException(
						"Route '%s' already requires input header '%s' as '%s' and cannot redeclare it as '%s'"
							.formatted(this.name, headerName, existingValue, value));
				}
			});
			inputContractHeaders.putAll(requiredValues);
			return node(name, (message, context) -> {
				requiredValues.forEach((headerName, expectedValue) -> {
					Object actualValue = message.getHeaders().get(headerName);
					if (!expectedValue.equals(actualValue)) {
						throw new IllegalArgumentException(
							"Input contract header '%s' expected '%s' but received '%s'"
								.formatted(headerName, expectedValue, actualValue));
					}
				});
				return message;
			});
		}

		public <I, O> Builder transformPayload(
			String name,
			Class<I> inputType,
			Class<O> outputType,
			Function<I, O> transformer) {
			Objects.requireNonNull(transformer, "transformer must not be null");
			return transformPayload(name, inputType, outputType, (payload, context) -> transformer.apply(payload));
		}

		public <I, O> Builder transformPayload(
			String name,
			Class<I> inputType,
			Class<O> outputType,
			BiFunction<I, EtlContext, O> transformer) {
			Objects.requireNonNull(transformer, "transformer must not be null");
			validatePayloadType(inputType);
			validatePayloadType(outputType);
			return node(name, (message, context) -> {
				Object inputPayload = message.getPayload();
				if (!inputType.isInstance(inputPayload)) {
					throw new IllegalArgumentException("Node '%s' expected payload type %s but received %s".formatted(
						name,
						inputType.getName(),
						actualType(inputPayload)));
				}

				O outputPayload = transformer.apply(inputType.cast(inputPayload), context);
				if (!outputType.isInstance(outputPayload)) {
					throw new IllegalStateException("Node '%s' produced payload type %s but expected %s".formatted(
						name,
						actualType(outputPayload),
						outputType.getName()));
				}
				return EtlMessages.replacePayload(message, outputPayload);
			});
		}

		public Builder enrichHeader(String name, String headerName, Function<Message<?>, Object> valueResolver) {
			String requiredHeaderName = requireText(headerName, "Header name must not be blank");
			if (EtlHeaders.isRouteManaged(requiredHeaderName)) {
				throw new IllegalArgumentException(
					"Header '%s' is managed by EtlRoute and cannot be customized".formatted(requiredHeaderName));
			}
			Objects.requireNonNull(valueResolver, "valueResolver must not be null");
			return node(name, (message, context) -> {
				Object value = Objects.requireNonNull(
					valueResolver.apply(message),
					"Header value for '%s' must not be null".formatted(requiredHeaderName));
				return MessageBuilder.fromMessage(message)
					.setHeader(requiredHeaderName, value)
					.build();
			});
		}

		public Builder enrichHeaders(String name, Function<Message<?>, Map<String, Object>> valuesResolver) {
			Objects.requireNonNull(valuesResolver, "valuesResolver must not be null");
			return node(name, (message, context) -> {
				Map<String, Object> values = Objects.requireNonNull(
					valuesResolver.apply(message),
					"Header values must not be null");
				return enrichMessageHeaders(message, values);
			});
		}

		public Builder enrichHeaders(String name, Map<String, Object> values) {
			Map<String, Object> requiredValues = immutableCustomHeaders(values);
			requiredValues.forEach((headerName, value) -> {
				Object existingValue = contractHeaders.get(headerName);
				if (existingValue != null && !existingValue.equals(value)) {
					throw new IllegalArgumentException(
						"Route '%s' already declares contract header '%s' as '%s' and cannot redeclare it as '%s'"
							.formatted(this.name, headerName, existingValue, value));
				}
			});
			contractHeaders.putAll(requiredValues);
			return node(name, (message, context) -> enrichMessageHeaders(message, requiredValues));
		}

		public EtlRoute build() {
			return new EtlRoute(name, inputType, outputType, nodes, inputContractHeaders, contractHeaders);
		}
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static String requireRouteName(String value) {
		String routeName = requireText(value, "Route name must not be blank");
		if (!ROUTE_NAME_PATTERN.matcher(routeName).matches()) {
			throw new IllegalArgumentException(
				"Route name must start with a letter and contain only letters, numbers, underscores, or hyphens");
		}
		return routeName;
	}

	private void validateInputPayload(
		Object payload,
		String traceId,
		String inputPayloadType,
		String sourceMessageId,
		Long sourceMessageTimestamp) {
		if (!inputType.isInstance(payload)) {
			throw new EtlRouteExecutionException(
				name,
				INPUT_PAYLOAD_VALIDATION_NODE,
				traceId,
				inputPayloadType,
				sourceMessageId,
				sourceMessageTimestamp,
				0,
				Map.of(),
				new IllegalArgumentException("Route expected input payload type %s but received %s".formatted(
					inputType.getName(),
					inputPayloadType)));
		}
	}

	private void validateOutputPayload(
		Object payload,
		String lastNodeName,
		String traceId,
		String sourceMessageId,
		Long sourceMessageTimestamp,
		String inputPayloadType,
		int completedNodeCount,
		Map<String, Long> nodeDurationsNanos) {
		if (!outputType.isInstance(payload)) {
			throw new EtlRouteExecutionException(
				name,
				lastNodeName,
				traceId,
				inputPayloadType,
				sourceMessageId,
				sourceMessageTimestamp,
				completedNodeCount,
				nodeDurationsNanos,
				new IllegalStateException("Route expected output payload type %s but produced %s".formatted(
					outputType.getName(),
				actualType(payload))));
		}
	}

	private void validateOutputContractHeaders(
		Message<?> output,
		String lastNodeName,
		String traceId,
		String sourceMessageId,
		Long sourceMessageTimestamp,
		String inputPayloadType,
		int completedNodeCount,
		Map<String, Long> nodeDurationsNanos) {
		contractHeaders.forEach((headerName, expectedValue) -> {
			Object actualValue = output.getHeaders().get(headerName);
			if (!expectedValue.equals(actualValue)) {
				throw new EtlRouteExecutionException(
					name,
					lastNodeName,
					traceId,
					inputPayloadType,
					sourceMessageId,
					sourceMessageTimestamp,
					completedNodeCount,
					nodeDurationsNanos,
					new IllegalStateException(
						"Route output contract header '%s' expected '%s' but produced '%s'"
							.formatted(headerName, expectedValue, actualValue)));
			}
		});
	}

	private EtlRouteExecutionException routeFailure(
		String nodeName,
		String traceId,
		String inputPayloadType,
		String sourceMessageId,
		Long sourceMessageTimestamp,
		int completedNodeCount,
		Map<String, Long> nodeDurationsNanos,
		Throwable cause) {
		return new EtlRouteExecutionException(
			name,
			nodeName,
			traceId,
			inputPayloadType,
			sourceMessageId,
			sourceMessageTimestamp,
			completedNodeCount,
			nodeDurationsNanos,
			cause);
	}

	private static String actualType(Object payload) {
		return payload == null ? "null" : payload.getClass().getName();
	}

	private static Map<String, Long> immutableNodeDurations(Map<String, Long> nodeDurationsNanos) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(nodeDurationsNanos));
	}

	private static Map<String, Object> immutableCustomHeaders(Map<String, Object> values) {
		Objects.requireNonNull(values, "Header values must not be null");
		Map<String, Object> requiredValues = new LinkedHashMap<>();
		values.forEach((headerName, value) -> {
			String requiredHeaderName = requireText(headerName, "Header name must not be blank");
			if (EtlHeaders.isRouteManaged(requiredHeaderName)) {
				throw new IllegalArgumentException(
					"Header '%s' is managed by EtlRoute and cannot be customized".formatted(requiredHeaderName));
			}
			requiredValues.put(
				requiredHeaderName,
				Objects.requireNonNull(
					value,
					"Header value for '%s' must not be null".formatted(requiredHeaderName)));
		});
		return Collections.unmodifiableMap(requiredValues);
	}

	private static Message<?> enrichMessageHeaders(Message<?> message, Map<String, Object> values) {
		MessageBuilder<?> builder = MessageBuilder.fromMessage(message);
		immutableCustomHeaders(values).forEach(builder::setHeader);
		return builder.build();
	}

	private static void validatePayloadType(Class<?> payloadType) {
		Objects.requireNonNull(payloadType, "payloadType must not be null");
		if (payloadType.isPrimitive()) {
			throw new IllegalArgumentException("Payload types must be reference types");
		}
	}

	private static void validateUniqueNodeNames(String routeName, List<NamedEtlNode> nodes) {
		Set<String> names = new LinkedHashSet<>();
		for (NamedEtlNode node : nodes) {
			if (!names.add(node.name())) {
				throw new IllegalArgumentException(
					"Route '%s' contains duplicate node name '%s'".formatted(routeName, node.name()));
			}
		}
	}

	private static String resolveTraceId(Message<?> input) {
		Object existingTraceId = input.getHeaders().get(EtlHeaders.TRACE_ID);
		if (existingTraceId != null) {
			String traceId = existingTraceId.toString().trim();
			if (!traceId.isBlank()) {
				return traceId;
			}
		}
		return UUID.randomUUID().toString();
	}

	private static String sourceMessageId(Message<?> input) {
		Object sourceMessageId = input.getHeaders().get(MessageHeaders.ID);
		return sourceMessageId == null ? null : sourceMessageId.toString();
	}

	private static void copySourceMessageHeaders(
		MessageBuilder<?> builder,
		String sourceMessageId,
		Long sourceMessageTimestamp) {
		if (sourceMessageId != null) {
			builder.setHeader(EtlHeaders.SOURCE_MESSAGE_ID, sourceMessageId);
		}
		if (sourceMessageTimestamp != null) {
			builder.setHeader(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP, sourceMessageTimestamp);
		}
	}
}
