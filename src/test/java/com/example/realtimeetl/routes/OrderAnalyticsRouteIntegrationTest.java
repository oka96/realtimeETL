package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.example.realtimeetl.analytics.OrderAnalyticsEvent;
import com.example.realtimeetl.etl.EtlFailureEvent;
import com.example.realtimeetl.etl.EtlHeaders;
import com.example.realtimeetl.order.OrderCreated;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

@EnableTestBinder
@SpringBootTest
class OrderAnalyticsRouteIntegrationTest {

	@Autowired
	private InputDestination input;

	@Autowired
	private OutputDestination output;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void transformsExampleOrderIntoAnalyticsEvent() throws Exception {
		OrderCreated order = readJson("examples/order-analytics-input.json", OrderCreated.class);
		OrderAnalyticsEvent expected = readJson("examples/order-analytics-output.json", OrderAnalyticsEvent.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.setHeader(EtlHeaders.TRACE_ID, "analytics-trace-3001")
			.build(), "orders.analytics.input");

		Message<byte[]> result = output.receive(1_000, "order-analytics.output");

		assertThat(result).isNotNull();
		OrderAnalyticsEvent analytics = objectMapper.readValue(result.getPayload(), OrderAnalyticsEvent.class);
		assertThat(analytics).isEqualTo(expected);
		assertThat(result.getHeaders())
			.containsEntry(EtlHeaders.ROUTE, RouteNames.ORDER_ANALYTICS)
			.containsEntry(EtlHeaders.INPUT_PAYLOAD_TYPE, OrderCreated.class.getName())
			.containsEntry(EtlHeaders.OUTPUT_PAYLOAD_TYPE, OrderAnalyticsEvent.class.getName())
			.containsEntry(EtlHeaders.LAST_NODE, "markAnalyticsRoute")
			.containsEntry(EtlHeaders.NODE_COUNT, 6)
			.containsEntry(EtlHeaders.TRACE_ID, "analytics-trace-3001")
			.containsEntry(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_ANALYTICS)
			.containsEntry(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1);
		assertThat((Long) result.getHeaders().get(EtlHeaders.DURATION_NANOS)).isPositive();
		assertThat((String) result.getHeaders().get(EtlHeaders.SOURCE_MESSAGE_ID)).isNotBlank();
		assertThat((Long) result.getHeaders().get(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP)).isPositive();
		assertThat(nodeDurations(result))
			.containsOnlyKeys(
				"requireOrderContract",
				"validateOrder",
				"normalizeOrder",
				"classifyAmount",
				"buildAnalytics",
				"markAnalyticsRoute");
	}

	@Test
	void classifiesLargeOrders() throws Exception {
		OrderCreated order = readJson("examples/order-analytics-normalized-input.json", OrderCreated.class);
		OrderAnalyticsEvent expected = readJson("examples/order-analytics-normalized-output.json", OrderAnalyticsEvent.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.analytics.input");

		Message<byte[]> result = output.receive(1_000, "order-analytics.output");

		assertThat(result).isNotNull();
		OrderAnalyticsEvent analytics = objectMapper.readValue(result.getPayload(), OrderAnalyticsEvent.class);
		assertThat(analytics).isEqualTo(expected);
	}

	@Test
	void rejectsInvalidOrdersWithoutPublishingAnalyticsEvent() throws Exception {
		OrderCreated order = readJson("examples/order-analytics-invalid-input.json", OrderCreated.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.setHeader(EtlHeaders.TRACE_ID, "analytics-invalid-trace-1")
			.build(), "orders.analytics.input");

		assertThat(output.receive(100, "order-analytics.output")).isNull();
		Message<byte[]> failureMessage = receiveFailureMessageForTrace("analytics-invalid-trace-1");
		assertThat(failureMessage.getHeaders())
			.containsEntry(EtlHeaders.EVENT_TYPE, EtlFailureEvent.EVENT_TYPE)
			.containsEntry(EtlHeaders.EVENT_VERSION, EtlFailureEvent.EVENT_VERSION)
			.containsEntry(EtlHeaders.ROUTE, RouteNames.ORDER_ANALYTICS)
			.containsEntry(EtlHeaders.LAST_NODE, "validateOrder")
			.containsEntry(EtlHeaders.NODE_COUNT, 1)
			.containsEntry(EtlHeaders.TRACE_ID, "analytics-invalid-trace-1")
			.containsEntry(EtlHeaders.INPUT_PAYLOAD_TYPE, OrderCreated.class.getName());
		assertThat((Long) failureMessage.getHeaders().get(EtlHeaders.FAILURE_TIMESTAMP)).isPositive();
		EtlFailureEvent failure = objectMapper.readValue(failureMessage.getPayload(), EtlFailureEvent.class);
		assertThat(failure.routeName()).isEqualTo(RouteNames.ORDER_ANALYTICS);
		assertThat(failure.nodeName()).isEqualTo("validateOrder");
		assertThat(failure.traceId()).isEqualTo("analytics-invalid-trace-1");
		assertThat(failure.failureTimestamp()).isPositive();
		assertThat(failure.inputPayloadType()).isEqualTo(OrderCreated.class.getName());
		assertThat(failure.completedNodeCount()).isEqualTo(1);
		assertThat(failureMessage.getHeaders())
			.containsEntry(EtlHeaders.FAILURE_TIMESTAMP, failure.failureTimestamp())
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_ID, failure.sourceMessageId())
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP, failure.sourceMessageTimestamp());
		assertThat(failure.nodeDurationsNanos()).containsOnlyKeys("requireOrderContract", "validateOrder");
		assertThat(failure.errorType()).isEqualTo(IllegalArgumentException.class.getName());
		assertThat(failure.errorMessage()).isEqualTo("Customer id is required");
	}

	@Test
	void rejectsMalformedCurrencyWithoutPublishingAnalyticsEvent() throws Exception {
		OrderCreated order = readJson("examples/order-analytics-invalid-currency-input.json", OrderCreated.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.analytics.input");

		assertThat(output.receive(100, "order-analytics.output")).isNull();
	}

	private <T> T readJson(String path, Class<T> type) throws Exception {
		return objectMapper.readValue(new ClassPathResource(path).getInputStream(), type);
	}

	private Message<byte[]> receiveFailureMessageForTrace(String traceId) throws Exception {
		for (int attempt = 0; attempt < 10; attempt++) {
			Message<byte[]> failureMessage = output.receive(1_000, "etl.failures");
			if (failureMessage == null) {
				continue;
			}
			EtlFailureEvent failure = objectMapper.readValue(failureMessage.getPayload(), EtlFailureEvent.class);
			if (traceId.equals(failure.traceId())) {
				return failureMessage;
			}
		}
		throw new AssertionError("No ETL failure event received for trace " + traceId);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Long> nodeDurations(Message<?> message) {
		return (Map<String, Long>) message.getHeaders().get(EtlHeaders.NODE_DURATIONS_NANOS);
	}
}
