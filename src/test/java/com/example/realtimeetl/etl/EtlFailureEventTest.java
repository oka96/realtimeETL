package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EtlFailureEventTest {

	@Test
	void storesImmutableCopyOfNodeDurations() {
		Map<String, Long> durations = new LinkedHashMap<>();
		durations.put("requireOrderContract", 10L);
		EtlFailureEvent event = failureEvent(durations);

		durations.put("validateOrder", 20L);

		assertThat(event.nodeDurationsNanos())
			.containsExactly(Map.entry("requireOrderContract", 10L));
		assertThatThrownBy(() -> event.nodeDurationsNanos().put("mutate", 1L))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsNegativeFailureTimestamp() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			-1,
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("failureTimestamp must not be negative");
	}

	@Test
	void rejectsBlankRouteName() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			" ",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
	}

	@Test
	void rejectsBlankNodeName() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			" ",
			"trace-1",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("nodeName must not be blank");
	}

	@Test
	void rejectsBlankTraceId() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			" ",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("traceId must not be blank");
	}

	@Test
	void rejectsBlankInputPayloadTypeWhenPresent() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			" ",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("inputPayloadType must not be blank when present");
	}

	@Test
	void rejectsBlankSourceMessageIdWhenPresent() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			" ",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("sourceMessageId must not be blank when present");
	}

	@Test
	void rejectsBlankErrorType() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			" ",
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("errorType must not be blank");
	}

	@Test
	void rejectsBlankErrorMessage() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("errorMessage must not be blank");
	}

	@Test
	void rejectsNegativeSourceMessageTimestamp() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			"source-message-1",
			-1L,
			1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("sourceMessageTimestamp must not be negative");
	}

	@Test
	void fromUsesRouteFailureMessageWhenCauseMessageIsBlank() {
		EtlRouteExecutionException failure = new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException());

		EtlFailureEvent event = EtlFailureEvent.from(failure, 1_780_000_000_500L);

		assertThat(event.errorType()).isEqualTo(IllegalArgumentException.class.getName());
		assertThat(event.errorMessage())
			.isEqualTo("Route 'orderInvoice' failed at node 'validateOrder' for trace 'trace-1'");
	}

	@Test
	void rejectsNegativeCompletedNodeCount() {
		assertThatThrownBy(() -> new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			-1,
			Map.of("validateOrder", 10L),
			IllegalArgumentException.class.getName(),
			"bad order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("completedNodeCount must not be negative");
	}

	@Test
	void rejectsNullNodeDurations() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlFailureEvent(
				"orderInvoice",
				"validateOrder",
				"trace-1",
				1_780_000_000_500L,
				"com.example.OrderCreated",
				"source-message-1",
				1_780_000_000_000L,
				1,
				null,
				IllegalArgumentException.class.getName(),
				"bad order"))
			.withMessage("nodeDurationsNanos must not be null");
	}

	@Test
	void rejectsBlankNodeDurationName() {
		assertThatThrownBy(() -> failureEvent(Map.of(" ", 10L)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("node duration names must not be blank");
	}

	@Test
	void rejectsNullNodeDurationValue() {
		Map<String, Long> durations = new LinkedHashMap<>();
		durations.put("validateOrder", null);

		assertThatThrownBy(() -> failureEvent(durations))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("node duration for 'validateOrder' must not be null");
	}

	@Test
	void rejectsNegativeNodeDurationValue() {
		assertThatThrownBy(() -> failureEvent(Map.of("validateOrder", -1L)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("node duration for 'validateOrder' must not be negative");
	}

	private static EtlFailureEvent failureEvent(Map<String, Long> nodeDurationsNanos) {
		return new EtlFailureEvent(
			"orderInvoice",
			"validateOrder",
			"trace-1",
			1_780_000_000_500L,
			"com.example.OrderCreated",
			"source-message-1",
			1_780_000_000_000L,
			1,
			nodeDurationsNanos,
			IllegalArgumentException.class.getName(),
			"bad order");
	}
}
