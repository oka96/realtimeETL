package com.example.realtimeetl.etl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EtlRouteExecutionException extends RuntimeException {

	private final String routeName;
	private final String nodeName;
	private final String traceId;
	private final String inputPayloadType;
	private final String sourceMessageId;
	private final Long sourceMessageTimestamp;
	private final int completedNodeCount;
	private final Map<String, Long> nodeDurationsNanos;

	public EtlRouteExecutionException(String routeName, String nodeName, Throwable cause) {
		this(routeName, nodeName, null, cause);
	}

	public EtlRouteExecutionException(String routeName, String nodeName, String traceId, Throwable cause) {
		this(routeName, nodeName, traceId, null, null, 0, Map.of(), cause);
	}

	public EtlRouteExecutionException(
		String routeName,
		String nodeName,
		String traceId,
		int completedNodeCount,
		Map<String, Long> nodeDurationsNanos,
		Throwable cause) {
		this(routeName, nodeName, traceId, null, null, completedNodeCount, nodeDurationsNanos, cause);
	}

	public EtlRouteExecutionException(
		String routeName,
		String nodeName,
		String traceId,
		String sourceMessageId,
		Long sourceMessageTimestamp,
		int completedNodeCount,
		Map<String, Long> nodeDurationsNanos,
		Throwable cause) {
		this(routeName, nodeName, traceId, null, sourceMessageId, sourceMessageTimestamp,
			completedNodeCount, nodeDurationsNanos, cause);
	}

	public EtlRouteExecutionException(
		String routeName,
		String nodeName,
		String traceId,
		String inputPayloadType,
		String sourceMessageId,
		Long sourceMessageTimestamp,
		int completedNodeCount,
		Map<String, Long> nodeDurationsNanos,
		Throwable cause) {
		super(message(routeName, nodeName, traceId, cause), Objects.requireNonNull(cause, "cause must not be null"));
		if (completedNodeCount < 0) {
			throw new IllegalArgumentException("completedNodeCount must not be negative");
		}
		if (sourceMessageTimestamp != null && sourceMessageTimestamp < 0) {
			throw new IllegalArgumentException("sourceMessageTimestamp must not be negative");
		}
		this.routeName = requireText(routeName, "routeName must not be blank");
		this.nodeName = requireText(nodeName, "nodeName must not be blank");
		this.traceId = traceId;
		this.inputPayloadType = inputPayloadType;
		this.sourceMessageId = sourceMessageId;
		this.sourceMessageTimestamp = sourceMessageTimestamp;
		this.completedNodeCount = completedNodeCount;
		this.nodeDurationsNanos = immutableNodeDurations(nodeDurationsNanos);
	}

	public String routeName() {
		return routeName;
	}

	public String nodeName() {
		return nodeName;
	}

	public String traceId() {
		return traceId;
	}

	public String inputPayloadType() {
		return inputPayloadType;
	}

	public String sourceMessageId() {
		return sourceMessageId;
	}

	public Long sourceMessageTimestamp() {
		return sourceMessageTimestamp;
	}

	public int completedNodeCount() {
		return completedNodeCount;
	}

	public Map<String, Long> nodeDurationsNanos() {
		return nodeDurationsNanos;
	}

	private static String message(String routeName, String nodeName, String traceId, Throwable cause) {
		Objects.requireNonNull(cause, "cause must not be null");
		String baseMessage = "Route '%s' failed at node '%s'".formatted(routeName, nodeName);
		if (traceId != null && !traceId.isBlank()) {
			baseMessage += " for trace '%s'".formatted(traceId);
		}
		String causeMessage = cause.getMessage();
		if (causeMessage == null || causeMessage.isBlank()) {
			return baseMessage;
		}
		return baseMessage + ": " + causeMessage;
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static Map<String, Long> immutableNodeDurations(Map<String, Long> nodeDurationsNanos) {
		Objects.requireNonNull(nodeDurationsNanos, "nodeDurationsNanos must not be null");
		Map<String, Long> durations = new LinkedHashMap<>();
		nodeDurationsNanos.forEach((nodeName, durationNanos) -> {
			if (nodeName == null || nodeName.isBlank()) {
				throw new IllegalArgumentException("node duration names must not be blank");
			}
			if (durationNanos == null) {
				throw new IllegalArgumentException("node duration for '%s' must not be null".formatted(nodeName));
			}
			if (durationNanos < 0) {
				throw new IllegalArgumentException("node duration for '%s' must not be negative".formatted(nodeName));
			}
			durations.put(nodeName, durationNanos);
		});
		return Collections.unmodifiableMap(durations);
	}
}
