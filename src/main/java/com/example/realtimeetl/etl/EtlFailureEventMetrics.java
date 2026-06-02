package com.example.realtimeetl.etl;

import java.util.Objects;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public final class EtlFailureEventMetrics {

	public static final String PUBLISH_COUNTER = "realtime.etl.failure.events";
	public static final String ACCEPTED = "accepted";
	public static final String REJECTED = "rejected";
	public static final String ERROR = "error";

	private static final String ROUTE_TAG = "route";
	private static final String NODE_TAG = "node";
	private static final String RESULT_TAG = "result";

	private final MeterRegistry meterRegistry;

	public EtlFailureEventMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
	}

	public void record(EtlRouteExecutionException failure, String result) {
		Objects.requireNonNull(failure, "failure must not be null");
		String requiredResult = requireText(result, "result must not be blank");
		Counter.builder(PUBLISH_COUNTER)
			.description("Total ETL failure event publish attempts")
			.tag(ROUTE_TAG, failure.routeName())
			.tag(NODE_TAG, failure.nodeName())
			.tag(RESULT_TAG, requiredResult)
			.register(meterRegistry)
			.increment();
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}
}
