package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class EtlFailureEventMetricsTest {

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final EtlFailureEventMetrics metrics = new EtlFailureEventMetrics(meterRegistry);

	@Test
	void recordsFailureEventPublishAttempts() {
		metrics.record(routeFailure(), EtlFailureEventMetrics.ACCEPTED);

		assertThat(publishCounter(EtlFailureEventMetrics.ACCEPTED)).isEqualTo(1.0);
		assertThat(publishCounter(EtlFailureEventMetrics.ERROR)).isZero();
	}

	@Test
	void rejectsMissingMeterRegistry() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlFailureEventMetrics(null))
			.withMessage("meterRegistry must not be null");
	}

	@Test
	void rejectsMissingFailure() {
		assertThatNullPointerException()
			.isThrownBy(() -> metrics.record(null, EtlFailureEventMetrics.ERROR))
			.withMessage("failure must not be null");
	}

	@Test
	void rejectsBlankResult() {
		assertThatThrownBy(() -> metrics.record(routeFailure(), " "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("result must not be blank");
	}

	private double publishCounter(String result) {
		Counter counter = meterRegistry.find(EtlFailureEventMetrics.PUBLISH_COUNTER)
			.tags("route", "orderInvoice", "node", "validateOrder", "result", result)
			.counter();
		return counter == null ? 0.0 : counter.count();
	}

	private static EtlRouteExecutionException routeFailure() {
		return new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException("bad order"));
	}
}
