package com.example.realtimeetl.etl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record EtlFailureEvent(
	String routeName,
	String nodeName,
	String traceId,
	long failureTimestamp,
	String inputPayloadType,
	String sourceMessageId,
	Long sourceMessageTimestamp,
	int completedNodeCount,
	Map<String, Long> nodeDurationsNanos,
	String errorType,
	String errorMessage
) {

	public static final String EVENT_TYPE = "etl-route-failure";
	public static final String EVENT_VERSION = "v1";

	public EtlFailureEvent {
		routeName = requireText(routeName, "routeName must not be blank");
		nodeName = requireText(nodeName, "nodeName must not be blank");
		traceId = requireText(traceId, "traceId must not be blank");
		inputPayloadType = requireOptionalText(inputPayloadType, "inputPayloadType must not be blank when present");
		sourceMessageId = requireOptionalText(sourceMessageId, "sourceMessageId must not be blank when present");
		errorType = requireText(errorType, "errorType must not be blank");
		errorMessage = requireText(errorMessage, "errorMessage must not be blank");
		if (failureTimestamp < 0) {
			throw new IllegalArgumentException("failureTimestamp must not be negative");
		}
		if (sourceMessageTimestamp != null && sourceMessageTimestamp < 0) {
			throw new IllegalArgumentException("sourceMessageTimestamp must not be negative");
		}
		if (completedNodeCount < 0) {
			throw new IllegalArgumentException("completedNodeCount must not be negative");
		}
		nodeDurationsNanos = immutableNodeDurations(nodeDurationsNanos);
	}

	public static EtlFailureEvent from(EtlRouteExecutionException failure) {
		return from(failure, System.currentTimeMillis());
	}

	public static EtlFailureEvent from(EtlRouteExecutionException failure, long failureTimestamp) {
		Throwable cause = failure.getCause();
		return new EtlFailureEvent(
			failure.routeName(),
			failure.nodeName(),
			failure.traceId(),
			failureTimestamp,
			failure.inputPayloadType(),
			failure.sourceMessageId(),
			failure.sourceMessageTimestamp(),
			failure.completedNodeCount(),
			failure.nodeDurationsNanos(),
			errorType(failure, cause),
			errorMessage(failure, cause));
	}

	private static String errorType(EtlRouteExecutionException failure, Throwable cause) {
		return cause == null ? failure.getClass().getName() : cause.getClass().getName();
	}

	private static String errorMessage(EtlRouteExecutionException failure, Throwable cause) {
		if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
			return cause.getMessage();
		}
		return failure.getMessage();
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

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static String requireOptionalText(String value, String message) {
		if (value != null && value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}
}
