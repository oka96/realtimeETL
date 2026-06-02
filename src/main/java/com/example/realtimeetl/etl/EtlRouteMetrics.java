package com.example.realtimeetl.etl;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public final class EtlRouteMetrics {

	public static final String MESSAGE_COUNTER = "realtime.etl.route.messages";
	public static final String DURATION_TIMER = "realtime.etl.route.duration";
	private static final String ROUTE_TAG = "route";
	private static final String RESULT_TAG = "result";
	private static final String SUCCESS = "success";
	private static final String FAILURE = "failure";

	private final MeterRegistry meterRegistry;

	public EtlRouteMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
	}

	public <I, O> Function<Message<I>, Message<O>> instrument(
		String routeName,
		Function<Message<I>, Message<O>> delegate) {
		String requiredRouteName = requireRouteName(routeName);
		Objects.requireNonNull(delegate, "delegate must not be null");
		return input -> {
			long startedAt = System.nanoTime();
			try {
				Message<O> output = delegate.apply(input);
				record(requiredRouteName, SUCCESS, startedAt);
				return output;
			}
			catch (RuntimeException ex) {
				record(requiredRouteName, FAILURE, startedAt);
				throw ex;
			}
		};
	}

	private void record(String routeName, String result, long startedAt) {
		Counter.builder(MESSAGE_COUNTER)
			.description("Total ETL route messages processed")
			.tag(ROUTE_TAG, routeName)
			.tag(RESULT_TAG, result)
			.register(meterRegistry)
			.increment();
		Timer.builder(DURATION_TIMER)
			.description("ETL route processing duration")
			.tag(ROUTE_TAG, routeName)
			.tag(RESULT_TAG, result)
			.register(meterRegistry)
			.record(Duration.ofNanos(System.nanoTime() - startedAt));
	}

	private static String requireRouteName(String routeName) {
		if (routeName == null || routeName.isBlank()) {
			throw new IllegalArgumentException("routeName must not be blank");
		}
		return routeName;
	}
}
