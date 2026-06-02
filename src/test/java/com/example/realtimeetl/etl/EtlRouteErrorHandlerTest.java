package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.messaging.support.ErrorMessage;

@ExtendWith(OutputCaptureExtension.class)
class EtlRouteErrorHandlerTest {

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final RecordingFailureEventPublisher publisher = new RecordingFailureEventPublisher();
	private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_780_001_234_567L), ZoneOffset.UTC);
	private final EtlRouteErrorHandler handler = new EtlRouteErrorHandler(
		publisher,
		new EtlFailureEventMetrics(meterRegistry),
		clock);

	@Test
	void rejectsNullFailureEventPublisher() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteErrorHandler(null, new EtlFailureEventMetrics(meterRegistry), clock))
			.withMessage("failureEventPublisher must not be null");
	}

	@Test
	void rejectsNullFailureEventMetrics() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteErrorHandler(publisher, null, clock))
			.withMessage("failureEventMetrics must not be null");
	}

	@Test
	void rejectsNullClock() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteErrorHandler(publisher, new EtlFailureEventMetrics(meterRegistry), null))
			.withMessage("clock must not be null");
	}

	@Test
	void logsStructuredRouteFailureFromNestedException(CapturedOutput output) {
		EtlRouteExecutionException routeFailure = new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-123",
			"source-message-123",
			1_780_000_000_000L,
			0,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException("bad order"));

		handler.handle(new ErrorMessage(new RuntimeException("stream wrapper", routeFailure)));

		assertThat(output)
			.contains("ETL route failure route=orderInvoice node=validateOrder trace=trace-123 "
				+ "inputPayloadType=null sourceMessageId=source-message-123 "
				+ "sourceMessageTimestamp=1780000000000 completedNodes=0")
			.contains("nodeDurationsNanos={validateOrder=10}")
			.contains("bad order");
		assertThat(publisher.publishedEvent)
			.isEqualTo(new EtlFailureEvent(
				"orderInvoice",
				"validateOrder",
				"trace-123",
				1_780_001_234_567L,
				null,
				"source-message-123",
				1_780_000_000_000L,
				0,
				Map.of("validateOrder", 10L),
				IllegalArgumentException.class.getName(),
				"bad order"));
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.ACCEPTED))
			.isEqualTo(1.0);
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.REJECTED))
			.isZero();
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.ERROR))
			.isZero();
	}

	@Test
	void logsGenericErrorsWhenNoRouteFailureIsPresent(CapturedOutput output) {
		handler.handle(new ErrorMessage(new IllegalStateException("binder failed")));

		assertThat(output)
			.contains("Unhandled stream processing error: binder failed")
			.contains("java.lang.IllegalStateException: binder failed");
		assertThat(publisher.publishedEvent).isNull();
		assertThat(meterRegistry.find(EtlFailureEventMetrics.PUBLISH_COUNTER).counter()).isNull();
	}

	@Test
	void publishesFailureEventWithRouteFailureMessageWhenCauseMessageIsBlank() {
		EtlRouteExecutionException routeFailure = new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-123",
			"source-message-123",
			1_780_000_000_000L,
			0,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException());

		handler.handle(new ErrorMessage(routeFailure));

		assertThat(publisher.publishedEvent.errorType()).isEqualTo(IllegalArgumentException.class.getName());
		assertThat(publisher.publishedEvent.errorMessage())
			.isEqualTo("Route 'orderInvoice' failed at node 'validateOrder' for trace 'trace-123'");
	}

	@Test
	void logsWarningWhenFailureEventPublisherRejectsEvent(CapturedOutput output) {
		publisher.accepted = false;
		EtlRouteExecutionException routeFailure = new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-123",
			"source-message-123",
			1_780_000_000_000L,
			0,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException("bad order"));

		handler.handle(new ErrorMessage(routeFailure));

		assertThat(output)
			.contains("ETL route failure event was not accepted by binding etlFailures-out-0 "
				+ "for route=orderInvoice node=validateOrder trace=trace-123");
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.ACCEPTED))
			.isZero();
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.REJECTED))
			.isEqualTo(1.0);
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.ERROR))
			.isZero();
	}

	@Test
	void recordsMetricWhenFailureEventPublisherThrows(CapturedOutput output) {
		publisher.publishException = new IllegalStateException("broker unavailable");
		EtlRouteExecutionException routeFailure = new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			"trace-123",
			"source-message-123",
			1_780_000_000_000L,
			0,
			Map.of("validateOrder", 10L),
			new IllegalArgumentException("bad order"));

		handler.handle(new ErrorMessage(routeFailure));

		assertThat(output)
			.contains("Failed to publish ETL route failure event to binding etlFailures-out-0 "
				+ "for route=orderInvoice node=validateOrder trace=trace-123")
			.contains("java.lang.IllegalStateException: broker unavailable");
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.ACCEPTED))
			.isZero();
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.REJECTED))
			.isZero();
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.ERROR))
			.isEqualTo(1.0);
	}

	@Test
	void recordsMetricWhenFailureEventCannotBeBuilt(CapturedOutput output) {
		EtlRouteExecutionException routeFailure = new EtlRouteExecutionException(
			"orderInvoice",
			"validateOrder",
			new IllegalArgumentException("bad order"));

		handler.handle(new ErrorMessage(routeFailure));

		assertThat(output)
			.contains("Failed to publish ETL route failure event to binding etlFailures-out-0 "
				+ "for route=orderInvoice node=validateOrder trace=null")
			.contains("java.lang.IllegalArgumentException: traceId must not be blank");
		assertThat(publisher.publishedEvent).isNull();
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.ACCEPTED))
			.isZero();
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.REJECTED))
			.isZero();
		assertThat(publishCounter("orderInvoice", "validateOrder", EtlFailureEventMetrics.ERROR))
			.isEqualTo(1.0);
	}

	private double publishCounter(String routeName, String nodeName, String result) {
		Counter counter = meterRegistry.find(EtlFailureEventMetrics.PUBLISH_COUNTER)
			.tags("route", routeName, "node", nodeName, "result", result)
			.counter();
		return counter == null ? 0.0 : counter.count();
	}

	private static final class RecordingFailureEventPublisher implements EtlFailureEventPublisher {

		private boolean accepted = true;
		private RuntimeException publishException;
		private EtlFailureEvent publishedEvent;

		@Override
		public boolean publish(EtlFailureEvent event) {
			if (publishException != null) {
				throw publishException;
			}
			publishedEvent = event;
			return accepted;
		}
	}
}
