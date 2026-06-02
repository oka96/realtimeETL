package com.example.realtimeetl.etl;

import java.util.Set;

public final class EtlHeaders {

	public static final String ROUTE = "etlRoute";
	public static final String INPUT_PAYLOAD_TYPE = "etlInputPayloadType";
	public static final String OUTPUT_PAYLOAD_TYPE = "etlOutputPayloadType";
	public static final String LAST_NODE = "etlLastNode";
	public static final String NODE_COUNT = "etlNodeCount";
	public static final String NODE_DURATIONS_NANOS = "etlNodeDurationsNanos";
	public static final String DURATION_NANOS = "etlDurationNanos";
	public static final String TRACE_ID = "etlTraceId";
	public static final String SOURCE_MESSAGE_ID = "etlSourceMessageId";
	public static final String SOURCE_MESSAGE_TIMESTAMP = "etlSourceMessageTimestamp";
	public static final String FAILURE_TIMESTAMP = "etlFailureTimestamp";
	public static final String EVENT_TYPE = "etlEventType";
	public static final String EVENT_VERSION = "etlEventVersion";

	private static final Set<String> ROUTE_MANAGED_HEADERS = Set.of(
		ROUTE,
		INPUT_PAYLOAD_TYPE,
		OUTPUT_PAYLOAD_TYPE,
		LAST_NODE,
		NODE_COUNT,
		NODE_DURATIONS_NANOS,
		DURATION_NANOS,
		TRACE_ID,
		SOURCE_MESSAGE_ID,
		SOURCE_MESSAGE_TIMESTAMP,
		FAILURE_TIMESTAMP);

	private EtlHeaders() {
	}

	public static boolean isRouteManaged(String headerName) {
		return ROUTE_MANAGED_HEADERS.contains(headerName);
	}
}
