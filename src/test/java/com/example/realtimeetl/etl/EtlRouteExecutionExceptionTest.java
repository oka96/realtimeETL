package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EtlRouteExecutionExceptionTest {

	@Test
	void storesImmutableCopyOfNodeDurations() {
		Map<String, Long> durations = new LinkedHashMap<>();
		durations.put("validateOrder", 10L);
		EtlRouteExecutionException exception = routeFailure(durations);

		durations.put("buildInvoice", 20L);

		assertThat(exception.nodeDurationsNanos()).containsExactly(Map.entry("validateOrder", 10L));
		assertThatThrownBy(() -> exception.nodeDurationsNanos().put("mutate", 1L))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsBlankRouteName() {
		assertThatThrownBy(() -> new EtlRouteExecutionException(
			" ",
			"validateOrder",
			"trace-1",
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException("bad order")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
	}

	@Test
	void rejectsBlankNodeName() {
		assertThatThrownBy(() -> new EtlRouteExecutionException(
			"orderInvoice",
			" ",
			"trace-1",
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException("bad order")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("nodeName must not be blank");
	}

	@Test
	void rejectsNullCause() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteExecutionException(
				"orderInvoice",
				"validateOrder",
				"trace-1",
				"com.example.OrderCreated",
				"source-message-1",
				1_780_000_000_000L,
				1,
				Map.of("validateOrder", 10L),
				null))
			.withMessage("cause must not be null");
	}

	@Test
	void omitsBlankCauseMessageFromRouteFailureMessage() {
		EtlRouteExecutionException exception = new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException());

		assertThat(exception.getMessage())
			.isEqualTo("Route 'orderInvoice' failed at node 'validateOrder' for trace 'trace-1'");
	}

	@Test
	void rejectsNegativeSourceMessageTimestamp() {
		assertThatThrownBy(() -> new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			"com.example.OrderCreated",
			"source-message-1",
			-1L,
			1,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException("bad order")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("sourceMessageTimestamp must not be negative");
	}

	@Test
	void rejectsNegativeCompletedNodeCount() {
		assertThatThrownBy(() -> new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			-1,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException("bad order")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("completedNodeCount must not be negative");
	}

	@Test
	void rejectsNullNodeDurations() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteExecutionException(
				"orderInvoice",
				"validateOrder",
				"trace-1",
				"com.example.OrderCreated",
				"source-message-1",
				1_780_000_000_000L,
				1,
				null,
				new IllegalArgumentException("bad order")))
			.withMessage("nodeDurationsNanos must not be null");
	}

	@Test
	void rejectsBlankNodeDurationName() {
		assertThatThrownBy(() -> routeFailure(Map.of(" ", 10L)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("node duration names must not be blank");
	}

	@Test
	void rejectsNullNodeDurationValue() {
		Map<String, Long> durations = new LinkedHashMap<>();
		durations.put("validateOrder", null);

		assertThatThrownBy(() -> routeFailure(durations))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("node duration for 'validateOrder' must not be null");
	}

	@Test
	void rejectsNegativeNodeDurationValue() {
		assertThatThrownBy(() -> routeFailure(Map.of("validateOrder", -1L)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("node duration for 'validateOrder' must not be negative");
	}

	private static EtlRouteExecutionException routeFailure(Map<String, Long> nodeDurationsNanos) {
		return new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			nodeDurationsNanos,
			new IllegalArgumentException("bad order"));
	}
}
